/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package example.cosmos.rpc.netty;

import example.cosmos.common.ConfigurationKeys;
import example.cosmos.common.XID;
import example.cosmos.common.thread.NamedThreadFactory;
import example.cosmos.rpc.RemotingBootstrap;
import example.cosmos.rpc.codec.ProtocolV1Decoder;
import example.cosmos.rpc.codec.ProtocolV1Encoder;
import example.cosmos.rpc.registry.RegistryFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static example.cosmos.rpc.DefaultValues.SERVICE_DEFAULT_PORT;


/**
 * Rpc server bootstrap.
 *
 * @author zhangchenghui.dev@gmail.com
 * @since 1.1.0
 */
public class NettyServerBootstrap implements RemotingBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyServerBootstrap.class);
    public static final String SERVER_PORT = "8091";
    private final ServerBootstrap serverBootstrap = new ServerBootstrap();
    private final EventLoopGroup eventLoopGroupWorker;
    private final EventLoopGroup eventLoopGroupBoss;
    private final NettyServerConfig nettyServerConfig;
    private ChannelHandler[] channelHandlers;
    private int listenPort;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public NettyServerBootstrap(NettyServerConfig nettyServerConfig) {
        this.nettyServerConfig = nettyServerConfig;
        if (NettyServerConfig.enableEpoll()) {
            this.eventLoopGroupBoss = new EpollEventLoopGroup(nettyServerConfig.getBossThreadSize(),
                new NamedThreadFactory(nettyServerConfig.getBossThreadPrefix(), nettyServerConfig.getBossThreadSize()));
            this.eventLoopGroupWorker = new EpollEventLoopGroup(nettyServerConfig.getServerWorkerThreads(),
                new NamedThreadFactory(nettyServerConfig.getWorkerThreadPrefix(),
                    nettyServerConfig.getServerWorkerThreads()));
        } else {
            this.eventLoopGroupBoss = new NioEventLoopGroup(nettyServerConfig.getBossThreadSize(),
                new NamedThreadFactory(nettyServerConfig.getBossThreadPrefix(), nettyServerConfig.getBossThreadSize()));
            this.eventLoopGroupWorker = new NioEventLoopGroup(nettyServerConfig.getServerWorkerThreads(),
                new NamedThreadFactory(nettyServerConfig.getWorkerThreadPrefix(),
                    nettyServerConfig.getServerWorkerThreads()));
        }
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

    /**
     * use for mock
     *
     * @param listenPort the listen port
     */
    public void setListenPort(int listenPort) {
        if (listenPort <= 0) {
            throw new IllegalArgumentException("listen port: " + listenPort + " is invalid!");
        }
        this.listenPort = listenPort;
    }

    /**
     * Gets listen port.
     *
     * @return the listen port
     */
    public int getListenPort() {
        if (listenPort != 0) {
            return listenPort;
        }
        String strPort = SERVER_PORT;
        int port = 0;
        try {
            port = Integer.parseInt(strPort);
        } catch (NumberFormatException exx) {
            LOGGER.error("server service port set error:{}", exx.getMessage());
        }
        if (port <= 0) {
            LOGGER.error("listen port: {} is invalid, will use default port:{}", port, SERVICE_DEFAULT_PORT);
            port = SERVICE_DEFAULT_PORT;
        }
        listenPort = port;
        return port;
    }

    @Override
    public void start() {
        this.serverBootstrap.group(this.eventLoopGroupBoss, this.eventLoopGroupWorker)
            .channel(NettyServerConfig.SERVER_CHANNEL_CLAZZ)
            .option(ChannelOption.SO_BACKLOG, nettyServerConfig.getSoBackLogSize())
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_SNDBUF, nettyServerConfig.getServerSocketSendBufSize())
            .childOption(ChannelOption.SO_RCVBUF, nettyServerConfig.getServerSocketResvBufSize())
            .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                new WriteBufferWaterMark(nettyServerConfig.getWriteBufferLowWaterMark(),
                    nettyServerConfig.getWriteBufferHighWaterMark()))
            .localAddress(new InetSocketAddress(getListenPort()))
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new IdleStateHandler(nettyServerConfig.getChannelMaxReadIdleSeconds(), 0, 0))
                        .addLast(new ProtocolV1Decoder())
                        .addLast(new ProtocolV1Encoder());
                    if (channelHandlers != null) {
                        addChannelPipelineLast(ch, channelHandlers);
                    }
                }
            });

        try {
            ChannelFuture channelFuture = this.serverBootstrap.bind(getListenPort()).sync();
            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        LOGGER.info("bind server success");
                    } else {
                        LOGGER.error("bind server fail");
                    }
                }
            });
            XID.setPort(getListenPort());
            LOGGER.info("Server started, service listen port: {}", getListenPort());
            RegistryFactory.getInstance().register(new InetSocketAddress(XID.getIpAddress(), XID.getPort()));
            initialized.set(true);

            ChannelFuture closeFuture=  channelFuture.channel().closeFuture();
            closeFuture.sync();
        } catch (SocketException se) {
            throw new RuntimeException("Server start failed, the listen port: " + getListenPort(), se);
        } catch (Exception exx) {
            throw new RuntimeException("Server start failed", exx);
        }
    }

    @Override
    public void shutdown() {
        try {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Shutting server down, the listen port: {}", XID.getPort());
            }
            if (initialized.get()) {
                RegistryFactory.getInstance().unregister(new InetSocketAddress(XID.getIpAddress(), XID.getPort()));
                RegistryFactory.getInstance().close();
                //wait a few seconds for server transport
                TimeUnit.SECONDS.sleep(nettyServerConfig.getServerShutdownWaitTime());
            }

            this.eventLoopGroupBoss.shutdownGracefully();
            this.eventLoopGroupWorker.shutdownGracefully();
        } catch (Exception exx) {
            LOGGER.error("shutdown execute error: {}", exx.getMessage(), exx);
        }
    }
}
