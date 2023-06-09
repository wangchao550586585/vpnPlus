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
import java.util.Optional;

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
        while (true){
            try {
                String uuid = channelWrapped.uuid();
                WebsocketFrame frame = WebsocketFrame.parse(channelWrapped);
                //协议错误，断开连接
                if (Objects.isNull(frame)) {
                    return;
                }
                byte[] dataByte = frame.payloadData();
                switch (Utils.binary2Int(frame.opcode())) {
                    case 0x00:
                        break;
                    case 0x01:
                        break;
                    case 0x02:
                        if (dataByte.length==0){
                            LOGGER.info("receive 0");
                            return;
                        }
                        //二进制帧
                        int off = 0;
                        int len = Utils.byteToIntV2(dataByte[off++]);
                        byte[] data = Arrays.copyOfRange(dataByte, off, off + len);
                        off += len;
                        String cmd = new String(data);

                        try {
                            len = Utils.byteToIntV2(dataByte[off++]);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
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
                        LOGGER.info("receive pong channelSize {} channel seqids {}",wsClient.channelMap.size(),wsClient.channelMap.keySet());
                        //更新时间
                        heartbeat.num(0);
                        break;
                    default:
                        break;
                }
                //读取结束则清除本次读取数据
                channelWrapped.cumulation().clear();
            } catch (IOException e) {
                LOGGER.error("error ", e);
            }
        }

    }

    private void handlerRequest(String cmd, int seqId, byte[] data) throws IOException {
        LOGGER.info("receive cmd {} ,seqId {}", cmd, seqId);
        if (cmd.equals("write")) {
            //1.获取对应channel
            AbstractHandler handler = wsClient.channelMap.get(seqId);
            if (null != handler) {
                SocketChannel channel = handler.socketChannel();
                try {
                    channel.write(ByteBuffer.wrap(data));
                } catch (IOException e) {
                    //通知远端删除
                    LOGGER.info("1.主动关闭客户端连接 write error  client seqId {}",seqId);
                    wsClient.remove(seqId);
                }
            }else{
                LOGGER.info("handlerRequest handler empty seqId {}",seqId);
            }

        } else if (cmd.equals("closeAck")) {
            LOGGER.info("7.主动关闭客户端ACK client seqId {}",seqId);
        } else if (cmd.equals("close")) {
            receiveClose(seqId);
        }
    }

    private void receiveClose(int seqId) {
        LOGGER.info("4.主动收到服务端关闭channel server seqId {}",seqId);
        //清除缓存数据
        Optional.ofNullable(wsClient.channelMap.remove(seqId)).ifPresent(it -> {
            //关闭客户端
            try {
                LOGGER.info("5.主动收到服务端,关闭channel server seqId {}",seqId);
                it.getChannelWrapped().channel().close();
            } catch (IOException e) {
                LOGGER.error("5.主动收到服务端,关闭channel失败 server seqId "+seqId,e);
                throw new RuntimeException(e);
            }
            it.getChannelWrapped().cumulation().clearAll();
            //通知远端删除
            byte[] cmdByte = "closeAck".getBytes();
            //占用2字节
            byte[] seqIdByte = Utils.int2Byte(seqId);
            try {
                LOGGER.info("6.主动收到服务端,发送ACK server seqId {}",seqId);
                WebsocketFrame.write(cmdByte, seqIdByte, new byte[0], seqId + "", channelWrapped.channel());
            } catch (IOException e) {
                LOGGER.error("6.主动收到服务端,发送ACK失败 server seqId "+seqId,e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void after() {
    }
}
