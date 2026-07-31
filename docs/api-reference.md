# API 與行為

公共入口只有 `top.nkbe.npatch.remote.NPatchRemoteClient`。

## 常數

### `DEFAULT_AUTHORITY`

官方 NPatch Manager 的 Remote Provider authority：`top.nkbe.npatch.remote`。

## 探測

### `isAvailable(Context)`

檢查系統是否能解析官方 Manager provider。它只代表 provider 存在，不代表目前模組一定能通過驗證。

### `isAvailable(Context, String authority)`

檢查自訂 Manager authority。

## 連線

### `connect(Context)`

使用呼叫 App 的 package name 與官方 authority 同步連線。

### `connect(Context, String modulePackageName)`

指定模組 package name，使用官方 authority。

### `connect(Context, String modulePackageName, String authority)`

完整連線入口。呼叫會在三秒後逾時。

### `connectAsync(...)`

對應同步入口的 `CompletableFuture` 版本。

### `connectService(...)`

進階入口，直接回傳 libxposed `IXposedService`。應用若只需要儲存功能，優先使用 client 封裝以減少對 Binder 細節的依賴。

## Remote Preferences

### `getRemotePreferences(String group)`

取得實作 Android `SharedPreferences` 的遠端群組。同一 client 會快取同名群組。

### `deleteRemotePreferences(String group)`

刪除遠端群組內容，並清空目前 client 的快取快照。

## Remote Files

### `listRemoteFiles()`

回傳已排序的單層檔名陣列。

### `openRemoteFile(String name)`

取得可讀寫 `ParcelFileDescriptor`。呼叫端負責關閉 descriptor 及建立於其上的 stream。

### `deleteRemoteFile(String name)`

刪除指定遠端檔案並回傳是否成功。

## 執行緒

client 可在多執行緒共用；Preferences 快照及 listener 集合具備並行保護。同步 Binder 與同步連線操作仍可能阻塞，UI 應使用非同步入口或自行切換執行緒。
