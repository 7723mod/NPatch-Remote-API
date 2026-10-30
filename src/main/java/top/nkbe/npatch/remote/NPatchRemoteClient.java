package top.nkbe.npatch.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CompletableFuture;

import io.github.libxposed.service.IXposedService;

/**
 * Public NPatch client for module applications that cannot receive the normal libxposed provider
 * callback in local patching mode.
 *
 * <p>Writes use the standard {@link IXposedService} contract. Injected target access is a
 * framework-internal, read-only path and is intentionally not exposed by this module-app SDK.</p>
 */
public final class NPatchRemoteClient {
    public static final String DEFAULT_AUTHORITY = "top.nkbe.npatch.remote";

    /** @deprecated Use {@link #DEFAULT_AUTHORITY}. */
    @Deprecated
    public static final String AUTHORITY = DEFAULT_AUTHORITY;

    private static final String METHOD_GET_REMOTE_SERVICE = "getRemoteService";
    private static final String KEY_MODULE_PACKAGE = "modulePackageName";
    private static final String KEY_BINDER = "binder";
    private static final long CONNECT_TIMEOUT_SECONDS = 3;
    private static final ExecutorService CONNECTION_EXECUTOR =
            Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "NPatch-RemoteConnect");
                thread.setDaemon(true);
                return thread;
            });

    private static final Map<String, NPatchRemoteClient> CLIENT_CACHE = new ConcurrentHashMap<>();

    private final Context context;
    private final String modulePackageName;
    private final String authority;
    private final IXposedService service;
    private final Map<String, RemotePreferences> preferences = new ConcurrentHashMap<>();

    private NPatchRemoteClient(
            Context context,
            IXposedService service,
            String modulePackageName,
            String authority
    ) {
        this.context = context;
        this.service = service;
        this.modulePackageName = modulePackageName;
        this.authority = authority;
    }

    public static NPatchRemoteClient connect(Context context) {
        return connect(context, context.getPackageName());
    }

    public static NPatchRemoteClient connect(Context context, String modulePackageName) {
        return connect(context, modulePackageName, DEFAULT_AUTHORITY);
    }

    public static NPatchRemoteClient connect(
            Context context,
            String modulePackageName,
            String authority
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(modulePackageName, "modulePackageName");
        Objects.requireNonNull(authority, "authority");
        String cacheKey = authority + ":" + modulePackageName;
        NPatchRemoteClient cached = CLIENT_CACHE.get(cacheKey);
        if (cached != null && cached.service.asBinder().isBinderAlive()) {
            return cached;
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        IXposedService service = connectService(appContext, modulePackageName, authority);
        NPatchRemoteClient client = new NPatchRemoteClient(appContext, service, modulePackageName, authority);
        try {
            service.asBinder().linkToDeath(() -> CLIENT_CACHE.remove(cacheKey, client), 0);
        } catch (RemoteException ignored) {
        }
        CLIENT_CACHE.put(cacheKey, client);
        return client;
    }

    public static CompletableFuture<NPatchRemoteClient> connectAsync(Context context) {
        return connectAsync(context, context.getPackageName());
    }

    public static CompletableFuture<NPatchRemoteClient> connectAsync(
            Context context,
            String modulePackageName
    ) {
        return connectAsync(context, modulePackageName, DEFAULT_AUTHORITY);
    }

    public static CompletableFuture<NPatchRemoteClient> connectAsync(
            Context context,
            String modulePackageName,
            String authority
    ) {
        return CompletableFuture.supplyAsync(
                () -> connect(context, modulePackageName, authority),
                CONNECTION_EXECUTOR
        );
    }

    public static IXposedService connectService(Context context, String modulePackageName) {
        return connectService(context, modulePackageName, DEFAULT_AUTHORITY);
    }

    public static IXposedService connectService(
            Context context,
            String modulePackageName,
            String authority
    ) {
        IBinder binder = requestBinder(
                context,
                METHOD_GET_REMOTE_SERVICE,
                modulePackageName,
                authority
        );
        IXposedService service = IXposedService.Stub.asInterface(binder);
        if (service == null) {
            throw new IllegalStateException("NPatch remote service returned an invalid binder");
        }
        return service;
    }

    public static boolean isAvailable(Context context) {
        return isAvailable(context, DEFAULT_AUTHORITY);
    }

    public static boolean isAvailable(Context context, String authority) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(authority, "authority");
        return context.getPackageManager().resolveContentProvider(authority, 0) != null;
    }

    /**
     * Diagnoses the connection status without throwing exceptions.
     *
     * <p>Returns a {@link RemoteStatus} that explains <em>why</em> the remote service is
     * or is not reachable. Use this to show actionable guidance in the module settings UI
     * instead of catching opaque exceptions from {@link #connect}.</p>
     *
     * <p>This method performs IPC and must not be called on the main thread.</p>
     *
     * @param context the application context
     * @return a {@link RemoteStatus} describing the current connection state
     */
    public static RemoteStatus probe(Context context) {
        return probe(context, context.getPackageName(), DEFAULT_AUTHORITY);
    }

    /**
     * Diagnoses the connection status for a specific module package and authority.
     *
     * @param context           the application context
     * @param modulePackageName the module package to probe
     * @param authority         the Manager ContentProvider authority
     * @return a {@link RemoteStatus} describing the current connection state
     */
    public static RemoteStatus probe(Context context, String modulePackageName, String authority) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(modulePackageName, "modulePackageName");
        Objects.requireNonNull(authority, "authority");

        if (context.getPackageManager().resolveContentProvider(authority, 0) == null) {
            return RemoteStatus.MANAGER_NOT_FOUND;
        }

        try {
            NPatchRemoteClient client = connect(context, modulePackageName, authority);
            if (client.service.asBinder().isBinderAlive()) {
                return RemoteStatus.READY;
            }
            return RemoteStatus.UNKNOWN_ERROR;
        } catch (SecurityException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("rejected")) {
                return RemoteStatus.MODULE_NOT_CONFIGURED;
            }
            return RemoteStatus.DENIED;
        } catch (IllegalStateException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                return RemoteStatus.CONNECTION_TIMEOUT;
            }
            if (e.getMessage() != null && e.getMessage().contains("timed out")) {
                return RemoteStatus.CONNECTION_TIMEOUT;
            }
            return RemoteStatus.UNKNOWN_ERROR;
        } catch (Exception e) {
            return RemoteStatus.UNKNOWN_ERROR;
        }
    }

    public SharedPreferences getRemotePreferences(String group) {
        return preferences.computeIfAbsent(
                group,
                key -> new RemotePreferences(context, service, modulePackageName, authority, key)
        );
    }

    public void deleteRemotePreferences(String group) throws RemoteException {
        service.deleteRemotePreferences(group);
        RemotePreferences cached = preferences.remove(group);
        if (cached != null) {
            cached.close();
            cached.replaceWithEmpty();
        }
    }

    public String[] listRemoteFiles() throws RemoteException {
        return service.listRemoteFiles();
    }

    public ParcelFileDescriptor openRemoteFile(String name)
            throws RemoteException, FileNotFoundException {
        ParcelFileDescriptor descriptor = service.openRemoteFile(name);
        if (descriptor == null) {
            throw new FileNotFoundException(name);
        }
        return descriptor;
    }

    public boolean deleteRemoteFile(String name) throws RemoteException {
        return service.deleteRemoteFile(name);
    }

    /**
     * Returns the underlying {@link IXposedService} binder interface.
     *
     * <p>Allows advanced module apps to invoke standard API 102 methods directly (such as
     * querying scope, inspecting running targets, or requesting hot reload) over the
     * authenticated Manager connection.</p>
     *
     * @return the raw {@link IXposedService} instance
     */
    public IXposedService getService() {
        return service;
    }

    private static IBinder requestBinder(
            Context context,
            String method,
            String modulePackageName,
            String authority
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(modulePackageName, "modulePackageName");
        Objects.requireNonNull(authority, "authority");
        if (authority.isBlank()) {
            throw new IllegalArgumentException("authority is empty");
        }
        Bundle extras = new Bundle();
        extras.putString(KEY_MODULE_PACKAGE, modulePackageName);
        Future<Bundle> call = CONNECTION_EXECUTOR.submit(
                () -> context.getContentResolver().call(
                        Uri.parse("content://" + authority),
                        method,
                        null,
                        extras
                )
        );
        Bundle result;
        try {
            result = call.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            call.cancel(true);
            throw new IllegalStateException("NPatch remote service connection timed out", exception);
        } catch (InterruptedException exception) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NPatch remote service connection interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof SecurityException) {
                throw (SecurityException) cause;
            }
            throw new IllegalStateException("NPatch remote service is unavailable", cause);
        }
        IBinder binder = result == null ? null : result.getBinder(KEY_BINDER);
        if (binder == null) {
            throw new SecurityException(
                    "NPatch rejected the remote service request for " + modulePackageName);
        }
        return binder;
    }

    private static final class RemotePreferences implements SharedPreferences {
        private static final ExecutorService EXECUTOR =
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "NPatch-RemoteApi");
                    thread.setDaemon(true);
                    return thread;
                });

        private final Context context;
        private final IXposedService service;
        private final String group;
        private final Uri prefUri;
        private final ContentObserver observer;
        private final Set<OnSharedPreferenceChangeListener> listeners =
                Collections.newSetFromMap(new ConcurrentHashMap<>());
        private volatile Map<String, Object> values;

        @SuppressWarnings("unchecked")
        RemotePreferences(
                Context context,
                IXposedService service,
                String modulePackageName,
                String authority,
                String group
        ) {
            this.context = context;
            this.service = service;
            this.group = group;
            try {
                Bundle result = service.requestRemotePreferences(group);
                Serializable map = result == null ? null : result.getSerializable("map");
                if (map instanceof Map<?, ?>) {
                    values = immutableCopy((Map<String, Object>) map);
                } else {
                    values = Collections.emptyMap();
                }
            } catch (RemoteException exception) {
                throw new IllegalStateException("Cannot read NPatch remote preferences", exception);
            }

            if (context != null && modulePackageName != null && authority != null) {
                this.prefUri = Uri.parse("content://" + authority + "/pref/" + modulePackageName + "/" + group);
                this.observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        onChange(selfChange, null);
                    }

                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        reloadFromRemoteAsync();
                    }
                };
                try {
                    context.getContentResolver().registerContentObserver(prefUri, false, observer);
                } catch (Throwable ignored) {
                }
            } else {
                this.prefUri = null;
                this.observer = null;
            }
        }

        void close() {
            if (context != null && observer != null) {
                try {
                    context.getContentResolver().unregisterContentObserver(observer);
                } catch (Throwable ignored) {
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void reloadFromRemoteAsync() {
            EXECUTOR.execute(() -> {
                try {
                    Bundle result = service.requestRemotePreferences(group);
                    Serializable map = result == null ? null : result.getSerializable("map");
                    Map<String, Object> newValues = (map instanceof Map<?, ?>)
                            ? immutableCopy((Map<String, Object>) map)
                            : Collections.emptyMap();

                    Set<String> changed = new HashSet<>();
                    synchronized (this) {
                        Map<String, Object> oldValues = values;
                        for (Map.Entry<String, Object> entry : newValues.entrySet()) {
                            if (!Objects.equals(oldValues.get(entry.getKey()), entry.getValue())) {
                                changed.add(entry.getKey());
                            }
                        }
                        for (String oldKey : oldValues.keySet()) {
                            if (!newValues.containsKey(oldKey)) {
                                changed.add(oldKey);
                            }
                        }
                        values = newValues;
                    }
                    notifyListeners(changed);
                } catch (RemoteException ignored) {
                }
            });
        }

        @Override
        public Map<String, ?> getAll() {
            return new TreeMap<>(values);
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            return value == null ? defaultValue : (String) value;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<String> getStringSet(String key, Set<String> defaultValues) {
            Object value = values.get(key);
            return value == null
                    ? defaultValues
                    : new HashSet<>((Set<String>) value);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = values.get(key);
            return value == null ? defaultValue : (Integer) value;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            Object value = values.get(key);
            return value == null ? defaultValue : (Long) value;
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            Object value = values.get(key);
            return value == null ? defaultValue : (Float) value;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key);
            return value == null ? defaultValue : (Boolean) value;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new RemoteEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener
        ) {
            if (listener != null) {
                listeners.add(listener);
            }
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener
        ) {
            if (listener != null) {
                listeners.remove(listener);
            }
        }

        private void applyDiff(Bundle diff) {
            Set<String> changed = new HashSet<>();
            synchronized (this) {
                Map<String, Object> updated = new HashMap<>(values);
                if (diff.getBoolean("clear", false)) {
                    changed.addAll(updated.keySet());
                    updated.clear();
                }

                Serializable deletes = diff.getSerializable("delete");
                if (deletes instanceof Set<?>) {
                    for (Object item : (Set<?>) deletes) {
                        if (item instanceof String && updated.containsKey(item)) {
                            updated.remove(item);
                            changed.add((String) item);
                        }
                    }
                }

                Serializable puts = diff.getSerializable("put");
                if (puts instanceof Map<?, ?>) {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) puts).entrySet()) {
                        if (!(entry.getKey() instanceof String) || entry.getValue() == null) {
                            continue;
                        }
                        String key = (String) entry.getKey();
                        if (!Objects.equals(updated.put(key, entry.getValue()), entry.getValue())) {
                            changed.add(key);
                        }
                    }
                }
                values = immutableCopy(updated);
            }
            notifyListeners(changed);
        }

        private void replaceWithEmpty() {
            Set<String> changed;
            synchronized (this) {
                changed = new HashSet<>(values.keySet());
                values = Collections.emptyMap();
            }
            notifyListeners(changed);
        }

        private void notifyListeners(Set<String> changed) {
            if (changed.isEmpty()) {
                return;
            }
            List<OnSharedPreferenceChangeListener> snapshot =
                    new ArrayList<>(listeners);
            for (String key : changed) {
                for (OnSharedPreferenceChangeListener listener : snapshot) {
                    listener.onSharedPreferenceChanged(this, key);
                }
            }
        }

        private static Map<String, Object> immutableCopy(Map<String, Object> source) {
            Map<String, Object> copy = new HashMap<>();
            source.forEach((key, value) -> {
                if (value instanceof Set<?>) {
                    copy.put(key, new HashSet<>((Set<?>) value));
                } else {
                    copy.put(key, value);
                }
            });
            return Collections.unmodifiableMap(copy);
        }

        private final class RemoteEditor implements Editor {
            private boolean clear;
            private final Set<String> deletes = new HashSet<>();
            private final Map<String, Object> puts = new HashMap<>();

            private Editor put(String key, Object value) {
                if (value == null) {
                    return remove(key);
                }
                deletes.remove(key);
                puts.put(key, value);
                return this;
            }

            @Override
            public Editor putString(String key, String value) {
                return put(key, value);
            }

            @Override
            public Editor putStringSet(String key, Set<String> value) {
                return put(key, value == null ? null : new HashSet<>(value));
            }

            @Override
            public Editor putInt(String key, int value) {
                return put(key, value);
            }

            @Override
            public Editor putLong(String key, long value) {
                return put(key, value);
            }

            @Override
            public Editor putFloat(String key, float value) {
                return put(key, value);
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                return put(key, value);
            }

            @Override
            public Editor remove(String key) {
                puts.remove(key);
                deletes.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                puts.clear();
                deletes.clear();
                return this;
            }

            @Override
            public boolean commit() {
                Bundle diff = buildDiff();
                if (diff == null) {
                    return true;
                }
                try {
                    service.updateRemotePreferences(group, diff);
                    applyDiff(diff);
                    return true;
                } catch (RemoteException exception) {
                    return false;
                }
            }

            @Override
            public void apply() {
                Bundle diff = buildDiff();
                if (diff == null) {
                    return;
                }
                applyDiff(diff);
                EXECUTOR.execute(() -> {
                    try {
                        service.updateRemotePreferences(group, diff);
                    } catch (RemoteException ignored) {
                        // SharedPreferences.apply() cannot report asynchronous persistence errors.
                    }
                });
            }

            private Bundle buildDiff() {
                if (!clear && deletes.isEmpty() && puts.isEmpty()) {
                    return null;
                }
                Bundle diff = new Bundle();
                diff.putBoolean("clear", clear);
                diff.putSerializable("delete", new HashSet<>(deletes));
                diff.putSerializable("put", new HashMap<>(puts));
                return diff;
            }
        }
    }
}
