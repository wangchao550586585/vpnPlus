package org.client.protocol.websocket.client;

import org.client.entity.ChannelWrapped;
import org.client.protocol.AbstractHandler;
import org.client.protocol.http.entity.Request;
import org.client.protocol.websocket.entity.WebsocketFrame;
import org.client.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Objects;

/**
 * 这里可以进行websocket通信了
 */
public class WsReceive extends AbstractHandler {
    Request request;
    Heartbeat heartbeat;
    WsClient wsClient;

    public WsReceive(ChannelWrapped channelWrapped, Request request, Heartbeat heartbeat, WsClient wsClient) {
        super(channelWrapped);
        this.request = request;
        this.heartbeat = heartbeat;
        this.wsClient = wsClient;
    }

    @Override
    protected void exec() throws Exception {
        try {
            String uuid = channelWrapped.uuid();
            WebsocketFrame frame = WebsocketFrame.parse(channelWrapped);
            //协议错误，断开连接
            if (Objects.isNull(frame)) {
                //closeChildChannel();
                return;
            }
            //LOGGER.info("Receive {} {} ", frame.toString(), uuid);
            String msg = "";
            byte[] dataByte = frame.payloadData();
            switch (Utils.binary2Int(frame.opcode())) {
                case 0x00:
                    break;
                case 0x01:
                    break;
                case 0x02:
                    dataByte = Utils.binary2Bytes(frame.payloadData());
                    //二进制帧
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
                case 0x0A:
                    /**
                     * 作为回应发送的Pong帧必须完整携带Ping帧中传递过来的“应用数据”字段。
                     * 如果终端收到一个Ping帧但是没有发送Pong帧来回应之前的ping帧，那么终端可能选择用Pong帧来回复最近处理的那个Ping帧。
                     * Pong帧可以被主动发送。这会作为一个单向的心跳。预期外的Pong包的响应没有规定。
                     */
                    LOGGER.info("receive pong");
                    //更新时间
                    heartbeat.num(0);
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
        if (cmd.equals("write")) {
   /*         if (data[0]==70){
                LOGGER.info("websocket receive \r\n {}", new String(data));
            }*/
            //1.获取对应channel
            SocketChannel channel = wsClient.channelMap.get(seqId);
            if (null != channel) {
                try {
                    channel.write(ByteBuffer.wrap(data));
                } catch (IOException e) {
                    // TODO: 2023/6/6
                    wsClient.remove(seqId);
                   LOGGER.error("该channel关闭 seqId "+seqId,e);
                }
            }
        }
    }

    @Override
    public void after() {
    }
}
