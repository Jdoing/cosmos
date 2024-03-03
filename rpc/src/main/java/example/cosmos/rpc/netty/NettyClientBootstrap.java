package example.cosmos.rpc.netty;

import example.cosmos.common.exception.FrameworkException;
import example.cosmos.common.thread.NamedThreadFactory;
import example.cosmos.rpc.RemotingBootstrap;
import example.cosmos.rpc.codec.ProtocolV1Decoder;
import example.cosmos.rpc.codec.ProtocolV1Encoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollMode;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.PlatformDependent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 2022/9/4 16:53
 */
@Slf4j
public class NettyClientBootstrap implements RemotingBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyClientBootstrap.class);
    private final NettyClientConfig nettyClientConfig;
    private final Bootstrap bootstrap = new Bootstrap();
    private final EventLoopGroup eventLoopGroupWorker;
    private EventExecutorGroup defaultEventExecutorGroup;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final String THREAD_PREFIX_SPLIT_CHAR = "_";
    private final NettyPoolKey.TransactionRole transactionRole;
    private ChannelHandler[] channelHandlers;

    private Channel channel;

    public Channel getChannel() {
        return channel;
    }

    public NettyClientBootstrap(NettyClientConfig nettyClientConfig, final EventExecutorGroup eventExecutorGroup,
                                NettyPoolKey.TransactionRole transactionRole) {
        if (nettyClientConfig == null) {
            nettyClientConfig = new NettyClientConfig();
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("use default netty client config.");
            }
        }
        this.nettyClientConfig = nettyClientConfig;
        int selectorThreadSizeThreadSize = this.nettyClientConfig.getClientSelectorThreadSize();
        this.transactionRole = transactionRole;
        this.eventLoopGroupWorker = new NioEventLoopGroup(selectorThreadSizeThreadSize,
                new NamedThreadFactory(getThreadPrefix(this.nettyClientConfig.getClientSelectorThreadPrefix()),
                        selectorThreadSizeThreadSize));
        this.defaultEventExecutorGroup = eventExecutorGroup;
    }

    /**
     * Sets channel handlers.
     *
     * @param handlers the handlers
     */
    protected void setChannelHandlers(final ChannelHandler... handlers) {
        if (handlers != null) {
            channelHandlers = handlers;
        }
    }

    /**
     * Add channel pipeline last.
     *
     * @param channel  the channel
     * @param handlers the handlers
     */
    private void addChannelPipelineLast(Channel channel, ChannelHandler... handlers) {
        if (channel != null && handlers != null) {
            channel.pipeline().addLast(handlers);
        }
    }

    @Override
    public void start() {
        if (this.defaultEventExecutorGroup == null) {
            this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(nettyClientConfig.getClientWorkerThreads(),
                    new NamedThreadFactory(getThreadPrefix("nettyClientBootstrap"),
                            nettyClientConfig.getClientWorkerThreads()));
        }

        this.bootstrap.group(this.eventLoopGroupWorker).channel(
                NioSocketChannel.class).option(
                ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true).option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, nettyClientConfig.getConnectTimeoutMillis()).option(
                ChannelOption.SO_SNDBUF, nettyClientConfig.getClientSocketSndBufSize()).option(ChannelOption.SO_RCVBUF,
                nettyClientConfig.getClientSocketRcvBufSize());

        if (nettyClientConfig.enableNative()) {
            if (PlatformDependent.isOsx()) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("client run on macOS");
                }
            } else {
                bootstrap.option(EpollChannelOption.EPOLL_MODE, EpollMode.EDGE_TRIGGERED)
                        .option(EpollChannelOption.TCP_QUICKACK, true);
            }
        }

        bootstrap.handler(
                new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline
//                                .addLast(
//                                        new IdleStateHandler(nettyClientConfig.getChannelMaxReadIdleSeconds(),
//                                                nettyClientConfig.getChannelMaxWriteIdleSeconds(),
//                                                nettyClientConfig.getChannelMaxAllIdleSeconds()))
                                .addLast(new ProtocolV1Decoder())
                                .addLast(new ProtocolV1Encoder());
                        if (channelHandlers != null) {
                            addChannelPipelineLast(ch, channelHandlers);
                        }
                    }
                });

        if (initialized.compareAndSet(false, true) && LOGGER.isInfoEnabled()) {
            LOGGER.info("NettyClientBootstrap has started");
        }

//        connect(bootstrap, "127.0.0.1", 8091, 3);
    }

    public Channel connect(String host, int port, int retry) {
        ChannelFuture channelFuture = bootstrap.connect(host, port).addListener(future -> {
            if (future.isSuccess()) {
                log.info("连接服务端成功");
            } else  {
                log.error("重试次数已用完，放弃连接");
            }
        });


        try {
            channelFuture.sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Channel channel = channelFuture.channel();
        channel.closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                log.info(new Date() + ": 连接已经断开……");
            }
        });

       return channel;
    }


    @Override
    public void shutdown() {
        try {
            this.eventLoopGroupWorker.shutdownGracefully();
            if (this.defaultEventExecutorGroup != null) {
                this.defaultEventExecutorGroup.shutdownGracefully();
            }
        } catch (Exception exx) {
            LOGGER.error("Failed to shutdown: {}", exx.getMessage());
        }
    }

    /**
     * Gets new channel.
     *
     * @param address the address
     * @return the new channel
     */
    public Channel getNewChannel(InetSocketAddress address) {
        Channel channel;
        ChannelFuture f = this.bootstrap.connect(address);
        try {
            f.await(this.nettyClientConfig.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (f.isCancelled()) {
                throw new FrameworkException(f.cause(), "connect cancelled, can not connect to services-server.");
            } else if (!f.isSuccess()) {
                throw new FrameworkException(f.cause(), "connect failed, can not connect to services-server.");
            } else {
                channel = f.channel();
            }
        } catch (Exception e) {
            throw new FrameworkException(e, "can not connect to services-server.");
        }
        return channel;
    }

    /**
     * Gets thread prefix.
     *
     * @param threadPrefix the thread prefix
     * @return the thread prefix
     */
    private String getThreadPrefix(String threadPrefix) {
        return threadPrefix + THREAD_PREFIX_SPLIT_CHAR + transactionRole.name();
    }
}
