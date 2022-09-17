package example.cosmos.rpc.protocol;

import example.cosmos.rpc.MessageTypeAware;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.charset.Charset;

/**
 * 2022/9/4 15:47
 */
public abstract class AbstractMessage implements MessageTypeAware, Serializable {
    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractMessage.class);

    protected static final long serialVersionUID = -1441020418526899889L;

    /**
     * The constant UTF8.
     */
    protected static final Charset UTF8 = Charset.forName("UTF-8");
    /**
     * The Ctx.
     */
    protected ChannelHandlerContext ctx;

    /**
     * Bytes to int int.
     *
     * @param bytes  the bytes
     * @param offset the offset
     * @return the int
     */
    public static int bytesToInt(byte[] bytes, int offset) {
        int ret = 0;
        for (int i = 0; i < 4 && i + offset < bytes.length; i++) {
            ret <<= 8;
            ret |= (int)bytes[i + offset] & 0xFF;
        }
        return ret;
    }

    /**
     * Int to bytes.
     *
     * @param i      the
     * @param bytes  the bytes
     * @param offset the offset
     */
    public static void intToBytes(int i, byte[] bytes, int offset) {
        bytes[offset] = (byte)((i >> 24) & 0xFF);
        bytes[offset + 1] = (byte)((i >> 16) & 0xFF);
        bytes[offset + 2] = (byte)((i >> 8) & 0xFF);
        bytes[offset + 3] = (byte)(i & 0xFF);
    }


}
