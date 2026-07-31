# 接入指南

## 前置條件

1. 模組已安裝，且能被 NPatch Manager 識別為 Xposed 模組。
2. 發起連線的 App 套件名稱必須與模組套件名稱相同；若分離 companion App，需明確傳入模組套件名稱，但目前 Manager 的 UID 驗證仍要求該套件屬於呼叫 UID。
3. 裝置已安裝支援 Remote API 的 NPatch 1.0.7 或更新版本。

不需要宣告自訂 Android permission。Manager 會根據 Binder calling UID、UID 所屬套件與模組資料庫完成驗證。

## 檢查與連線

```java
if (!NPatchRemoteClient.isAvailable(context)) {
    // 隱藏僅適用於 NPatch 的設定，或改用本機儲存。
    return;
}

NPatchRemoteClient.connectAsync(context)
        .thenAccept(this::onConnected)
        .exceptionally(error -> {
            showConnectionError(error);
            return null;
        });
```

`connectAsync` 使用 SDK 管理的 daemon executor。同步 `connect` 最長等待三秒，請勿從 Android 主執行緒呼叫。

## Preferences

```java
SharedPreferences prefs = client.getRemotePreferences("settings");
boolean enabled = prefs.getBoolean("enabled", false);
prefs.edit()
        .putBoolean("enabled", true)
        .putStringSet("targets", selectedTargets)
        .apply();
```

支援 `String`、`Set<String>`、`int`、`long`、`float` 與 `boolean`。群組名稱不可為空，也不能包含 `/` 或 `\\`。

`commit()` 會同步回報寫入結果；`apply()` 先更新目前 client 的記憶體快照，再非同步寫入 Manager。程序在寫入完成前結束時，`apply()` 可能尚未落盤。

目前 listener 用於通知同一 client 實例所做的變更，不保證接收其他程序的即時修改。需要強一致讀取時，重新取得 client 或建立新的連線。

## Files

```java
try (ParcelFileDescriptor pfd = client.openRemoteFile("config.json");
     FileOutputStream output = new FileOutputStream(pfd.getFileDescriptor())) {
    output.write(jsonBytes);
}
```

檔名必須是單層名稱，不能使用路徑分隔符、`.` 或 `..`。`openRemoteFile` 回傳可讀寫 descriptor，檔案不存在時會建立。另可使用 `listRemoteFiles()` 和 `deleteRemoteFile(name)`。

## 錯誤處理

- `SecurityException`：呼叫 UID、套件名稱或已安裝模組狀態未通過 Manager 驗證。
- `IllegalStateException`：Manager 不可用、Binder 無效、連線逾時或遭中斷。
- `RemoteException`：已建立 Binder 連線，但遠端操作失敗。
- `FileNotFoundException`：Manager 未回傳有效的檔案 descriptor。

Manager 更新、被系統停止或程序重建後，既有 Binder 可能失效。操作遇到 `RemoteException` 時應捨棄 client，稍後重新連線。
