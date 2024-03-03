package example.cosmos.rpc;

import example.cosmos.rpc.protocol.AbstractMessage;

public class EchoMessage  extends AbstractMessage {

    private String msg;


    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Override
    public short getTypeCode() {
        return MessageType.TYPE_ECHO;
    }
}
