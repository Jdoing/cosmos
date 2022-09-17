package example.cosmos.rpc.netty;

import example.cosmos.common.ConfigurationKeys;
import example.cosmos.common.config.Configuration;
import example.cosmos.common.config.FileConfiguration;
import example.cosmos.rpc.TransportProtocolType;
import example.cosmos.rpc.TransportServerType;
import io.netty.channel.Channel;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.NettyRuntime;
import io.netty.util.internal.PlatformDependent;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static example.cosmos.rpc.DefaultValues.DEFAULT_TRANSPORT_HEARTBEAT;

/**
 * 2022/9/4 16:55
 */
public class NettyBaseConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyBaseConfig.class);

    /**
     * The constant TRANSPORT_PROTOCOL_TYPE.
     */

    protected static final int DEFAULT_WRITE_IDLE_SECONDS = 5;

    protected static final int READIDLE_BASE_WRITEIDLE = 3;


    /**
     * The constant MAX_WRITE_IDLE_SECONDS.
     */
    protected static final int MAX_WRITE_IDLE_SECONDS ;

    /**
     * The constant MAX_READ_IDLE_SECONDS.
     */
    protected static final int MAX_READ_IDLE_SECONDS ;

    /**
     * The constant MAX_ALL_IDLE_SECONDS.
     */
    protected static final int MAX_ALL_IDLE_SECONDS = 0;

    /**
     * The constant SERVER_CHANNEL_CLAZZ.
     */
    protected static final Class<? extends ServerChannel> SERVER_CHANNEL_CLAZZ;
    /**
     * The constant CLIENT_CHANNEL_CLAZZ.
     */
    protected static final Class<? extends Channel> CLIENT_CHANNEL_CLAZZ;

    /**
     * The constant TRANSPORT_PROTOCOL_TYPE.
     */
    protected static final TransportProtocolType TRANSPORT_PROTOCOL_TYPE;

    /**
     * The constant TRANSPORT_SERVER_TYPE.
     */
    protected static final TransportServerType TRANSPORT_SERVER_TYPE;
    /**
     * The constant WORKER_THREAD_SIZE.
     */
    protected static int WORKER_THREAD_SIZE;

    /**
     * The constant CONFIG.
     */
    protected static final Configuration CONFIG = new FileConfiguration();

    static {
        TRANSPORT_PROTOCOL_TYPE = TransportProtocolType.getType(CONFIG.getConfig(ConfigurationKeys.TRANSPORT_TYPE, TransportProtocolType.TCP.name()));
        String workerThreadSize = CONFIG.getConfig(ConfigurationKeys.WORKER_THREAD_SIZE);
        if (StringUtils.isNotBlank(workerThreadSize) && StringUtils.isNumeric(workerThreadSize)) {
            WORKER_THREAD_SIZE = Integer.parseInt(workerThreadSize);
        } else if (WorkThreadMode.getModeByName(workerThreadSize) != null) {
            WORKER_THREAD_SIZE = WorkThreadMode.getModeByName(workerThreadSize).getValue();
        } else {
            WORKER_THREAD_SIZE = WorkThreadMode.Default.getValue();
        }
        TRANSPORT_SERVER_TYPE = TransportServerType.getType(CONFIG.getConfig(ConfigurationKeys.TRANSPORT_SERVER, TransportServerType.NIO.name()));
        switch (TRANSPORT_SERVER_TYPE) {
            case NIO:
                if (TRANSPORT_PROTOCOL_TYPE == TransportProtocolType.TCP) {
                    SERVER_CHANNEL_CLAZZ = NioServerSocketChannel.class;
                    CLIENT_CHANNEL_CLAZZ = NioSocketChannel.class;
                } else {
                    raiseUnsupportedTransportError();
                    SERVER_CHANNEL_CLAZZ = null;
                    CLIENT_CHANNEL_CLAZZ = null;
                }
                break;
            case NATIVE:
                if (PlatformDependent.isWindows()) {
                    throw new IllegalArgumentException("no native supporting for Windows.");
                } else if (PlatformDependent.isOsx()) {
                    if (TRANSPORT_PROTOCOL_TYPE == TransportProtocolType.TCP) {
                        SERVER_CHANNEL_CLAZZ = KQueueServerSocketChannel.class;
                        CLIENT_CHANNEL_CLAZZ = KQueueSocketChannel.class;
                    } else if (TRANSPORT_PROTOCOL_TYPE == TransportProtocolType.UNIX_DOMAIN_SOCKET) {
                        SERVER_CHANNEL_CLAZZ = KQueueServerDomainSocketChannel.class;
                        CLIENT_CHANNEL_CLAZZ = KQueueDomainSocketChannel.class;
                    } else {
                        raiseUnsupportedTransportError();
                        SERVER_CHANNEL_CLAZZ = null;
                        CLIENT_CHANNEL_CLAZZ = null;
                    }
                } else {
                    if (TRANSPORT_PROTOCOL_TYPE == TransportProtocolType.TCP) {
                        SERVER_CHANNEL_CLAZZ = EpollServerSocketChannel.class;
                        CLIENT_CHANNEL_CLAZZ = EpollSocketChannel.class;
                    } else if (TRANSPORT_PROTOCOL_TYPE == TransportProtocolType.UNIX_DOMAIN_SOCKET) {
                        SERVER_CHANNEL_CLAZZ = EpollServerDomainSocketChannel.class;
                        CLIENT_CHANNEL_CLAZZ = EpollDomainSocketChannel.class;
                    } else {
                        raiseUnsupportedTransportError();
                        SERVER_CHANNEL_CLAZZ = null;
                        CLIENT_CHANNEL_CLAZZ = null;
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("unsupported.");
        }
        boolean enableHeartbeat = CONFIG.getBoolean(ConfigurationKeys.TRANSPORT_HEARTBEAT, DEFAULT_TRANSPORT_HEARTBEAT);
        if (enableHeartbeat) {
            MAX_WRITE_IDLE_SECONDS = DEFAULT_WRITE_IDLE_SECONDS;
        } else {
            MAX_WRITE_IDLE_SECONDS = 0;
        }
        MAX_READ_IDLE_SECONDS = MAX_WRITE_IDLE_SECONDS * READIDLE_BASE_WRITEIDLE;
    }

    private static void raiseUnsupportedTransportError() throws RuntimeException {
        String errMsg = String.format("Unsupported provider type :[%s] for transport:[%s].", TRANSPORT_SERVER_TYPE,
                TRANSPORT_PROTOCOL_TYPE);
        LOGGER.error(errMsg);
        throw new IllegalArgumentException(errMsg);
    }

    /**
     * The enum Work thread mode.
     */
    public enum WorkThreadMode {

        /**
         * Auto work thread mode.
         */
        Auto(NettyRuntime.availableProcessors() * 2 + 1),
        /**
         * Pin work thread mode.
         */
        Pin(NettyRuntime.availableProcessors()),
        /**
         * Busy pin work thread mode.
         */
        BusyPin(NettyRuntime.availableProcessors() + 1),
        /**
         * Default work thread mode.
         */
        Default(NettyRuntime.availableProcessors() * 2);

        /**
         * Gets value.
         *
         * @return the value
         */
        public int getValue() {
            return value;
        }

        private int value;

        WorkThreadMode(int value) {
            this.value = value;
        }

        /**
         * Gets mode by name.
         *
         * @param name the name
         * @return the mode by name
         */
        public static WorkThreadMode getModeByName(String name) {
            for (WorkThreadMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return null;
        }
    }
}
