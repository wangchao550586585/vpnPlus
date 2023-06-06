package org.server.protocol.http.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.server.entity.ChannelWrapped;
import org.server.protocol.http.entity.Request;
import org.server.protocol.http.entity.RequestHeaders;
import org.server.protocol.http.entity.StartLine;
import org.server.protocol.websocket.server.WebsocketReceive;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Base64;
import java.util.Random;

public class HttpClientHandler implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    private static final Random r = new Random();
    ChannelWrapped channelWrapped;
    final Integer seqId;
    final SocketChannel wsChannel;
    final String host;
    final Integer port;
    public HttpClientHandler(ChannelWrapped channelWrapped, Integer seqId, String host, Integer port, SocketChannel wsChannel) {
        this.channelWrapped = channelWrapped;
        this.seqId = seqId;
        this.wsChannel = wsChannel;
        this.host=host;
        this.port=port;
    }

    @Override
    public void run() {
        try {
            String uuid = channelWrapped.uuid();
            String hosts = host + ":" + port;
            Request request = Request.builder()//构建状态行
                    .startLine(StartLine.builder().method("CONNECT")
                            .requestTarget(hosts)
                            .httpVersion("HTTP/1.1"))
                    .requestHeaders(RequestHeaders.builder()
                            .host(hosts)
                            .proxyConnection("keep-alive")//值必须包含"websocket"。
                    );
            request.write(channelWrapped.channel(), uuid);
            HttpProxyHandler websocketClientUpgrade = new HttpProxyHandler(channelWrapped, request,seqId,wsChannel);
            SelectionKey key = channelWrapped.key();
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            channelWrapped.key().attach(websocketClientUpgrade);
            LOGGER.info("connect http proxy success {}", uuid);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}