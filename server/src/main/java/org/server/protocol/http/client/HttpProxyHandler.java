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
        //Request request = getRequest();
        //LOGGER.info(request.toString());
        String uuid = channelWrapped.uuid();
        //将数据封装输入websocket发送。
        //序列化值
        byte[] cmdByte = "write".getBytes();
        //占用2字节
        byte[] seqIdByte = Utils.int2Byte(seqId);
        channelWrapped.cumulation().print(uuid);
        byte[] bytes = channelWrapped.cumulation().readAllByte();
        String s = new String(bytes);
        LOGGER.info("child -> remote  end {}", s);
        LOGGER.info("child -> remote  end {}", bytes);
        WebsocketFrame.write(cmdByte, seqIdByte, bytes, uuid, wsChannel);
        LOGGER.info("child -> remote  end {}", uuid);
    }
    public Request getRequest() {
        String uuid = channelWrapped.uuid();
        CompositeByteBuf cumulation = channelWrapped.cumulation();
        if (cumulation.remaining() <= 0) {
            LOGGER.info("{} request empty",uuid);
            return null;
        }
        cumulation.mark();
        cumulation.print(uuid);
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

        return request;
    }
}