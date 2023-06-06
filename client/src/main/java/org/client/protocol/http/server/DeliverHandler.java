package org.client.protocol.http.server;

import org.client.entity.ChannelWrapped;
import org.client.entity.CompositeByteBuf;
import org.client.protocol.AbstractHandler;
import org.client.protocol.websocket.client.WsClient;

import java.io.IOException;

public class DeliverHandler extends AbstractHandler {
    WsClient wsClient;
    int seqId;

    public DeliverHandler(ChannelWrapped channelWrapped, WsClient wsClient, int seqId) {
        super(channelWrapped);
        this.wsClient = wsClient;
        this.seqId = seqId;
    }

    public void exec() throws IOException {
        CompositeByteBuf cumulation = channelWrapped.cumulation();
        String uuid = channelWrapped.uuid();
        //判断当前channel是否已经关闭了
        if (!channelWrapped.channel().isOpen()) {
            LOGGER.warn("channel 已经关闭 {}", uuid);
            return;
        }
        //获取服务端数据
        wsClient.write(cumulation,seqId,uuid);
        cumulation.clearAll();
        LOGGER.info("child -> remote  end {}", uuid);
    }
    @Override
    public void after() {
        wsClient.remove(seqId);
    }
}
