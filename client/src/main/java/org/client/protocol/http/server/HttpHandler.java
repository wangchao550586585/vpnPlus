package org.client.protocol.http.server;

import org.client.entity.CompositeByteBuf;
import org.client.entity.ChannelWrapped;
import org.client.protocol.AbstractHandler;
import org.client.protocol.http.entity.HttpStatus;
import org.client.protocol.http.entity.*;
import org.client.protocol.websocket.client.WsClient;

import java.io.IOException;
import java.util.*;

public class HttpHandler extends AbstractHandler {
    private WsClient wsClient;

    public HttpHandler(ChannelWrapped channelWrapped, WsClient wsClient) {
        super(channelWrapped);
        this.wsClient = wsClient;

    }

    @Override
    protected void exec() throws Exception {
        Request request = getRequest();
        if (Objects.isNull(request)) {
            return;
        }

        //接收端接收到一个不合法的请求行request-line时，应当 响应一个 400 (Bad Request) 错误或者 301 (Move Permanently) 重定向，
        //服务器接收到超出其长度要求的请求方法request method时 应当 响应一个 501 (Not Implemented) 状态码。服务器接收到一个 URI 其长度超出服务器所期望的最大长度时，必须 响应一个 414 (URI Too Long) 状态码
        String method = request.getStartLine().getMethod();
        if (method.equals("CONNECT")) {
            doConnect(request);
        } else {
        }
        //清除读取的数据
        channelWrapped.cumulation().clear();
    }

    private void doConnect(Request request) throws IOException {
        String uuid = channelWrapped.uuid();
        String[] split = request.getStartLine().getRequestTarget().split(":");
        int seqId = wsClient.seqId();
        LOGGER.info("IPV4 host:{} remoteAddress:{} {} seqId {}", request.getRequestHeaders().getHost(), channelWrapped.channel().getRemoteAddress(), channelWrapped.uuid(),seqId);
        wsClient.connect(split[0], split[1], uuid, seqId,this);
        //更换附件
        DeliverHandler deliverHandler = new DeliverHandler(channelWrapped, wsClient, seqId);
        channelWrapped.key().attach(deliverHandler);
    }

    public Request getRequest() {
        String uuid = channelWrapped.uuid();
        CompositeByteBuf cumulation = channelWrapped.cumulation();
        if (cumulation.remaining() <= 0) {
            LOGGER.info("{} request empty", uuid);
            return null;
        }
        cumulation.mark();
        //cumulation.print(uuid);
        //1:读取start line
        StartLine startLine = StartLine.parse(cumulation.readLine());
        LOGGER.debug("startLine {} {} ", startLine, uuid);

        //2:读取请求头
        //通常会在一个 POST 请求中带有一个 Content-Length 头字段
        RequestHeaders requestLine = RequestHeaders.parse(cumulation);
        LOGGER.debug("headerFields {} {}", requestLine, uuid);

        Request request = new Request(startLine, requestLine);
        //3:获取请求体
        //在一个请求中是否会出现消息体，以消息头中是否带有 Content-Length 或者 Transfer-Encoding 头字段作为信号。
        // 请求消息的分帧是独立于请求方法request method的语义之外的，即使请求方法并没有任何用于一个消息体的相关定义。
        //如果发送端所生成的消息包含有一个有效载荷，那么发送端 应当 在该消息里生成一个 Content-Type 头字段
        Integer contentLength = requestLine.getContentLength();
        String contentType = requestLine.getContentType();
        if (Objects.isNull(contentLength) || Objects.isNull(contentType)) {
        } else {
            //3:读取请求体
            //校验请求体长度是否满足
            int remaining = cumulation.remaining();
            if (remaining < contentLength) {
                cumulation.reset();
                LOGGER.info("数据长度不够 {} ", uuid);
                return null;
            }
            //说明表单提交，可能存在附件
            if (requestLine.getContentType().contains("multipart/form-data")) {
                List<Multipart> multiparts = Multipart.parse(cumulation, requestLine.getContentType());
                request.multiparts(multiparts);
                LOGGER.debug("multiparts {} {}", multiparts, uuid);
            } else {
                String payload = cumulation.read(contentLength);
                request.requestBody(payload);
                LOGGER.debug("payloads {} {}", payload, uuid);
            }
        }

        //4:获取路径上的参数
        //GET /index.jsp?name=%E5%BC%A0%E4%B8%89&value=10 HTTP/1.1
        getParams(uuid, startLine.getRequestTarget(), request);
        return request;
    }

    private void getParams(String uuid, String requestTarget, Request request) {
        int pre = requestTarget.indexOf("?");
        if (pre > 0) {
            String params = requestTarget.substring(pre + 1, requestTarget.length());
            Map<String, String> paramsMap = parseParams(params);
            request.params(paramsMap);
            LOGGER.debug("params {} {}", paramsMap, uuid);
        }
    }



    /**
     * 解析key2=value1&key2=value2成map
     *
     * @param params
     * @return
     */
    private Map<String, String> parseParams(String params) {
        Map<String, String> paramsMap = new HashMap<>();
        String[] split = params.split("&");
        for (int i = 0; i < split.length; i++) {
            String[] keyVal = split[i].split("=");
            paramsMap.put(keyVal[0], keyVal[1]);
        }
        return paramsMap;
    }


}
