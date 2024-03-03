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

import example.cosmos.common.thread.NamedThreadFactory;
import example.cosmos.rpc.MessageType;
import example.cosmos.rpc.processor.server.ServerEchoProcessor;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The netty remoting server.
 *
 * @author slievrly
 * @author xingfudeshi@gmail.com
 * @author zhangchenghui.dev@gmail.com
 */
public class NettyRemotingServer extends AbstractNettyRemotingServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyRemotingServer.class);

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    @Override
    public void init() {
        // registry processor
        registerProcessor();
        if (initialized.compareAndSet(false, true)) {
            super.init();
        }
    }

    /**
     * Instantiates a new Rpc remoting server.
     *
     * @param messageExecutor   the message executor
     */
    public NettyRemotingServer(ThreadPoolExecutor messageExecutor) {
        super(messageExecutor, new NettyServerConfig());
    }


    @Override
    public void destroyChannel(String serverAddress, Channel channel) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("will destroy channel:{},address:{}", channel, serverAddress);
        }
        channel.disconnect();
        channel.close();
    }

    private void registerProcessor() {
        // 1. registry on request message processor
        super.registerProcessor(MessageType.TYPE_ECHO, new ServerEchoProcessor(), messageExecutor);

        // 5. registry heartbeat message processor
//        ServerHeartbeatProcessor heartbeatMessageProcessor = new ServerHeartbeatProcessor(this);
//        super.registerProcessor(MessageType.TYPE_HEARTBEAT_MSG, heartbeatMessageProcessor, null);
    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    protected String getTransactionServiceGroup() {
        return "default.service.group";
    }
}
