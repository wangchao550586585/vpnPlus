package org.server.protocol.websocket.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.server.entity.ChannelWrapped;
import org.server.protocol.AbstractHandler;
import org.server.protocol.http.client.HttpClient;
import org.server.protocol.http.entity.Request;
import org.server.protocol.websocket.entity.WebsocketFrame;
import org.server.util.Utils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
    private String host = "127.0.0.1";
    private int port = 8060;

    public WebsocketReceive(ChannelWrapped channelWrapped, Request request) {
        super(channelWrapped);
        this.request = request;
        new Thread(this::doWrite).start();
    }

    @Override
    protected void exec() throws Exception {
        //没有读到对应的终止符则返回接着继续读。
        /*客户端必须在它发送到服务器的所有帧中添加掩码（Mask）（具体细节见5.3节）。
        （注意：无论WebSocket协议是否使用了TLS，帧都需要添加掩码）。服务端收到没有添加掩码的数据帧以后，
        必须立即关闭连接。在这种情况下，服务端可以发送一个在7.4.1节定义的状态码为1002（协议错误）的关闭帧。
        服务端禁止在发送数据帧给客户端时添加掩码。
        客户端如果收到了一个添加了掩码的帧，必须立即关闭连接。
        在这种情况下，它可以使用第7.4.1节定义的1002（协议错误）状态码。（*/
        WebsocketFrame frame;
        try {
            while (null != (frame = WebsocketFrame.parse(channelWrapped))) {
                byte[] tempPayloadData = frame.payloadData();
                switch (Utils.binary2Int(frame.opcode())) {
                    case 0x00:
                        break;
                    case 0x01:
                        break;
                    case 0x02:
                        //二进制帧
                        byte[] dataByte = Utils.mask(tempPayloadData, frame.maskingKey());
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
                        break;
                    case 0x09:
                        /**
                         * 如果收到了一个心跳Ping帧，那么终端必须发送一个心跳Pong 帧作为回应，除非已经收到了一个关闭帧。终端应该尽快回复Pong帧。
                         */
                        LOGGER.info("receive ping channelSize {} channel seqids {}", channelMap.size(), channelMap.keySet());
                        WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.PONG, DEFAULT_MASK, null, null, null, null, this, channelWrapped.uuid());
                        break;
                    default:
                        break;
                }
                //读取结束则清除本次读取数据
                channelWrapped.cumulation().clear();
            }
        } catch (IOException e) {
            LOGGER.error("error ", e);
        }
    }

    private void handlerRequest(String cmd, int seqId, byte[] data) throws IOException {
        LOGGER.info("receive cmd {} ,seqId {}", cmd, seqId);
        if (cmd.equals("connect")) {
            JsonObject jsonObject = new Gson().fromJson(new String(data), JsonObject.class);
            String targetHost = jsonObject.getAsJsonPrimitive("host").getAsString();
            int targetPort = jsonObject.getAsJsonPrimitive("port").getAsInt();
            //连接http代理服务器。
            HttpClient httpClient = new HttpClient(host, port, seqId, channelWrapped.channel(), targetHost, targetPort, this);
            httpClient.connect();
            channelMap.put(seqId, httpClient);
            LOGGER.debug("connect proxy http success,seqId {}", seqId);
        } else if (cmd.equals("write")) {
            HttpClient httpClient = channelMap.get(seqId);
            if (Objects.nonNull(httpClient)) {
                httpClient.write(ByteBuffer.wrap(data));
            }
        } else if (cmd.equals("close")) {
            LOGGER.debug("3.收到客户端删除channel client seqId {}", seqId);
            Optional.ofNullable(channelMap.remove(seqId)).ifPresent(it -> {
                //关闭channel和selector
                it.close();
                byte[] cmdByte = "closeAck".getBytes();
                //占用2字节
                byte[] seqIdByte = Utils.int2Byte(seqId);
                LOGGER.debug("6.收到客户端ACK client seqId {}", seqId);
                try {
                    WebsocketFrame.write(cmdByte, seqIdByte, new byte[0], seqId + "", this);
                } catch (IOException e) {
                    LOGGER.debug("6.收到客户端ACK 失败 client seqId {}", seqId);
                    throw new RuntimeException(e);
                }
            });
        } else if (cmd.equals("writeAck")) {
            LOGGER.error("7.主动收到客户端,发送ACK失败 server seqId {}", seqId);
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


    public void remove(Integer seqId) {
        Optional.ofNullable(channelMap.remove(seqId)).ifPresent(it -> {
            LOGGER.info("通知客户端删除channel,剔除map success {} ", seqId);
        });

    }
}
