package example.cosmos.rpc;

import example.cosmos.common.NetUtil;
import example.cosmos.common.XID;
import example.cosmos.rpc.netty.NettyRemotingServer;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 2022/9/17 20:17
 */
public class RpcServer {
    private static final ThreadPoolExecutor workingThreads = new ThreadPoolExecutor(100, 500, 500, TimeUnit.SECONDS,
            new LinkedBlockingQueue(20000), new ThreadPoolExecutor.CallerRunsPolicy());

    public static void main(String[] args) {
        NettyRemotingServer nettyRemotingServer = new NettyRemotingServer(workingThreads);
        XID.setIpAddress(NetUtil.getLocalIp());

        nettyRemotingServer.init();
    }
}
