package hev.htproxy;

/**
 * JNI binding for the native HevSocks5Tunnel library.
 *
 * The native library registers these methods against this exact class name.
 */
public final class TProxyService {
    private TProxyService() {
    }

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    public static native boolean TProxyStartService(String config_path, int fd);

    public static native boolean TProxyStopService();

    public static native boolean TProxyIsRunning();

    public static native long[] TProxyGetStats();
}
