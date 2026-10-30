# NPatch Remote API

**English** • [简体中文](README_ZH.md)

NPatch Remote API is a lightweight Android SDK that bridges your Xposed module's settings app to NPatch Manager in rootless Local mode.

---

## Why NPatch Remote API?

In NPatch's rootless local patch mode, module processes and the manager operate in separate application sandboxes without a privileged system daemon to deliver services. 

NPatch Remote API solves this by exposing an authenticated ContentProvider gateway inside NPatch Manager. Your module's UI app can connect on demand and interact with the standard LibXposed API 102 `IXposedService` contracts—no root required, no custom IPC protocols to learn.

### Highlights

- **Standard LibXposed Contracts**: Seamlessly read/write Remote Preferences (`SharedPreferences`-compatible), manage Remote Files, query scope, and trigger module hot reloads.
- **Strict Sandbox Isolation**: Stored data is partitioned by module package name and strictly validated against the calling UID.
- **Live Preference Sync**: In-memory preferences automatically stay up to date via `ContentObserver` change notifications.
- **Pre-flight Diagnostics (`probe`)**: Inspect Manager installation, module enablement, and connection health in a single call—no messy try-catch boilerplate.
- **Lifecycle Clean**: Tracks connection lifecycles with Binder death recipients to avoid memory leaks on both ends.

---

## Getting Started

### Option 1: JitPack (Recommended)

Add JitPack to your repository list:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependencies to your module app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.7723mod:NPatch-Remote-API:v1.0.1")
    implementation("io.github.libxposed:interface:102.0.0")
}
```

### Option 2: Direct AAR

Download `npatch-remote-api-v1.0.1-release.aar` from [Releases](https://github.com/7723mod/NPatch-Remote-API/releases), drop it into your module's `libs/` directory, and declare:

```kotlin
dependencies {
    implementation(files("libs/npatch-remote-api-v1.0.1-release.aar"))
    implementation("io.github.libxposed:interface:102.0.0")
}
```

> **Requirements**: Android 9.0+ (API 28+), JDK 21.

---

## Usage

### 1. Basic Connection & Preference Access

Connecting may launch the NPatch Manager process. Always connect asynchronously or from a background thread:

```java
// Asynchronous connection (safe for main thread)
NPatchRemoteClient.connectAsync(context)
    .thenAccept(client -> {
        SharedPreferences prefs = client.getRemotePreferences("settings");
        
        // Standard SharedPreferences operations
        boolean isEnabled = prefs.getBoolean("feature_x", false);
        prefs.edit().putBoolean("feature_x", true).apply();
    })
    .exceptionally(error -> {
        Log.e("Module", "Failed to connect to NPatch Manager", error);
        return null;
    });
```

### 2. Connection Diagnostics (`probe`)

Before rendering your settings page, use `probe()` to detect connection status and guide users if setup is incomplete:

```java
RemoteStatus status = NPatchRemoteClient.probe(context);
switch (status) {
    case READY -> 
        showSettingsUI();
    case MANAGER_NOT_FOUND -> 
        showNotice("NPatch Manager is not installed.");
    case MODULE_NOT_CONFIGURED -> 
        showNotice("Please enable this module in NPatch Manager.");
    case CONNECTION_TIMEOUT -> 
        showNotice("NPatch Manager is not responding. Please retry.");
    case DENIED -> 
        showNotice("Access denied by NPatch Manager.");
    case UNKNOWN_ERROR -> 
        showNotice("An unknown error occurred.");
}
```

### 3. Version Compatibility Check (`connectChecked`)

If your module requires a specific NPatch release, verify compatibility during handshake:

```java
NPatchRemoteClient.connectCheckedAsync(context)
    .thenAccept(client -> {
        // Connected to a compatible Manager (>= NPatch 1.0.7)
    })
    .exceptionally(error -> {
        if (error.getCause() instanceof IncompatibleManagerException ex) {
            Log.w("Module", "NPatch Manager is too old: " + ex.getManagerVersionCode()
                    + ", requires >= " + ex.getRequiredVersionCode());
        }
        return null;
    });
```

### 4. Custom Manager Authority

If you build NPatch Manager with a custom package name or application ID, specify the authority explicitly:

```java
NPatchRemoteClient client = NPatchRemoteClient.connect(
    context,
    context.getPackageName(),
    "your.custom.manager.package.remote"
);
```

---

## Architecture at a Glance

NPatch Remote API complements LibXposed in rootless environments—it does not alter Hook APIs or inject target apps.

| Interface | Running In | Role | How It's Acquired |
| :--- | :--- | :--- | :--- |
| `XposedInterface` | Hooked Target App | Hook entry points, module lifecycle | Framework injection (`XposedModule.attachFramework`) |
| `XposedService` (standard) | Module UI App | Scope, remote storage, hot reload | Delivered via `XposedServiceHelper.registerListener` |
| `NPatchRemoteClient` | Module UI App | Explicit gateway for NPatch Local mode | Authenticated ContentProvider bridge returning API 102 `IXposedService` |

---

## Compatibility

- **SDK Version**: `1.0.1`
- **LibXposed Interface**: `102.0.0`
- **NPatch Manager**: `v1.0.7` or later
- **Android Support**: API 28+ (Android 9.0+)

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
