package top.nkbe.npatch.remote;

/**
 * Diagnostic status returned by {@link NPatchRemoteClient#probe}.
 *
 * <p>Indicates why the NPatch remote service is or is not reachable, so the
 * module app can show actionable guidance instead of a generic error.</p>
 */
public enum RemoteStatus {

    /**
     * The NPatch Manager ContentProvider is resolvable, the module is configured,
     * and a service binder was successfully obtained. The connection is ready to use.
     */
    READY,

    /**
     * No package on this device exposes the expected ContentProvider authority.
     * Either NPatch Manager is not installed, or the caller did not declare the
     * required {@code <queries>} element in its manifest (Android 11+).
     */
    MANAGER_NOT_FOUND,

    /**
     * The ContentProvider was resolved but returned {@code null} when asked for a
     * service binder. This typically means the calling module package is not
     * configured (enabled) in NPatch Manager.
     */
    MODULE_NOT_CONFIGURED,

    /**
     * The ContentProvider was resolved but the connection attempt timed out.
     * The Manager process may be starting up or under heavy load.
     */
    CONNECTION_TIMEOUT,

    /**
     * The remote service connection was explicitly denied. The calling UID does
     * not match the module package, or the Manager rejected the request for
     * security reasons.
     */
    DENIED,

    /**
     * An unexpected error occurred during the probe. Call
     * {@link NPatchRemoteClient#probe} again or inspect logcat for details.
     */
    UNKNOWN_ERROR
}
