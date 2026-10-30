package top.nkbe.npatch.remote;

/**
 * Thrown when the connected NPatch Manager is too old to support the SDK features
 * required by this version of the Remote API client.
 *
 * <p>The module app should prompt the user to update NPatch Manager.</p>
 */
public class IncompatibleManagerException extends IllegalStateException {

    private final long managerVersionCode;
    private final long requiredVersionCode;

    public IncompatibleManagerException(long managerVersionCode, long requiredVersionCode) {
        super("NPatch Manager version " + managerVersionCode
                + " is too old; this SDK requires at least version " + requiredVersionCode);
        this.managerVersionCode = managerVersionCode;
        this.requiredVersionCode = requiredVersionCode;
    }

    /** Returns the version code reported by the connected NPatch Manager. */
    public long getManagerVersionCode() {
        return managerVersionCode;
    }

    /** Returns the minimum version code required by this SDK. */
    public long getRequiredVersionCode() {
        return requiredVersionCode;
    }
}
