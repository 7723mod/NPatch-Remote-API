# NPatch Remote API

NPatch Remote API 是提供給 Xposed 模組伴生 App 的輕量 Android SDK。它讓未取得 Root、也不在被注入程序中的模組 App，透過 NPatch Manager 存取同一份 Remote Preferences 與 Remote Files。

SDK 使用 libxposed API 102 的 `IXposedService` 作為資料操作合約，但連線入口是 NPatch Manager 的已驗證 `ContentProvider`。Manager 會核對呼叫 UID、套件名稱與已安裝模組資料，Binder 也會持續限制呼叫 UID。

## 為什麼不是直接使用 libxposed？

標準 libxposed service 由框架在模組載入或 provider callback 階段交給模組；一般的模組設定 App 在 NPatch 本地修補模式下不一定能取得這個 callback。NPatch Remote API 只補上這段「模組 App 如何安全連回 Manager」的缺口，取得 Binder 後仍使用標準 API 102 介面。

它不是另一套 Hook API，也不取代 libxposed：

| 能力 | libxposed API | NPatch Remote API |
| --- | --- | --- |
| Hook、作用域、熱重載 | 標準框架能力 | 不封裝 |
| 注入程序取得 service | 框架 callback | 不提供 |
| 模組伴生 App 連線 | 取決於框架實作 | 由 NPatch Manager 驗證後提供 |
| Remote Preferences / Files | 定義標準 Binder 合約 | 提供 Android App 友善入口 |
| 適用框架 | 支援 API 102 的框架 | NPatch 本地修補模式 |

## 引入

目前先使用 Release 頁面的 AAR：

```kotlin
dependencies {
    compileOnly("io.github.libxposed:interface:102.0.0")
    implementation(files("libs/npatch-remote-api-1.0.0.aar"))
}
```

SDK 的 `minSdk` 為 28，使用 Java 8 以上語法即可呼叫；建置 SDK 本身需要 JDK 21、Android SDK 37 與 Gradle Wrapper。

## 快速開始

請在工作執行緒或使用非同步入口連線，不要在主執行緒呼叫同步 `connect`：

```java
NPatchRemoteClient.connectAsync(getApplicationContext())
        .thenAccept(client -> {
            SharedPreferences preferences =
                    client.getRemotePreferences("settings");
            preferences.edit().putBoolean("enabled", true).apply();
        })
        .exceptionally(error -> {
            Log.e("Module", "NPatch Remote unavailable", error);
            return null;
        });
```

若 Manager 使用自訂 application ID，可傳入對應 authority：

```java
NPatchRemoteClient client = NPatchRemoteClient.connect(
        context,
        context.getPackageName(),
        "your.manager.application.id.remote"
);
```

完整說明請見：

- [接入指南](docs/getting-started.md)
- [API 與行為](docs/api-reference.md)
- [安全與架構邊界](docs/architecture.md)

## 建置

```bash
./gradlew assembleRelease
```

輸出位於 `build/outputs/aar/`。執行 `publishReleasePublicationToMavenLocal` 可發布到本機 Maven。

## 相容性

- SDK API：`1.0.0`
- libxposed interface：`102.0.0`
- NPatch：`1.0.7` 或更新版本
- Android：API 28+

## License

[GNU General Public License v3.0](LICENSE)
