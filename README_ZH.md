# NPatch Remote API

[English](README.md) • **简体中文**

NPatch Remote API 是专为 Xposed 模块设置界面打造的轻量级 Android SDK。它让模块 App 在免 Root 本地修补模式下安全连接 NPatch Manager，读写按模块包名严格隔离的远程 Preferences 与 Files。

---

## 为什么需要 NPatch Remote API？

在 NPatch 的本地免 Root 修补模式下，模块设置应用与 Manager 分属独立的 Android 沙盒，缺乏系统级守护进程分发 `XposedService`。

NPatch Remote API 内部通过经过身份校验的 ContentProvider 提供连接通道，让模块设置 App 能够按需取得标准 LibXposed API 102 的 `IXposedService` 合约——无需 Root 权限，也无需学习私有 IPC 通信。

### 核心特性

- **标准 LibXposed 合约**：无缝支持远程偏好设置（完全兼容 `SharedPreferences`）、远程文件管理、作用域查询以及模块热重载。
- **严格沙盒隔离**：存储数据按模块包名隔离，并严格绑定与校验 Binder 调用的 UID。
- **即时配置同步**：内置 `ContentObserver`，在远程配置发生变动时自动刷新本地缓存。
- **状态健康诊断 (`probe`)**：单次调用即可明确识别未安装 Manager、未启用模块、超时或权限拒绝等状态，告别繁重的 try-catch。
- **完善生命周期管理**：支持 Binder 死亡监听（DeathRecipient），进程退出时自动释放连接与缓存，避免内存滞留。

---

## 引入 SDK

### 方式一：JitPack（推荐）

在项目的 `settings.gradle.kts` 或根 `build.gradle.kts` 中配置 JitPack 仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

在模块 App 的 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation("com.github.7723mod:NPatch-Remote-API:v1.0.1")
    implementation("io.github.libxposed:interface:102.0.0")
}
```

### 方式二：手动引入 AAR

从 [Releases](https://github.com/7723mod/NPatch-Remote-API/releases) 下载 `npatch-remote-api-v1.0.1-release.aar`，放入模块项目的 `libs/` 目录，并添加依赖：

```kotlin
dependencies {
    implementation(files("libs/npatch-remote-api-v1.0.1-release.aar"))
    implementation("io.github.libxposed:interface:102.0.0")
}
```

> **环境要求**：Android 9.0+（API 28+），JDK 21。

---

## 快速上手

### 1. 基础连接与偏好设置读写

建立连接可能会拉起 NPatch Manager 进程，请在工作线程调用同步 API，或在 UI 线程直接使用异步 API：

```java
// 异步连接（主线程安全）
NPatchRemoteClient.connectAsync(context)
    .thenAccept(client -> {
        SharedPreferences prefs = client.getRemotePreferences("settings");
        
        // 标准 SharedPreferences 操作
        boolean isEnabled = prefs.getBoolean("feature_x", false);
        prefs.edit().putBoolean("feature_x", true).apply();
    })
    .exceptionally(error -> {
        Log.e("Module", "连接 NPatch Manager 失败", error);
        return null;
    });
```

### 2. 连接状态诊断 (`probe`)

在打开模块设置页面前，调用 `probe()` 获取结构化状态枚举（`RemoteStatus`），向用户给出明确的操作指引：

```java
RemoteStatus status = NPatchRemoteClient.probe(context);
switch (status) {
    case READY -> 
        showSettingsUI();
    case MANAGER_NOT_FOUND -> 
        showNotice("未检测到 NPatch Manager，请先安装。");
    case MODULE_NOT_CONFIGURED -> 
        showNotice("请在 NPatch Manager 中勾选启用本模块。");
    case CONNECTION_TIMEOUT -> 
        showNotice("连接 NPatch Manager 超时，请重试。");
    case DENIED -> 
        showNotice("NPatch 拒绝了本次访问。");
    case UNKNOWN_ERROR -> 
        showNotice("发生未知错误。");
}
```

### 3. 最低版本保护 (`connectChecked`)

若模块依赖特定版本以上的 Manager 能力，可在建立连接时即时验证兼容性：

```java
NPatchRemoteClient.connectCheckedAsync(context)
    .thenAccept(client -> {
        // 安全使用 Remote API（已验证 Manager >= 1.0.7）
    })
    .exceptionally(error -> {
        if (error.getCause() instanceof IncompatibleManagerException ex) {
            Log.w("Module", "Manager 版本过低: " + ex.getManagerVersionCode()
                    + "，需要 >= " + ex.getRequiredVersionCode());
        }
        return null;
    });
```

### 4. 自定义 Manager Authority

如果自行编译的 Manager 修改了 Application ID 或包名，可在连接时显式传入对应 Authority：

```java
NPatchRemoteClient client = NPatchRemoteClient.connect(
    context,
    context.getPackageName(),
    "your.custom.manager.package.remote"
);
```

---

## 与 LibXposed 的关系

NPatch Remote API 是 LibXposed 在免 Root 本地修补环境下的补充，不改变 Hook 机制，也不干涉注入目标进程。

| 通道 | 所在进程 | 核心职责 | 获取途径 |
| :--- | :--- | :--- | :--- |
| `XposedInterface` | 被注入的目标 App | Hook 入口点、模块生命周期 | 框架注入回调（`XposedModule.attachFramework` 等） |
| `XposedService` (标准) | 模块设置 App | 作用域、远程存储、热重载 | 系统广播分发（`XposedServiceHelper.registerListener`） |
| `NPatchRemoteClient` | 模块设置 App | NPatch 本地模式显式直连 | 安全 ContentProvider 通道，返回 API 102 `IXposedService` |

---

## 兼容性

- **SDK 版本**：`1.0.1`
- **LibXposed Interface**：`102.0.0`
- **NPatch Manager**：`v1.0.7` 或更高版本
- **系统要求**：Android 9.0+（API 28+）

## 许可证

基于 [Apache License 2.0](LICENSE) 协议开源。
