package org.server.protocol.websocket.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.server.entity.ChannelWrapped;
import org.server.protocol.AbstractHandler;
import org.server.protocol.http.client.HttpClient;
import org.server.protocol.http.entity.Request;
import org.server.protocol.websocket.entity.WebsocketFrame;
import org.server.util.Utils;

import javax.rmi.CORBA.Util;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.server.protocol.websocket.entity.WebsocketFrame.DEFAULT_MASK;

/**
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
 * |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
 * |N|V|V|V|       |S|             |   (if payload len==126/127)   |
 * | |1|2|3|       |K|             |                               |
 * +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
 * |     Extended payload length continued, if payload len == 127  |
 * + - - - - - - - - - - - - - - - +-------------------------------+
 * |                               |Masking-key, if MASK set to 1  |
 * +-------------------------------+-------------------------------+
 * | Masking-key (continued)       |          Payload Data         |
 * +-------------------------------- - - - - - - - - - - - - - - - +
 * :                     Payload Data continued ...                :
 * + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
 * |                     Payload Data continued ...                |
 * +---------------------------------------------------------------+
 */
public class WebsocketReceive extends AbstractHandler {
    Map<Integer, HttpClient> channelMap = new ConcurrentHashMap<>();
    private final Request request;
    private String host="127.0.0.1";
    private int port=8080;

    public WebsocketReceive(ChannelWrapped channelWrapped, Request request) {
        super(channelWrapped);
        this.request = request;
    }

