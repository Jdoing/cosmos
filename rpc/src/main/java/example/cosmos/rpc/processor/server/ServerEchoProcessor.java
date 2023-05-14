package example.cosmos.rpc.processor.server;

import example.cosmos.rpc.RemotingProcessor;
import example.cosmos.rpc.RpcMessage;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 2022/9/17 20:26
 */
public class ServerEchoProcessor implements RemotingProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerEchoProcessor.class);


    @Override
    public void process(ChannelHandlerContext ctx, RpcMessage rpcMessage) throws Exception {

        LOGGER.info("received echo message:{}", rpcMessage.getBody());
    }
}
