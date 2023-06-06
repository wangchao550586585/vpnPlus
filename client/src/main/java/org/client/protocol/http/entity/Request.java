package org.client.protocol.http.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Request {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    private StartLine startLine;
    private RequestHeaders requestHeaders;
    private String requestBody;
    private Map<String, String> params;
    private List<Multipart> multiparts;

    public Request(StartLine startLine, RequestHeaders requestHeaders) {
        this.startLine = startLine;
        this.requestHeaders = requestHeaders;
    }

    public Request() {
    }

    public static Request builder() {
        return new Request();
    }

    public StartLine getStartLine() {
        return startLine;
    }

    public Request startLine(StartLine startLine) {
        this.startLine = startLine;
        return self();
    }

    private Request self() {
        return this;
    }

    public RequestHeaders getRequestHeaders() {
        return requestHeaders;
    }

    public Request requestHeaders(RequestHeaders requestHeaders) {
        this.requestHeaders = requestHeaders;
        return self();
    }

    public String getRequestBody() {
        return requestBody;
    }

    public Request requestBody(String requestBody) {
        this.requestBody = requestBody;
        return self();
    }

    public Map<String, String> getParams() {
        return params;
    }

    public Request params(Map<String, String> params) {
        this.params = params;
        return self();
    }

    public List<Multipart> getMultiparts() {
        return multiparts;
    }

    public Request multiparts(List<Multipart> multiparts) {
        this.multiparts = multiparts;
        return self();
    }

    public void write(SocketChannel channel, String uuid) throws IOException {
        String request = build();
        ByteBuffer byteBuffer = ByteBuffer.wrap(request.getBytes());
        channel.write(byteBuffer);
        LOGGER.info("request {} \r\n{} ",uuid, request);
    }
    /**
     * GET ws://127.0.0.1:8070/echo?name=%E5%93%88%E5%93%88&value=1111 HTTP/1.1
     * Host: 127.0.0.1:8070
     * Upgrade: websocket
     * Connection: Upgrade
     * Sec-WebSocket-Key: Vv8eO2tpqWIjn9gLEk/qzg==
     * Sec-WebSocket-Version: 13
     * Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits
     * Pragma: no-cache
     * Cache-Control: no-cache
     * User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36
     * Origin: http://localhost:63342
     * Accept-Encoding: gzip, deflate, br
     * Accept-Language: zh-CN,zh;q=0.9
     *
     * @throws Exception
     */
    private String build() {
        StringBuilder sb = new StringBuilder();
        //build startLine
        sb.append(startLine.getMethod())
                .append(" ")
                .append(startLine.getRequestTarget())
                .append(" ")
                .append(startLine.getHttpVersion())
                .append("\r\n");
        //build HeaderFields
        Optional.ofNullable(requestHeaders.getHost()).ifPresent(it -> {
            sb.append("Host").append(": ").append(it).append("\r\n");
        });
        /**
         * websocket支持
         */
        Optional.ofNullable(requestHeaders.getUpgrade()).ifPresent(it -> {
            sb.append("Upgrade").append(": ").append(it).append("\r\n");
        });
        Optional.ofNullable(requestHeaders.getConnection()).ifPresent(it -> {
            sb.append("Connection").append(": ").append(it).append("\r\n");
        });
        Optional.ofNullable(requestHeaders.getSecWebSocketKey()).ifPresent(it -> {
            sb.append("Sec-WebSocket-Key").append(": ").append(it).append("\r\n");
        });
        Optional.ofNullable(requestHeaders.getSecWebSocketVersion()).ifPresent(it -> {
            sb.append("Sec-WebSocket-Version").append(": ").append(it).append("\r\n");
        });
        Optional.ofNullable(requestHeaders.getSecWebSocketProtocol()).ifPresent(it -> {
            sb.append("Sec-WebSocket-Protocol").append(": ").append(it).append("\r\n");
        });
        Optional.ofNullable(requestHeaders.getSecWebSocketExtensions()).ifPresent(it -> {
            sb.append("Sec-WebSocket-Extensions").append(": ").append(it).append("\r\n");
        });
        //build requestBody
        sb.append("\r\n");
        return sb.toString();
    }
}
