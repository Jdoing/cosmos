package example.cosmos.rpc;

import io.netty.channel.Channel;
import io.netty.channel.ServerChannel;

/**
 * 2022/9/4 16:55
 */
public class NettyBaseConfig {

    /**
     * The constant TRANSPORT_PROTOCOL_TYPE.
     */

    protected static final int DEFAULT_WRITE_IDLE_SECONDS = 5;

    protected static final int READIDLE_BASE_WRITEIDLE = 3;


    /**
     * The constant MAX_WRITE_IDLE_SECONDS.
     */
    protected static final int MAX_WRITE_IDLE_SECONDS = DEFAULT_WRITE_IDLE_SECONDS;

    /**
     * The constant MAX_READ_IDLE_SECONDS.
     */
    protected static final int MAX_READ_IDLE_SECONDS =  MAX_WRITE_IDLE_SECONDS * READIDLE_BASE_WRITEIDLE;

    /**
     * The constant MAX_ALL_IDLE_SECONDS.
     */
    protected static final int MAX_ALL_IDLE_SECONDS = 0;
}
