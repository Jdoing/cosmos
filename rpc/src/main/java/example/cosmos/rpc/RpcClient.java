package example.cosmos.rpc;

import example.cosmos.rpc.netty.NettyClientConfig;
import example.cosmos.rpc.netty.NettyPoolKey;
import example.cosmos.rpc.netty.NettyRemoteClient;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author jiangyu.666@bytedance.com
 * @date 2023/5/6
 */
public class RpcClient {
    private static final ThreadPoolExecutor workingThreads = new ThreadPoolExecutor(100, 500, 500, TimeUnit.SECONDS,
            new LinkedBlockingQueue(20000), new ThreadPoolExecutor.CallerRunsPolicy());

    public static void main(String[] args) throws TimeoutException {
        NettyRemoteClient client = new NettyRemoteClient(new NettyClientConfig(), null, workingThreads, NettyPoolKey.TransactionRole.RMROLE);
        client.init();

        EchoMessage echoMessage = new EchoMessage();
        echoMessage.setMsg("hi");
        client.sendSyncRequest("localhost:8091", echoMessage);
    }

}
