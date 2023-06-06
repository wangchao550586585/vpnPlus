package org.server.protocol.http.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.server.protocol.http.HttpStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public final class Response {
    private static final DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss 'GMT'", Locale.US);
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    private String httpVersion;
    private HttpStatus httpStatus;
    private String date;
    private String connection;
    private String contentLanguage;
    private String contentType;
    private Integer contentLength;
    private byte[] payload;
    private FileChannel stream;

    /**
     * websocket支持
     */
    private String upgrade;
    private String secWebSocketExtensions;
    private String secWebSocketProtocol;
    private String secWebSocketAccept;

    static {
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static Response builder() {
        return new Response();
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        //build startLine
        sb.append(httpVersion)
                .append(" ")
                .append(httpStatus.getStatusCode())
                .append(" ")
                .append(httpStatus.getReasonPhrase())
                .append("\r\n");
        //build HeaderFields
        sb.append("Date").append(": ").append(date).append("\r\n");
        Optional.ofNullable(connection).ifPresent(it -> {
            sb.append("Connection").append(": ").append(it).append("\r\n");
        });
        sb.append("Content-Language").append(": ").append(contentLanguage).append("\r\n");
        Optional.ofNullable(contentType).ifPresent(it -> {
            sb.append("Content-Type").append(": ").append(it).append("\r\n");
        });
        Optional.ofNullable(contentLength).ifPresent(it -> {
            sb.append("Content-Length").append(": ").append(it).append("\r\n");
        });

        /**
         * websocket支持
         */
        Optional.ofNullable(upgrade).ifPresent(it -> {
            sb.append("Upgrade").append(": ").append(it).append("\r\n");
        });
        Optional.ofNullable(secWebSocketExtensions).ifPresent(it -> {
            sb.append("Sec-WebSocket-Extensions").append(": ").append(it).append("\r\n");
        });
        Optional.ofNullable(secWebSocketProtocol).ifPresent(it -> {
            sb.append("Sec-WebSocket-Protocol").append(": ").append(it).append("\r\n");
        });
        Optional.ofNullable(secWebSocketAccept).ifPresent(it -> {
            sb.append("Sec-WebSocket-Accept").append(": ").append(it).append("\r\n");
        });
        //build requestBody
        sb.append("\r\n");
        return sb.toString();
    }

    private Response self() {
        return this;
    }

    public Response httpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
        return self();
    }

    public Response httpStatus(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
        return self();
    }

    public Response date() {
        this.date = df.format(new Date());
        return self();
    }

    public Response connection(String connection) {
        this.connection = connection;
        return self();
    }

    public Response contentLanguage(String contentLanguage) {
        this.contentLanguage = contentLanguage;
        return self();
    }

    public Response contentType(String contentType) {
        this.contentType = contentType;
        return self();
    }

    public Response contentLength(Integer contentLength) {
        this.contentLength = contentLength;
        return self();
    }

    public Response payload(byte[] payload) {
        this.payload = payload;
        return self();
    }

    public Response stream(FileChannel channel) {
        this.stream = channel;
        return self();
    }

    public Response upgrade(String upgrade) {
        this.upgrade = upgrade;
        return self();
    }

    public Response secWebSocketExtensions(String secWebSocketExtensions) {
        this.secWebSocketExtensions = secWebSocketExtensions;
        return self();
    }

    public Response secWebSocketProtocol(String secWebSocketProtocol) {
        this.secWebSocketProtocol = secWebSocketProtocol;
        return self();
    }

    public Response secWebSocketAccept(String secWebSocketAccept) {
        this.secWebSocketAccept = secWebSocketAccept;
        return self();
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDate() {
        return date;
    }

    public String getConnection() {
        return connection;
    }

    public String getContentLanguage() {
        return contentLanguage;
    }

    public String getContentType() {
        return contentType;
    }

    public Integer getContentLength() {
        return contentLength;
    }

    public byte[] getPayload() {
        return payload;
    }

    public FileChannel getStream() {
        return stream;
    }

    public String getUpgrade() {
        return upgrade;
    }

    public String getSecWebSocketExtensions() {
        return secWebSocketExtensions;
    }

    public String getSecWebSocketProtocol() {
        return secWebSocketProtocol;
    }

    public String getSecWebSocketAccept() {
        return secWebSocketAccept;
    }

    public void write(SocketChannel channel, String uuid) throws IOException {
        String response = build();
        ByteBuffer byteBuffer = ByteBuffer.wrap(response.getBytes());
        channel.write(byteBuffer);
        //刷入响应体
        if (Objects.nonNull(payload)) {
            ByteBuffer data = ByteBuffer.wrap(payload);
            channel.write(data);
        }
        //如果是一个流则刷入流
        //某些情况下并不保证数据能够全部完成传输，确切传输了多少字节的数据需要根据返回的值来进行判断。
        //例如：从一个非阻塞模式下的 SocketChannel 中输入数据就不能够一次性将数据全部传输过来，
        //或者将文件通道的数据传输给一个非阻塞模式下的 SocketChannel 不能一次性传输过去。
        if (Objects.nonNull(stream)) {
            long transfered = 0;
            while (transfered < stream.size()) {
                transfered += stream.transferTo(transfered, stream.size(), channel);
            }
            stream.close();
        }
        LOGGER.info("response {} \r\n{} ",uuid, response);
    }

}
