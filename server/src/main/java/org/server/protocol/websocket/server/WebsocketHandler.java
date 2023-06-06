package org.server.protocol.websocket.server;

import org.server.entity.ChannelWrapped;
import org.server.protocol.http.HttpHandler;
import org.server.protocol.http.HttpStatus;
import org.server.protocol.http.entity.Request;
import org.server.protocol.http.entity.Response;
import org.server.util.Utils;

import java.io.IOException;
import java.util.Objects;

/**
 * 替代HTTP轮询的方法来满足Web页面和远端服务器的双向数据通信。
 * ws-URI = "ws:" "//" host [ ":" port ] path [ "?" query ]
 * wss-URI = "wss:" "//" host [ ":" port ] path [ "?" query ]
 *
 * https://github.com/HJava/myBlog/blob/master/WebSocket%20%E7%B3%BB%E5%88%97/WebSocket%E7%B3%BB%E5%88%97%E4%B9%8B%E4%BA%8C%E8%BF%9B%E5%88%B6%E6%95%B0%E6%8D%AE%E8%AE%BE%E8%AE%A1%E4%B8%8E%E4%BC%A0%E8%BE%93.md
 */
public class WebsocketHandler extends HttpHandler {

    public WebsocketHandler(ChannelWrapped channelWrapped) {
        super(channelWrapped);
    }

    @Override
    protected void doGet(Request request) throws Exception {
        String uuid = channelWrapped.uuid();
        String httpVersion = request.getStartLine().getHttpVersion();
        float v = Float.parseFloat(httpVersion.split("/")[1]);
        if (v < 1.1) {
            //不支持
        }
        String host = request.getRequestHeaders().getHost();
        if (Objects.isNull(host)) {
            //不支持
        }
        //表示建立连接的脚本属于哪一个源
        String origin = request.getRequestHeaders().getOrigin();
        if (Objects.isNull(origin)) {
            //缺少origin字段，WebSocket服务器需要回复HTTP 403 状态码（禁止访问）
        }
        String upgrade = request.getRequestHeaders().getUpgrade();
        if (Objects.equals("websocket", upgrade)) {
            //不支持
        }
        String connection = request.getRequestHeaders().getConnection();
        if (Objects.equals("Upgrade", connection)) {
            //不支持
        }
        String secWebSocketKey = request.getRequestHeaders().getSecWebSocketKey();
        if (Objects.isNull(secWebSocketKey)) {
            //不支持
        }

        Integer secWebSocketVersion = request.getRequestHeaders().getSecWebSocketVersion();
        if (secWebSocketVersion != 13) {
            //不支持
        }

        String secWebSocketExtensions = request.getRequestHeaders().getSecWebSocketExtensions();
        String secWebSocketProtocol = request.getRequestHeaders().getSecWebSocketProtocol();
        Response.builder()//构建状态行
                .httpVersion(request.getStartLine().getHttpVersion())
                //状态码101表示同意升级
                .httpStatus(HttpStatus.UPGRADE)
                .date()//构建响应头
                //不包含"Upgrade"的值（该值不区分大小写），那么客户端必须关闭连接。
                .connection(connection)
                //不是"websocket，那么客户端必须关闭连接。
                .upgrade(upgrade)
                //表示客户端期望使用的协议级别的扩展
                //.secWebSocketExtensions(secWebSocketExtensions)
                //包含了一个或者多个客户端希望使用的用逗号分隔的根据权重排序的子协议。
                .secWebSocketProtocol(secWebSocketProtocol)
                //这里需要解码，解码规则为sha+base64
                .secWebSocketAccept(Utils.getKey(secWebSocketKey))
                .contentLanguage("zh-CN")
                .write(channelWrapped.channel(), channelWrapped.uuid());
        WebsocketReceive deliverHandler = new WebsocketReceive(channelWrapped,request);
        channelWrapped.key().attach(deliverHandler);

        LOGGER.info("websocket upgrade success {}",uuid);
    }

    /**
     * 不支持
     *
     * @param request
     * @throws IOException
     */
    @Override
    protected void doPost(Request request) throws IOException {
        //dont accept
        super.doPost(request);
    }

    /**
     * 不支持
     *
     * @param request
     */
    @Override
    protected void otherMethod(Request request) {
        //dont accept
        super.otherMethod(request);
    }
}
