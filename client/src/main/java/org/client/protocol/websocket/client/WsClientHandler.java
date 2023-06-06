package org.client.protocol.websocket.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.client.entity.ChannelWrapped;
import org.client.protocol.http.entity.Request;
import org.client.protocol.http.entity.RequestHeaders;
import org.client.protocol.http.entity.StartLine;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Base64;
import java.util.Random;

public class WsClientHandler implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    private static final Random r = new Random();
    ChannelWrapped channelWrapped;
    WsClient wsClient;
    public WsClientHandler(ChannelWrapped channelWrapped,WsClient wsClient) {
        this.channelWrapped = channelWrapped;
        this.wsClient=wsClient;
    }

    @Override
    public void run() {
        try {
            String uuid = channelWrapped.uuid();
            Request request = Request.builder()//构建状态行
                    .startLine(StartLine.builder().method("GET")
                            .requestTarget("/echo")
                            .httpVersion("HTTP/1.1"))
                    .requestHeaders(RequestHeaders.builder()
                            .host("127.0.0.1:8000")
                            .upgrade("websocket")//值必须包含"websocket"。
                            .connection("Upgrade")//值必须包含"Upgrade"。
                            .secWebSocketKey(getKey())//由一个随机生成的16字节的随机数通过base64编码得到的。每一个连接都必须随机的选择随机数。
                            .secWebSocketVersion(13) //值必须为13。
                            .secWebSocketProtocol("chat")//这个值包含了一个或者多个客户端希望使用的用逗号分隔的根据权重排序的子协议。
                            .secWebSocketExtensions("permessage-deflate; client_max_window_bits")//这个值表示客户端期望使用的协议级别的扩展。
                    );
            request.write(channelWrapped.channel(), uuid);
            WsClientUpgradeHandler websocketClientUpgrade = new WsClientUpgradeHandler(channelWrapped, request,wsClient);
            SelectionKey key = channelWrapped.key();
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            channelWrapped.key().attach(websocketClientUpgrade);
            LOGGER.info("websocket upgrade success {}", uuid);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getKey() {
        byte[] by = new byte[16];
        for (int i = 0; i < by.length; i++) {
            int i1 = r.nextInt(256);
            by[i] = (byte) i1;
        }
        return Base64.getEncoder().encodeToString(by);
    }

}
