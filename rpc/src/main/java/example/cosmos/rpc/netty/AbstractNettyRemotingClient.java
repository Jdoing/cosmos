package example.cosmos.rpc.netty;

import example.cosmos.common.CollectionUtils;
import example.cosmos.common.NetUtil;
import example.cosmos.common.Pair;
import example.cosmos.common.StringUtils;
import example.cosmos.common.exception.FrameworkErrorCode;
import example.cosmos.common.exception.FrameworkException;
import example.cosmos.rpc.*;
import example.cosmos.rpc.loadbalance.LoadBalanceFactory;
import example.cosmos.rpc.protocol.HeartbeatMessage;
import example.cosmos.rpc.registry.RegistryFactory;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

import static example.cosmos.common.exception.FrameworkErrorCode.NoAvailableService;

/**
 * 2022/9/4 16:51
 */
public abstract class AbstractNettyRemotingClient extends AbstractNettyRemoting implements RemotingClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractNettyRemotingClient.class);

    private final NettyClientBootstrap clientBootstrap;

    private final ConcurrentMap<String /* addr */, Channel> channelTables = new ConcurrentHashMap<>();

    @Override
    public void init() {
//        timerExecutor.scheduleAtFixedRate(new Runnable() {
//            @Override
//            public void run() {
//                clientChannelManager.reconnect(getTransactionServiceGroup());
//            }
//        }, SCHEDULE_DELAY_MILLS, SCHEDULE_INTERVAL_MILLS, TimeUnit.MILLISECONDS);

        super.init();
        clientBootstrap.start();
    }

    public synchronized Channel getOrCreateChannel(String addr) {
        String[] serverArr = addr.split(":");
        String host = serverArr[0];
        int port = Integer.parseInt(serverArr[1]);

        Channel channel = channelTables.get(addr);
        if (channel == null) {
            channel =  clientBootstrap.connect(host, port, 3);
            channelTables.put(addr, channel);
        }

        return channel;
    }

    private String getAddr(String host, int port) {
        return  host + ":" + port;
    }


    public AbstractNettyRemotingClient(NettyClientConfig nettyClientConfig, EventExecutorGroup eventExecutorGroup,
                                       ThreadPoolExecutor messageExecutor, NettyPoolKey.TransactionRole transactionRole) {
        super(messageExecutor);
        clientBootstrap = new NettyClientBootstrap(nettyClientConfig, eventExecutorGroup, transactionRole);
        clientBootstrap.setChannelHandlers(new ClientHandler());
//        clientChannelManager = new NettyClientChannelManager(
//                new NettyPoolableFactory(this, clientBootstrap), getPoolKeyFunction(), nettyClientConfig);
    }

    /**
     * Get pool key function.
     *
     * @return lambda function
     */
    protected abstract Function<String, NettyPoolKey> getPoolKeyFunction();

    /**
     * The type ClientHandler.
     */
    @ChannelHandler.Sharable
    class ClientHandler extends ChannelDuplexHandler {

        @Override
        public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!(msg instanceof RpcMessage)) {
                return;
            }
            processMessage(ctx, (RpcMessage) msg);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            synchronized (lock) {
                if (ctx.channel().isWritable()) {
                    lock.notifyAll();
                }
            }
            ctx.fireChannelWritabilityChanged();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (messageExecutor.isShutdown()) {
                return;
            }
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("channel inactive: {}", ctx.channel());
            }
//            clientChannelManager.releaseChannel(ctx.channel(), NetUtil.toStringAddress(ctx.channel().remoteAddress()));
            super.channelInactive(ctx);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
                if (idleStateEvent.state() == IdleState.READER_IDLE) {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("channel {} read idle.", ctx.channel());
                    }
                    try {
                        String serverAddress = NetUtil.toStringAddress(ctx.channel().remoteAddress());
//                        clientChannelManager.invalidateObject(serverAddress, ctx.channel());
                    } catch (Exception exx) {
                        LOGGER.error(exx.getMessage());
                    } finally {
//                        clientChannelManager.releaseChannel(ctx.channel(), getAddressFromContext(ctx));
                    }
                }
                if (idleStateEvent == IdleStateEvent.WRITER_IDLE_STATE_EVENT) {
                    try {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("will send ping msg,channel {}", ctx.channel());
                        }
                        AbstractNettyRemotingClient.this.sendAsyncRequest(ctx.channel(), HeartbeatMessage.PING);
                    } catch (Throwable throwable) {
                        LOGGER.error("send request error: {}", throwable.getMessage(), throwable);
                    }
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            LOGGER.error(FrameworkErrorCode.ExceptionCaught.getErrCode(),
                    NetUtil.toStringAddress(ctx.channel().remoteAddress()) + "connect exception. " + cause.getMessage(), cause);
