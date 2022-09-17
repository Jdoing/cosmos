package example.cosmos.rpc.processor.server;

import example.cosmos.rpc.RemotingProcessor;
import example.cosmos.rpc.RpcMessage;
import io.netty.channel.ChannelHandlerContext;

/**
 * 2022/9/17 20:26
 */
public class ServerEchoProcessor implements RemotingProcessor {

    @Override
    public void process(ChannelHandlerContext ctx, RpcMessage rpcMessage) throws Exception {

    }
}
