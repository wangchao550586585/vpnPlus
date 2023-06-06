package org.client.protocol.websocket.client;

import org.client.entity.ChannelWrapped;
import org.client.entity.CompositeByteBuf;
import org.client.protocol.AbstractHandler;
import org.client.protocol.http.entity.HttpStatus;
import org.client.protocol.http.entity.*;
import org.client.util.Utils;

import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WsClientUpgradeHandler extends AbstractHandler {
    Request request;
    WsClient wsClient;

    public WsClientUpgradeHandler(ChannelWrapped channelWrapped, Request request, WsClient wsClient) {
        super(channelWrapped);
        this.request = request;
        this.wsClient = wsClient;
    }

    @Override
    protected void exec() throws Exception {
        Response response = getResponse();
        String uuid = channelWrapped.uuid();
        if (Objects.isNull(request)) {
            return;
        }
        if (!response.getHttpStatus().equals(HttpStatus.UPGRADE)) {
            // 关闭客户端
            closeChildChannel();
            return;
        }
        if (!"websocket".equals(response.getUpgrade())) {
            //关闭客户端
            closeChildChannel();
            return;
        }
        if (!"Upgrade".equals(response.getConnection())) {
            //  关闭客户端
            closeChildChannel();
            return;
        }
        RequestHeaders requestHeaders = request.getRequestHeaders();
        if (!Utils.getKey(requestHeaders.getSecWebSocketKey()).equals(response.getSecWebSocketAccept())) {
            //关闭客户端
            closeChildChannel();
            return;
        }
        if (Objects.nonNull(response.getSecWebSocketExtensions())) {
            if (!requestHeaders.getSecWebSocketExtensions().contains(response.getSecWebSocketExtensions())) {
                //关闭客户端
                closeChildChannel();
                return;
            }
        }
        if (!response.getSecWebSocketProtocol().contains(requestHeaders.getSecWebSocketProtocol())) {
            //关闭客户端
            closeChildChannel();
            return;
        }
        Heartbeat heartbeat = new Heartbeat(channelWrapped);
        new ScheduledThreadPoolExecutor(1, r -> new Thread(r, "ping" + r.hashCode()))
                .scheduleAtFixedRate(heartbeat, 1, 5, TimeUnit.SECONDS);
        //协议升级
        WsReceive websocketClientUpgrade = new WsReceive(channelWrapped, request, heartbeat, wsClient);
        channelWrapped.key().attach(websocketClientUpgrade);
        //清除读取的数据
        channelWrapped.cumulation().clear();
        LOGGER.info("websocket upgrade success {}", uuid);
    }

    private Response getResponse() {
        String uuid = channelWrapped.uuid();
        CompositeByteBuf cumulation = channelWrapped.cumulation();
        if (cumulation.remaining() <= 0) {
            LOGGER.info("{} request empty", uuid);
            return null;
        }
        cumulation.mark();
        cumulation.print(uuid);
        //1:读取status line
        String readLine = cumulation.readLine();
        LOGGER.debug("statusLine {} {} ", readLine, uuid);

        //2:读取请求头
        //通常会在一个 POST 请求中带有一个 Content-Length 头字段
        Response response = null;
        try {
            response = Utils.parse(cumulation, Response.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        LOGGER.debug("headerFields {} {}", response, uuid);
        if (Objects.nonNull(response)) {
            //设置status line
            String[] readLineArr = readLine.split(" ");
            response.httpVersion(readLineArr[0]).httpStatus(HttpStatus.match(Integer.parseInt(readLineArr[1])));
        } else {
            cumulation.reset();
        }
        return response;
    }
}