    @Override
    protected void exec() throws Exception {
        try {
            //没有读到对应的终止符则返回接着继续读。
        /*客户端必须在它发送到服务器的所有帧中添加掩码（Mask）（具体细节见5.3节）。
        （注意：无论WebSocket协议是否使用了TLS，帧都需要添加掩码）。服务端收到没有添加掩码的数据帧以后，
        必须立即关闭连接。在这种情况下，服务端可以发送一个在7.4.1节定义的状态码为1002（协议错误）的关闭帧。
        服务端禁止在发送数据帧给客户端时添加掩码。
        客户端如果收到了一个添加了掩码的帧，必须立即关闭连接。
        在这种情况下，它可以使用第7.4.1节定义的1002（协议错误）状态码。（*/
            String uuid = channelWrapped.uuid();
            WebsocketFrame frame = WebsocketFrame.parse(channelWrapped);
            //协议错误，断开连接
            if (Objects.isNull(frame)) {
                LOGGER.warn("协议错误");
                //closeChildChannel();
                return;
            }
            LOGGER.info("Receive {} {} ", frame.toString(), uuid);
            String msg = "";
            byte[] sendPayloadData;
            byte[] sendPayloadLen = null;
            byte[] tempPayloadData = frame.payloadData();
            byte[] payloadLenExtended = null;
            switch (Utils.binary2Int(frame.opcode())) {
                case 0x00:
                    break;
                case 0x01:
                    //“负载字段”是用UTF-8编码的文本数据。
                    if (tempPayloadData.length > 0) {
                        msg = Utils.unmask(tempPayloadData, frame.maskingKey());
                        LOGGER.info("receive msg {} {} ", msg, uuid);
                    }
                    //响应数据，掩码
                    WebsocketFrame.serverSendUTF("接收成功", channelWrapped.channel(), uuid);
                    break;
                case 0x02:
                    //二进制帧
                    byte[] dataByte = Utils.unmaskBytes(tempPayloadData, frame.maskingKey());
                    int off = 0;
                    int len = Utils.byteToIntV2(dataByte[off++]);
                    byte[] data = Arrays.copyOfRange(dataByte, off, off + len);
                    off += len;
                    String cmd = new String(data);

                    len = Utils.byteToIntV2(dataByte[off++]);
                    data = Arrays.copyOfRange(dataByte, off, off + len);
                    off += len;
                    int seqId = Utils.bytes2Int(data);

                    data = Arrays.copyOfRange(dataByte, off, dataByte.length);
                    handlerRequest(cmd, seqId, data);
                    break;
                case 0x08:
                    //可省略关闭帧，也会自动关闭
                    //1:解码
                    if (tempPayloadData.length > 0) {
                        if (frame.mask() == 1) {
                            tempPayloadData = Utils.unmaskBytes(tempPayloadData, frame.maskingKey());
                        }
                        //2:前两个字节必须是一个无符号整型（按照网络字节序）来代表定义的状态码。
                        int statusCode = Utils.binary2Int(Utils.bytes2Binary(Arrays.copyOfRange(tempPayloadData, 0, 2)));
                        //3：跟在这两个整型字节之后的可以是UTF-8编码的的数据值（原因）
                        if (tempPayloadData.length > 8) {
                            byte[] bytes = Arrays.copyOfRange(tempPayloadData, 2, tempPayloadData.length);
                            msg = new String(bytes);
                        }
                        if (msg.isEmpty()) {
                            msg = getCloseCause(msg, statusCode);
                        }
                        //“负载字段”是用UTF-8编码的文本数据。
                        LOGGER.info("close websocket statusCode {} msg {} {}", statusCode, msg, uuid);
                    } else {
                        LOGGER.info("close websocket empty statusCode msg  {}", uuid);
                    }
                    //1000表示正常关闭
                    sendPayloadData = Utils.int2Byte(1000);
                    sendPayloadLen = Utils.bytes2Binary((byte) sendPayloadData.length);
                    //这里len只有7位
                    sendPayloadLen = Arrays.copyOfRange(sendPayloadLen, 1, sendPayloadLen.length);
                    //响应关闭
                    WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.CLOSE, DEFAULT_MASK, sendPayloadLen, null, null, sendPayloadData, channelWrapped.channel(), channelWrapped.uuid());
                    break;
                case 0x09:
                    /**
                     * 如果收到了一个心跳Ping帧，那么终端必须发送一个心跳Pong 帧作为回应，除非已经收到了一个关闭帧。终端应该尽快回复Pong帧。
                     */
                    LOGGER.info("pong {} ", uuid);
                    WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.PONG, DEFAULT_MASK, null, null, null, null, channelWrapped.channel(), channelWrapped.uuid());
                    break;
                default:
                    break;
            }
            //读取结束则清除本次读取数据
            channelWrapped.cumulation().clear();
        } catch (IOException e) {
            LOGGER.error("error ",e);
        }
    }

    private void handlerRequest(String cmd, int seqId, byte[] data) throws IOException {
        LOGGER.info("receive cmd {} ,seqId {}", cmd, seqId);
        if (cmd.equals("connect")) {
            JsonObject jsonObject = new Gson().fromJson(new String(data), JsonObject.class);
            String targetHost = jsonObject.getAsJsonPrimitive("host").getAsString();
            int targetPort = jsonObject.getAsJsonPrimitive("port").getAsInt();
            //连接http代理服务器。
            HttpClient httpClient = new HttpClient(host, port, seqId, channelWrapped.channel(), targetHost, targetPort);
            httpClient.connect();
            channelMap.put(seqId, httpClient);
            LOGGER.info("connect proxy http success,seqId {}", seqId);
        } else if (cmd.equals("write")) {
            HttpClient httpClient = channelMap.get(seqId);
            httpClient.write(ByteBuffer.wrap(data));
        }
    }

    /**
     * 举例几个关闭的原因
     *
     * @param msg
     * @param statusCode
     * @return
     */
    private static String getCloseCause(String msg, int statusCode) {
        switch (statusCode) {
            case 1000:
                msg = "正常关闭";
                break;
            case 1001:
                msg = "服务器停机了或者在浏览器中离开了这个页面";
                break;
            case 1002:
                msg = "终端由于协议错误中止了连接。";
                break;
            case 1003:
                msg = "终端由于收到了一个不支持的数据类型的数据（如终端只能怪理解文本数据，但是收到了一个二进制数据）从而关闭连接。";
                break;
            case 1007:
                msg = "终端因为收到了类型不连续的消息（如非 UTF-8 编码的文本消息）导致的连接关闭。";
                break;
        }
        return msg;
    }


}
