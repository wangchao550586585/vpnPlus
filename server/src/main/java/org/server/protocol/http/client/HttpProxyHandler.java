package org.server.protocol.http.client;

import org.server.entity.ChannelWrapped;
import org.server.protocol.AbstractHandler;
import org.server.protocol.http.entity.Request;
import org.server.protocol.websocket.entity.WebsocketFrame;
import org.server.protocol.websocket.server.WebsocketReceive;
import org.server.util.Utils;
import java.io.IOException;

public class HttpProxyHandler extends AbstractHandler {
    final Request request;
    final Integer seqId;
    final WebsocketReceive websocketReceive;
    final HttpClient httpClient;

    public HttpProxyHandler(ChannelWrapped channelWrapped, Request request, Integer seqId, WebsocketReceive websocketReceive, HttpClient httpClient) {
        super(channelWrapped);
        this.request = request;
        this.seqId = seqId;
        this.websocketReceive = websocketReceive;
        this.httpClient = httpClient;
    }

    @Override
    protected void exec() throws Exception {
        String uuid = channelWrapped.uuid();
        //将数据封装输入websocket发送。
        //序列化值
        byte[] cmdByte = "write".getBytes();
        //占用2字节
        byte[] seqIdByte = Utils.int2Byte(seqId);
        byte[] bytes = channelWrapped.cumulation().readAllByte();
        if (bytes.length==0){
            LOGGER.info("info is 0");
            return;
        }
        WebsocketFrame.write(cmdByte, seqIdByte, bytes, uuid, websocketReceive);
    }

    @Override
    public void after() {
        LOGGER.debug("1.主动关闭selector server seqId {}",seqId);
        httpClient.closeSelector();
        //从map中删除httpclient
        LOGGER.debug("2.主动删除httpClient server seqId {}",seqId);
        httpClient.remove(seqId);
        byte[] cmdByte = "close".getBytes();
        //占用2字节
        byte[] seqIdByte = Utils.int2Byte(seqId);
        try {
            LOGGER.debug("3.主动通知客户端关闭channel server seqId {}",seqId);
            WebsocketFrame.write(cmdByte, seqIdByte, new byte[0], seqId + "", websocketReceive);
        } catch (IOException e) {
            LOGGER.error("3.主动通知客户端关闭channel失败 server seqId "+seqId,e);
            throw new RuntimeException(e);
        }
    }
}