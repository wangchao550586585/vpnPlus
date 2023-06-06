package org.server.protocol.http;

import org.server.entity.CompositeByteBuf;
import org.server.entity.ChannelWrapped;
import org.server.protocol.AbstractHandler;
import org.server.protocol.http.entity.*;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * curl  http://127.0.0.1/index.jsp?name=%E5%BC%A0%E4%B8%89&value=10
 * POST请求
 * json提交：curl -H "Content-Type: application/json" -X POST -d '{"id": "001", "name":"张三", "phone":"13099999999"}'  http://127.0.0.1/index.jsp?name=%E5%BC%A0%E4%B8%89&value=10
 * application/x-www-form-urlencoded 提交：curl  -X POST -d 'name=%E5%BC%A0111&value=10&key=va'  http://127.0.0.1/index.jsp   传参需要urlencode，不然会乱码
 * multipart/form-data提交：
 * <p>
 * 请求行 request-line，开始于一个方法标识 method，
 * 紧接着一个空白 SP，
 * 然后是请求目标 request-target，
 * 另一个空白 SP，
 * 之后是协议版本 HTTP-version，
 * 最后是回车换行符 CRLF。
 * 服务器在等待接收和解析parse一个请求行 request-line 的时候，应当 忽略至少一个空行
 * 例子如下：GET /index.jsp?name=%E5%BC%A0%E4%B8%89&value=10 HTTP/1.1
 * <p>
 * status-line = HTTP-version SP status-code SP reason-phrase CRLF
 * 协议版本 HTTP-version，一个空白 SP，状态码 status-code，另一个空白 SP，一个可能为空的文本短语 reason-phrase 来描述该状态码，最后是回车换行符 CRLF。
 * reason-phrase 早期互联网交互，可省略。
 * 例子如下：HTTP/1.1 200 OK
 * <p>
 * 每一个头字段header field都由一个字段名field name
 * 及随后的一个分号（":"）、
 * 可选的前置空白、
 * 一个字段值field value、
 * 一个可选的结尾空白组成。
 * 例子如下：Connection: keep-alive
 * <p>
 * https://www.docin.com/p-1581691022.html
 * https://duoani.github.io/HTTP-RFCs.zh-cn/
 * https://github.com/abbshr/rfc7540-translation-zh_cn/tree/master
 * http://blog.zhaojie.me/2011/03/html-form-file-uploading-programming.html
 */
public class HttpHandler extends AbstractHandler {
    private final static String BASE_DIR = "./src/main/resources/images/";
    private final static String ACCESS_STATIC_RESOURCES = "/resource/";

