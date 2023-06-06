package org.server.protocol.http.client;

import com.google.gson.Gson;
import org.server.entity.ChannelWrapped;
import org.server.entity.CompositeByteBuf;
import org.server.protocol.AbstractHandler;
import org.server.protocol.http.entity.Multipart;
import org.server.protocol.http.entity.Request;
import org.server.protocol.http.entity.RequestHeaders;
import org.server.protocol.http.entity.StartLine;
import org.server.protocol.websocket.entity.WebsocketFrame;
import org.server.protocol.websocket.server.WebsocketReceive;
import org.server.util.Utils;

import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Objects;

public class HttpProxyHandler extends AbstractHandler {
    final Request request;
    final Integer seqId;
    final SocketChannel wsChannel;

    public HttpProxyHandler(ChannelWrapped channelWrapped, Request request, Integer seqId, SocketChannel wsChannel) {
        super(channelWrapped);
        this.request = request;
        this.seqId = seqId;
        this.wsChannel = wsChannel;
    }

    @Override
    protected void exec() throws Exception {
        String uuid = channelWrapped.uuid();
        //将数据封装输入websocket发送。
        //序列化值
        byte[] cmdByte = "write".getBytes();
        //占用2字节
        byte[] seqIdByte = Utils.int2Byte(seqId);
        //channelWrapped.cumulation().print(uuid);
        byte[] bytes = channelWrapped.cumulation().readAllByte();
  /*      if (bytes[0]==70){
            LOGGER.info("websocket receive \r\n {}", new String(bytes));
        }*/
        WebsocketFrame.write(cmdByte, seqIdByte, bytes, uuid, wsChannel);
    }

}