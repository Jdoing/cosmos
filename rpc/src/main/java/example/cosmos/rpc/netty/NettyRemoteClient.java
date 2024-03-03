package example.cosmos.rpc.netty;

import example.cosmos.rpc.MessageType;
import example.cosmos.rpc.processor.server.ServerEchoProcessor;
import example.cosmos.rpc.protocol.AbstractMessage;
import io.netty.channel.Channel;
import io.netty.util.concurrent.EventExecutorGroup;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;

/**
 * 2022/9/18 09:37
 */
public class NettyRemoteClient extends AbstractNettyRemotingClient {



    public NettyRemoteClient(NettyClientConfig nettyClientConfig, EventExecutorGroup eventExecutorGroup, ThreadPoolExecutor messageExecutor, NettyPoolKey.TransactionRole transactionRole) {
        super(nettyClientConfig, eventExecutorGroup, messageExecutor, transactionRole);

        super.registerProcessor(MessageType.TYPE_ECHO, new ServerEchoProcessor(), null);
    }

    @Override
    protected Function<String, NettyPoolKey> getPoolKeyFunction() {
//        return serverAddress -> {
//            RegisterRMRequest message = new RegisterRMRequest(applicationId, transactionServiceGroup);
//            message.setResourceIds(resourceIds);
//            return new NettyPoolKey(NettyPoolKey.TransactionRole.RMROLE, serverAddress, message);
//        };

        return null;
    }


    @Override
    public void onRegisterMsgSuccess(String serverAddress, Channel channel, Object response, AbstractMessage requestMessage) {

    }

    @Override
    public void onRegisterMsgFail(String serverAddress, Channel channel, Object response, AbstractMessage requestMessage) {

    }

    @Override
    protected String getTransactionServiceGroup() {
        return "default.service.group";
    }
}