//            clientChannelManager.releaseChannel(ctx.channel(), getAddressFromChannel(ctx.channel()));
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("remove exception rm channel:{}", ctx.channel());
            }
            super.exceptionCaught(ctx, cause);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise future) throws Exception {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(ctx + " will closed");
            }
            super.close(ctx, future);
        }
    }

    @Override
    public Object sendSyncRequest(String addr, Object msg) throws TimeoutException {
//        String serverAddress = loadBalance(getTransactionServiceGroup(), msg);

        long timeoutMillis = 100000;
        RpcMessage rpcMessage = buildRequestMessage(msg, ProtocolConstants.MSGTYPE_RESQUEST_SYNC);
//        RpcMessage rpcMessage = buildRequestMessage(msg, MessageType.TYPE_ECHO);

        // send batch message
        // put message into basketMap, @see MergedSendRunnable

        Channel channel = getOrCreateChannel(addr);

        return super.sendSync(channel, rpcMessage, timeoutMillis);

    }

    protected String loadBalance(String transactionServiceGroup, Object msg) {
        InetSocketAddress address = null;
        try {
            @SuppressWarnings("unchecked")
            List<InetSocketAddress> inetSocketAddressList = RegistryFactory.getInstance().aliveLookup(transactionServiceGroup);
            address = this.doSelect(inetSocketAddressList, msg);
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage());
        }
        if (address == null) {

            return "127.0.0.1:8091";
//            throw new FrameworkException(NoAvailableService);
        }
        return NetUtil.toStringAddress(address);
    }

    protected InetSocketAddress doSelect(List<InetSocketAddress> list, Object msg) throws Exception {
        if (CollectionUtils.isNotEmpty(list)) {
            if (list.size() > 1) {
                return LoadBalanceFactory.getInstance().select(list, getXid(msg));
            } else {
                return list.get(0);
            }
        }
        return null;
    }

    protected String getXid(Object msg) {
        String xid = "";
        try {
            Field field = msg.getClass().getDeclaredField("xid");
            xid = String.valueOf(field.get(msg));
        } catch (Exception ignore) {
        }
        return StringUtils.isBlank(xid) ? String.valueOf(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE)) : xid;
    }

    @Override
    public Object sendSyncRequest(Channel channel, Object msg) throws TimeoutException {
        if (channel == null) {
            LOGGER.warn("sendSyncRequest nothing, caused by null channel.");
            return null;
        }
        RpcMessage rpcMessage = buildRequestMessage(msg, ProtocolConstants.MSGTYPE_RESQUEST_SYNC);
        return super.sendSync(channel, rpcMessage, 100000);
    }

    @Override
    public void sendAsyncRequest(Channel channel, Object msg) {
        if (channel == null) {
            LOGGER.warn("sendAsyncRequest nothing, caused by null channel.");
            return;
        }
        RpcMessage rpcMessage = buildRequestMessage(msg, msg instanceof HeartbeatMessage
                ? ProtocolConstants.MSGTYPE_HEARTBEAT_REQUEST
                : ProtocolConstants.MSGTYPE_RESQUEST_ONEWAY);
        super.sendAsync(channel, rpcMessage);
    }

    @Override
    public void sendAsyncResponse(String serverAddress, RpcMessage rpcMessage, Object msg) {
        RpcMessage rpcMsg = buildResponseMessage(rpcMessage, msg, ProtocolConstants.MSGTYPE_RESPONSE);
        Channel channel = getOrCreateChannel(serverAddress);
        super.sendAsync(channel, rpcMsg);
    }

    @Override
    public void registerProcessor(int requestCode, RemotingProcessor processor, ExecutorService executor) {
        Pair<RemotingProcessor, ExecutorService> pair = new Pair<>(processor, executor);
        this.processorTable.put(requestCode, pair);
    }

    @Override
    public void destroyChannel(String serverAddress, Channel channel) {
//        clientChannelManager.destroyChannel(serverAddress, channel);
    }

    @Override
    public void destroy() {
        clientBootstrap.shutdown();
//        if (mergeSendExecutorService != null) {
//            mergeSendExecutorService.shutdown();
//        }
        super.destroy();
    }
}
