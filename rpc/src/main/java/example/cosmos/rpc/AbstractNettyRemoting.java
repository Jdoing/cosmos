package example.cosmos.rpc;

import example.cosmos.common.FrameworkErrorCode;
import example.cosmos.common.FrameworkException;
import example.cosmos.common.Pair;
import example.cosmos.common.PositiveAtomicCounter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 2022/9/4 15:57
 */
public abstract class AbstractNettyRemoting implements Disposable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNettyRemoting.class);
    /**
     * The Timer executor.
     */
    protected final ScheduledExecutorService timerExecutor = new ScheduledThreadPoolExecutor(1,
            new NamedThreadFactory("timeoutChecker", 1, true));
    /**
     * The Message executor.
     */
    protected final ThreadPoolExecutor messageExecutor;

    /**
     * Id generator of this remoting
     */
    protected final PositiveAtomicCounter idGenerator = new PositiveAtomicCounter();

    /**
     * Obtain the return result through MessageFuture blocking.
     *
     * @see AbstractNettyRemoting#sendSync
     */
    protected final ConcurrentHashMap<Integer, MessageFuture> futures = new ConcurrentHashMap<>();

    private static final long NOT_WRITEABLE_CHECK_MILLS = 10L;

    /**
     * The Now mills.
     */
    protected volatile long nowMills = 0;
    private static final int TIMEOUT_CHECK_INTERVAL = 3000;
    protected final Object lock = new Object();
    /**
     * The Is sending.
     */
    protected volatile boolean isSending = false;
    private String group = "DEFAULT";

    /**
     * This container holds all processors.
     * processor type {@link MessageType}
     */
    protected final HashMap<Integer/*MessageType*/, Pair<RemotingProcessor, ExecutorService>> processorTable = new HashMap<>(32);

    public void init() {
        timerExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<Integer, MessageFuture> entry : futures.entrySet()) {
                    MessageFuture future = entry.getValue();
                    if (future.isTimeout()) {
                        futures.remove(entry.getKey());
                        RpcMessage rpcMessage = future.getRequestMessage();
                        future.setResultMessage(new TimeoutException(String
                                .format("msgId: %s ,msgType: %s ,msg: %s ,request timeout", rpcMessage.getId(), String.valueOf(rpcMessage.getMessageType()), rpcMessage.getBody().toString())));
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("timeout clear future: {}", entry.getValue().getRequestMessage().getBody());
                        }
                    }
                }

                nowMills = System.currentTimeMillis();
            }
        }, TIMEOUT_CHECK_INTERVAL, TIMEOUT_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public AbstractNettyRemoting(ThreadPoolExecutor messageExecutor) {
        this.messageExecutor = messageExecutor;
    }

    public int getNextMessageId() {
        return idGenerator.incrementAndGet();
    }

    public ConcurrentHashMap<Integer, MessageFuture> getFutures() {
        return futures;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void destroyChannel(Channel channel) {
        destroyChannel(getAddressFromChannel(channel), channel);
    }

    /**
     * rpc sync request
     * Obtain the return result through MessageFuture blocking.
     *
     * @param channel       netty channel
     * @param rpcMessage    rpc message
     * @param timeoutMillis rpc communication timeout
     * @return response message
     * @throws TimeoutException
     */
    protected Object sendSync(Channel channel, RpcMessage rpcMessage, long timeoutMillis) throws TimeoutException {
        if (timeoutMillis <= 0) {
            throw new RuntimeException("timeout should more than 0ms");
        }
        if (channel == null) {
            LOGGER.warn("sendSync nothing, caused by null channel.");
            return null;
        }

        MessageFuture messageFuture = new MessageFuture();
        messageFuture.setRequestMessage(rpcMessage);
        messageFuture.setTimeout(timeoutMillis);
        futures.put(rpcMessage.getId(), messageFuture);

        channelWritableCheck(channel, rpcMessage.getBody());

        String remoteAddr = ChannelUtil.getAddressFromChannel(channel);

        channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                MessageFuture messageFuture1 = futures.remove(rpcMessage.getId());
                if (messageFuture1 != null) {
                    messageFuture1.setResultMessage(future.cause());
                }
                destroyChannel(future.channel());
            }
        });

        try {
            Object result = messageFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return result;
        } catch (Exception exx) {
            LOGGER.error("wait response error:{},ip:{},request:{}", exx.getMessage(), channel.remoteAddress(),
                    rpcMessage.getBody());
            if (exx instanceof TimeoutException) {
                throw (TimeoutException) exx;
            } else {
                throw new RuntimeException(exx);
            }
        }
    }
    /**
     * rpc async request.
     *
     * @param channel    netty channel
     * @param rpcMessage rpc message
     */
    protected void sendAsync(Channel channel, RpcMessage rpcMessage) {
        channelWritableCheck(channel, rpcMessage.getBody());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("write message:" + rpcMessage.getBody() + ", channel:" + channel + ",active?"
                    + channel.isActive() + ",writable?" + channel.isWritable() + ",isopen?" + channel.isOpen());
        }

        channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                destroyChannel(future.channel());
            }
        });
    }

    protected RpcMessage buildRequestMessage(Object msg, byte messageType) {
        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setId(getNextMessageId());
        rpcMessage.setMessageType(messageType);
        rpcMessage.setCodec(ProtocolConstants.CONFIGURED_CODEC);
        rpcMessage.setCompressor(ProtocolConstants.CONFIGURED_COMPRESSOR);
        rpcMessage.setBody(msg);
        return rpcMessage;
    }

    protected RpcMessage buildResponseMessage(RpcMessage rpcMessage, Object msg, byte messageType) {
        RpcMessage rpcMsg = new RpcMessage();
        rpcMsg.setMessageType(messageType);
        rpcMsg.setCodec(rpcMessage.getCodec()); // same with request
        rpcMsg.setCompressor(rpcMessage.getCompressor());
        rpcMsg.setBody(msg);
        rpcMsg.setId(rpcMessage.getId());
        return rpcMsg;
    }
    /**
     * Rpc message processing.
     *
     * @param ctx        Channel handler context.
     * @param rpcMessage rpc message.
     * @throws Exception throws exception process message error.
     * @since 1.3.0
     */
    protected void processMessage(ChannelHandlerContext ctx, RpcMessage rpcMessage) throws Exception {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("%s msgId:%s, body:%s", this, rpcMessage.getId(), rpcMessage.getBody()));
        }
        Object body = rpcMessage.getBody();
        if (body instanceof MessageTypeAware) {
            MessageTypeAware messageTypeAware = (MessageTypeAware) body;
            final Pair<RemotingProcessor, ExecutorService> pair = this.processorTable.get((int) messageTypeAware.getTypeCode());
            if (pair != null) {
                if (pair.getSecond() != null) {
                    try {
                        pair.getSecond().execute(() -> {
                            try {
                                pair.getFirst().process(ctx, rpcMessage);
                            } catch (Throwable th) {
                                LOGGER.error(FrameworkErrorCode.NetDispatch.getErrCode(), th.getMessage(), th);
                            } finally {
                                MDC.clear();
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        LOGGER.error(FrameworkErrorCode.ThreadPoolFull.getErrCode(),
                                "thread pool is full, current max pool size is " + messageExecutor.getActiveCount());
                    }
                } else {
                    try {
                        pair.getFirst().process(ctx, rpcMessage);
                    } catch (Throwable th) {
                        LOGGER.error(FrameworkErrorCode.NetDispatch.getErrCode(), th.getMessage(), th);
                    }
                }
            } else {
                LOGGER.error("This message type [{}] has no processor.", messageTypeAware.getTypeCode());
            }
        } else {
            LOGGER.error("This rpcMessage body[{}] is not MessageTypeAware type.", body);
        }
    }


    /**
     * Destroy channel.
     *
     * @param serverAddress the server address
     * @param channel       the channel
     */
    public abstract void destroyChannel(String serverAddress, Channel channel);

    @Override
    public void destroy() {
        timerExecutor.shutdown();
        messageExecutor.shutdown();
    }

    private void channelWritableCheck(Channel channel, Object msg) {
        int tryTimes = 0;
        synchronized (lock) {
            while (!channel.isWritable()) {
                try {
                    tryTimes++;
                    if (tryTimes > 3) {
                        destroyChannel(channel);
                        throw new FrameworkException("msg:" + ((msg == null) ? "null" : msg.toString()),
                                FrameworkErrorCode.ChannelIsNotWritable);
                    }
                    lock.wait(NOT_WRITEABLE_CHECK_MILLS);
                } catch (InterruptedException exx) {
                    LOGGER.error(exx.getMessage());
                }
            }
        }
    }


    /**
     * Gets address from channel.
     *
     * @param channel the channel
     * @return the address from channel
     */
    protected String getAddressFromChannel(Channel channel) {
        SocketAddress socketAddress = channel.remoteAddress();
        String address = socketAddress.toString();
        if (socketAddress.toString().indexOf(SOCKET_ADDRESS_START_CHAR) == 0) {
            address = socketAddress.toString().substring(SOCKET_ADDRESS_START_CHAR.length());
        }
        return address;
    }

    private static final String SOCKET_ADDRESS_START_CHAR = "/";

    public static String getSocketAddressStartChar() {
        return SOCKET_ADDRESS_START_CHAR;
    }


}
