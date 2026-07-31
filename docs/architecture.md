# 安全與架構邊界

```text
Module companion App
        │ ContentResolver.call + package name
        ▼
NPatch Manager RemoteApiProvider
        │ 驗證 Binder calling UID、UID 套件、模組資料庫
        ▼
UID-bound IXposedService Binder
        │ API 102 Remote Preferences / Remote Files
        ▼
Manager private storage, partitioned by module package
```

## 身分驗證

Provider 雖然必須 exported 才能服務模組 App，但不以呼叫端傳入的 package name 作為唯一信任來源。Manager 同時確認：

1. package 已存在於 NPatch 的模組資料；
2. package 屬於 Binder calling UID；
3. 回傳的 service Binder 後續每次呼叫仍來自原始 UID。

因此不能只偽造 extras 內的 package name 讀取另一模組的資料。

## 儲存隔離

Preferences 與 Files 都以模組 package 分區。群組和檔名會拒絕路徑分隔符，Files 僅允許單層檔名，以防止路徑穿越。

## 不公開的能力

被修補目標程序使用另一條框架內部、只讀的 injected service。此 repository 不包含該 AIDL，也不提供取得它的公共方法，避免模組伴生 App 或第三方 App 偽裝成被注入目標。

SDK 取得的 `IXposedService` 仍可能包含作用域、執行中目標或熱重載等 API 102 方法。這些方法由 NPatch Manager 按模組 UID 驗證；Remote SDK 不另外封裝或放寬權限。

## 威脅模型限制

- Android 若允許攻擊者以相同 UID 執行程式，該程式本來就共享應用信任邊界。
- `isAvailable` 只探測 provider，不等於身分驗證成功。
- Binder 物件不應傳給其他程序；Manager 會拒絕不同 UID 的後續呼叫。
- SDK 不為 Remote Files 提供內容加密。資料位於 Manager 私有目錄，安全性依賴 Android sandbox 與裝置本身。
