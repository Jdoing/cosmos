package example.cosmos.rpc;


import static example.cosmos.rpc.DefaultValues.*;

/**
 * 2022/9/4 16:55
 */
public class NettyClientConfig extends NettyBaseConfig {

    private int connectTimeoutMillis = 10000;
    private int clientSocketSndBufSize = 153600;
    private int clientSocketRcvBufSize = 153600;
    private int clientWorkerThreads = 8;
    private int perHostMaxConn = 2;
    private static final int PER_HOST_MIN_CONN = 2;
    private int pendingConnSize = Integer.MAX_VALUE;
    private static String vgroup;
    private static String clientAppName;
    private static int clientType;
    private static int maxInactiveChannelCheck = 10;
    private static final int MAX_NOT_WRITEABLE_RETRY = 2000;
    private static final int MAX_CHECK_ALIVE_RETRY = 300;
    private static final int CHECK_ALIVE_INTERVAL = 10;
    private static final String SOCKET_ADDRESS_START_CHAR = "/";
    private static final long MAX_ACQUIRE_CONN_MILLS = 60 * 1000L;
    private static final String RPC_DISPATCH_THREAD_PREFIX = "rpcDispatch";
    private static final int DEFAULT_MAX_POOL_ACTIVE = 1;
    private static final int DEFAULT_MIN_POOL_IDLE = 0;
    private static final boolean DEFAULT_POOL_TEST_BORROW = true;
    private static final boolean DEFAULT_POOL_TEST_RETURN = true;
    private static final boolean DEFAULT_POOL_LIFO = true;


    /**
     * Gets socket address start char.
     *
     * @return the socket address start char
     */
    public static String getSocketAddressStartChar() {
        return SOCKET_ADDRESS_START_CHAR;
    }
    /**
     * Get max acquire conn mills long.
     *
     * @return the long
     */
    public long getMaxAcquireConnMills() {
        return MAX_ACQUIRE_CONN_MILLS;
    }

    /**
     * Gets max check alive retry.
     *
     * @return the max check alive retry
     */
    public static int getMaxCheckAliveRetry() {
        return MAX_CHECK_ALIVE_RETRY;
    }

    /**
     * Gets max pool active.
     *
     * @return the max pool active
     */
    public int getMaxPoolActive() {
        return DEFAULT_MAX_POOL_ACTIVE;
    }

    /**
     * Gets min pool idle.
     *
     * @return the min pool idle
     */
    public int getMinPoolIdle() {
        return DEFAULT_MIN_POOL_IDLE;
    }

    /**
     * Is pool test borrow boolean.
     *
     * @return the boolean
     */
    public boolean isPoolTestBorrow() {
        return DEFAULT_POOL_TEST_BORROW;
    }

    /**
     * Is pool test return boolean.
     *
     * @return the boolean
     */
    public boolean isPoolTestReturn() {
        return DEFAULT_POOL_TEST_RETURN;
    }

    /**
     * Is pool fifo boolean.
     *
     * @return the boolean
     */
    public boolean isPoolLifo() {
        return DEFAULT_POOL_LIFO;
    }

    /**
     * Gets channel max all idle seconds.
     *
     * @return the channel max all idle seconds
     */
    public int getChannelMaxAllIdleSeconds() {
        return MAX_ALL_IDLE_SECONDS;
    }

    /**
     * Gets client channel max idle time seconds.
     *
     * @return the client channel max idle time seconds
     */
    public int getChannelMaxWriteIdleSeconds() {
        return MAX_WRITE_IDLE_SECONDS;
    }
    /**
     * Gets channel max read idle seconds.
     *
     * @return the channel max read idle seconds
     */
    public int getChannelMaxReadIdleSeconds() {
        return DEFAULT_WRITE_IDLE_SECONDS * READIDLE_BASE_WRITEIDLE;
    }

    /**
     * Gets client socket rcv buf size.
     *
     * @return the client socket rcv buf size
     */
    public int getClientSocketRcvBufSize() {
        return clientSocketRcvBufSize;
    }
    /**
     * Gets client socket snd buf size.
     *
     * @return the client socket snd buf size
     */
    public int getClientSocketSndBufSize() {
        return clientSocketSndBufSize;
    }
    /**
     * Gets client selector thread size.
     *
     * @return the client selector thread size
     */
    public int getClientSelectorThreadSize() {
        return DEFAULT_SELECTOR_THREAD_SIZE;
    }

    /**
     * Get client selector thread prefix string.
     *
     * @return the string
     */
    public String getClientSelectorThreadPrefix() {
        return DEFAULT_SELECTOR_THREAD_PREFIX;
    }

    /**
     * Enable native boolean.
     *
     * @return the boolean
     */
    public boolean enableNative() {
        return TransportServerType.NIO == TransportServerType.NATIVE;
    }

    /**
     * Gets client worker threads.
     *
     * @return the client worker threads
     */
    public int getClientWorkerThreads() {
        return clientWorkerThreads;
    }

    /**
     * Gets connect timeout millis.
     *
     * @return the connect timeout millis
     */
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }
}