    static {
        try {
            //当目录不存在则创建
            Path directoryPath = Paths.get(BASE_DIR);
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public HttpHandler(ChannelWrapped channelWrapped) {
        super(channelWrapped);
    }

    @Override
    protected void exec() throws Exception {
        Request request = getRequest();
        if (Objects.isNull(request)) {
            return;
        }
        LOGGER.info("IPV4 host:{} remoteAddress:{} {}", request.getRequestHeaders().getHost(), channelWrapped.channel().getRemoteAddress(), channelWrapped.uuid());
        //接收端接收到一个不合法的请求行request-line时，应当 响应一个 400 (Bad Request) 错误或者 301 (Move Permanently) 重定向，
        //服务器接收到超出其长度要求的请求方法request method时 应当 响应一个 501 (Not Implemented) 状态码。服务器接收到一个 URI 其长度超出服务器所期望的最大长度时，必须 响应一个 414 (URI Too Long) 状态码
        String method = request.getStartLine().getMethod();
        if (("GET").equals(method)) {
            //解析域名后面的字符串。
            doGet(request);
        } else if (method.equals("POST")) {
            doPost(request);
        } else if (method.equals("CONNECT")) {
        } else {
            otherMethod(request);
        }
        //清除读取的数据
        channelWrapped.cumulation().clear();
    }

    protected void otherMethod(Request request) {

    }

    public Request getRequest() {
        String uuid = channelWrapped.uuid();
        CompositeByteBuf cumulation = channelWrapped.cumulation();
        if (cumulation.remaining() <= 0) {
            LOGGER.info("{} request empty",uuid);
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

    protected void doPost(Request request) throws IOException {
        if (request.getRequestHeaders().getContentType().contains("application/json")) {
            //解析json
            String requestBody = request.getRequestBody();
            String payload = buildPayload(requestBody);
            byte[] data = payload.getBytes();
            Response.builder()//构建状态行
                    .httpVersion(request.getStartLine().getHttpVersion())
                    .httpStatus(HttpStatus.OK)
                    .date()  //构建响应头
                    .connection(request.getRequestHeaders().getConnection())
                    .contentLanguage("zh-CN")
                    .contentType("text/html;charset=utf-8")
                    .contentLength(data.length)
                    .payload(data)//构建响应体
                    .write(channelWrapped.channel(), channelWrapped.uuid());
        } else if (request.getRequestHeaders().getContentType().contains("multipart/form-data")) {
            StringBuilder sb = new StringBuilder();
            request.getMultiparts().stream().forEach(it -> {
                sb.append("name: ").append(it.name()).append("</br>");
                Optional.ofNullable(it.value()).ifPresent(val -> {
                    sb.append("value: ").append(val).append("</br>");
                });
                Optional.ofNullable(it.filename()).ifPresent(val -> {
                    sb.append("filename: ").append(val).append("</br>");
                });
                //如果是文本则直接输出到html里面
                Optional.ofNullable(it.contentType()).ifPresent(val -> {
                    if (val.equals("text/plain")) {
                        String text = it.compositeByteBuf().readAll();
                        sb.append("text: ").append(text).append("</br>");
                    } else if (val.equals("image/png")) {
                        try {
                            //创建一个稀疏文件：所谓稀疏文件就是我们创建文件时需要占用很大的空间而实际写入数据可能只占几个字节。
                            Long milliSecond = LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli();
                            String filename = milliSecond + it.filename();
                            FileChannel fileChannel = FileChannel.open(Paths.get(BASE_DIR + filename), StandardOpenOption.SPARSE, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                            it.compositeByteBuf().write(fileChannel);
                            String url = "http://127.0.0.1" + ACCESS_STATIC_RESOURCES + filename;
                            sb.append("<img src='" + url + "'>").append("</br>");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                sb.append("</br>");
            });
            String payload = buildPayload(sb.toString());
            byte[] data = payload.getBytes();
            Response.builder()//构建状态行
                    .httpVersion(request.getStartLine().getHttpVersion())
                    .httpStatus(HttpStatus.OK)
                    .date()  //构建响应头
                    .connection(request.getRequestHeaders().getConnection())
                    .contentLanguage("zh-CN")
                    .contentType("text/html;charset=utf-8")
                    .contentLength(data.length)
                    .payload(data)//构建响应体
                    .write(channelWrapped.channel(), channelWrapped.uuid());
        } else {
            //统一当application/x-www-form-urlencoded处理 格式为：key=value&key=value
            String requestBody = URLDecoder.decode(request.getRequestBody(), "UTF-8");
            String payload = buildPayload(parseParams(requestBody).toString());
            byte[] data = payload.getBytes();
            Response.builder()//构建状态行
                    .httpVersion(request.getStartLine().getHttpVersion())
                    .httpStatus(HttpStatus.OK)
                    .date()  //构建响应头
                    .connection(request.getRequestHeaders().getConnection())
                    .contentLanguage("zh-CN")
                    .contentType("text/html;charset=utf-8")
                    .contentLength(data.length)
                    .payload(data)//构建响应体
                    .write(channelWrapped.channel(), channelWrapped.uuid());
        }
    }

    protected void doGet(Request request) throws Exception {
        //说明访问的是一个静态资源
        String requestTarget = request.getStartLine().getRequestTarget();
        if (requestTarget.contains("/favicon.ico")) {
            FileChannel fileChannel = FileChannel.open(Paths.get(BASE_DIR + "favicon.png"), StandardOpenOption.READ);
            Response.builder()//构建状态行
                    .httpVersion(request.getStartLine().getHttpVersion())
                    .httpStatus(HttpStatus.OK)
                    .date()//构建响应头
                    .connection(request.getRequestHeaders().getConnection())
                    .contentLanguage("zh-CN")
                    .contentType("image/png")
                    .contentLength((int) fileChannel.size())
                    .stream(fileChannel)//构建响应体
                    .write(channelWrapped.channel(), channelWrapped.uuid());
        } else if (requestTarget.contains(ACCESS_STATIC_RESOURCES)) {
            String filename = requestTarget.replaceAll(ACCESS_STATIC_RESOURCES, "");
            FileChannel fileChannel = FileChannel.open(Paths.get(BASE_DIR + filename), StandardOpenOption.READ);
            Response.builder()//构建状态行
                    .httpVersion(request.getStartLine().getHttpVersion())
                    .httpStatus(HttpStatus.OK)
                    .date()//构建响应头
                    .connection(request.getRequestHeaders().getConnection())
                    .contentLanguage("zh-CN")
                    .contentType("image/png")
                    .contentLength((int) fileChannel.size())
                    .stream(fileChannel)//构建响应体
                    .write(channelWrapped.channel(), channelWrapped.uuid());
        } else {
            //获取响应体
            String payload = "helloworld";
            if (Objects.nonNull(request.getParams())) {
                payload = buildPayload(request.getParams().toString());
            }
            byte[] data = payload.getBytes();
            Response.builder()//构建状态行
                    .httpVersion(request.getStartLine().getHttpVersion())
                    .httpStatus(HttpStatus.OK)
                    .date()//构建响应头
                    .connection(request.getRequestHeaders().getConnection())
                    .contentLanguage("zh-CN")
                    .contentType("text/html;charset=utf-8")
                    .contentLength(data.length)
                    .payload(data)//构建响应体
                    .write(channelWrapped.channel(), channelWrapped.uuid());
        }
    }

    private String buildPayload(String s) {
        StringBuilder payload = new StringBuilder("");
        payload.append("<!DOCTYPE html>");
        payload.append("<html>");
        payload.append("<head>");
        payload.append("<body>");
        payload.append(s);
        payload.append("</body>");
        payload.append("</head>");
        payload.append("</html>");
        return payload.toString();
    }

}
