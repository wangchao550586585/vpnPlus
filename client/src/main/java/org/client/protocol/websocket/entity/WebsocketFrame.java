package org.client.protocol.websocket.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.client.entity.ChannelWrapped;
import org.client.entity.CompositeByteBuf;
import org.client.util.Pair;
import org.client.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Objects;

import static org.client.util.Utils.copy;

public class WebsocketFrame {
    protected static final Logger LOGGER = LogManager.getLogger(WebsocketFrame.class);

    public enum OpcodeEnum {
        //定义“有效负载数据”的解释。
        //%x1 表示一个文本帧
        SEND_UTF(new byte[]{0x00, 0x00, 0x00, 0x01}),
        //%x2 表示一个二进制帧
        SEND_BINARY(new byte[]{0x00, 0x00, 0x01, 0x00}),
        //%x8 表示一个连接关闭包
        CLOSE(new byte[]{0x01, 0x00, 0x00, 0x00}),
        //%x9 表示一个ping包
        PING(new byte[]{0x01, 0x00, 0x00, 0x01}),
        //%xA 表示一个pong包
        PONG(new byte[]{0x01, 0x00, 0x01, 0x00});
        byte[] send;

        OpcodeEnum(byte[] send) {
            this.send = send;
        }

        public byte[] send() {
            return send;
        }
    }

    byte fin;
    byte[] rsv;
    byte[] opcode;
    byte mask;
    byte[] payloadLen;  //字节的长度，而不是二进制数据的长度
    byte[] payloadLenExtended;
    //字节显示
    byte[] maskingKey;
    //字节显示
    byte[] payloadData;
    private int length;
    private final static byte DEFAULT_FIN = 0x01;
    private final static byte[] DEFAULT_RSV = {0x00, 0x00, 0x00};
    public final static byte DEFAULT_MASK = 0x00;
    public final static byte DEFAULT_HAS_MASK = 0x01;
    private final static byte[] DEFAULT_PAYLOAD_LEN = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    public static WebsocketFrame builder() {
        return new WebsocketFrame();
    }

    /**
     * @param opcode
     * @param mask
     * @param payloadLen
     * @param payloadLenExtended
     * @param maskingKey
     * @param payloadData        字节显示。
     * @param channel
     * @param uuid
     * @throws IOException
     */
    public static void defaultFrame(OpcodeEnum opcode, byte mask, byte[] payloadLen, byte[] payloadLenExtended, byte[] maskingKey, byte[] payloadData, SocketChannel channel, String uuid) throws IOException {
        WebsocketFrame.builder()//构建状态行
                .fin(DEFAULT_FIN)//最后一个包含数据的帧的 FIN （ FIN 帧）字段必须设置为 1 。
                .rsv(DEFAULT_RSV)//固定
                .opcode(opcode.send())  //构建响应头
                .mask(mask)//客户端需要掩码
                .payloadLen(payloadLen)
                .payloadLenExtended(payloadLenExtended)
                .maskingKey(maskingKey)
                .payloadData(payloadData)//构建响应体
                .write(channel, uuid);
    }

    /**
     * 服务端默认发送
     *
     * @param msg
     * @throws IOException
     */
    public static void serverSendUTF(String msg, SocketChannel channel, String uuid) throws IOException {
        byte[] payloadData = msg.getBytes();
        //构建长度
        Pair<byte[], byte[]> pair = getLength(payloadData.length);
        WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.SEND_UTF,
                DEFAULT_MASK,
                pair.getFirst(),
                pair.getSecond(),
                null,
                payloadData,
                channel,
                uuid);
    }

    /**
     * 客户端默认发送
     *
     * @param msg
     * @param channel
     * @param uuid
     * @throws IOException
     */
    public static void clientSendUTF(String msg, SocketChannel channel, String uuid) throws IOException {
        //发送一个hello
        byte[] payloadData = msg.getBytes();
        //构建长度
        Pair<byte[], byte[]> pair = getLength(payloadData.length);

        //4字节
        byte[] maskingKey = Utils.buildMask();
        payloadData = Utils.mask(payloadData, maskingKey);

        //“负载字段”是用UTF-8编码的文本数据。
        WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.SEND_UTF,
                DEFAULT_HAS_MASK,
                pair.getFirst(),
                pair.getSecond(),
                maskingKey,
                payloadData,
                channel,
                uuid);
    }

    public static void write(byte[] cmdByte, byte[] seqIdByte, byte[] bytes, String uuid, SocketChannel remoteChannel) throws IOException {
        //存储字节长度
        byte[] payload = new byte[cmdByte.length + seqIdByte.length + bytes.length + 2];
        int off = 0;
        payload[off++] = (byte) cmdByte.length;
        off = Utils.copy(off, payload, cmdByte);
        payload[off++] = (byte) seqIdByte.length;
        off = Utils.copy(off, payload, seqIdByte);
        off = Utils.copy(off, payload, bytes);
        WebsocketFrame.clientSendByte(payload, remoteChannel, uuid);
    }

    public static void clientSendByte(byte[] payloadData, SocketChannel channel, String uuid) throws IOException {
        //构建长度
        Pair<byte[], byte[]> pair = getLength(payloadData.length);
        //4字节
        byte[] maskingKey = Utils.buildMask();
        payloadData = Utils.mask(payloadData, maskingKey);

        //“负载字段”是用UTF-8编码的文本数据。
        WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.SEND_BINARY,
                DEFAULT_HAS_MASK,
                pair.getFirst(),
                pair.getSecond(),
                maskingKey,
                payloadData,
                channel,
                uuid);
    }

    /**
     * 获取长度和扩展字段
     *
     * @param length
     * @return
     */
    private static Pair<byte[], byte[]> getLength(int length) {
        byte[] payloadLenExtended = null;
        byte[] payloadLen = null;
        if (length < 126) {
            payloadLen = Utils.bytes2Binary((byte) length);
            //这里len只有7位
            payloadLen = Arrays.copyOfRange(payloadLen, 1, payloadLen.length);
        } else if (length >= 126 && length <= 65535) {
            payloadLen = Utils.bytes2Binary((byte) 126);
            //这里len只有7位
            payloadLen = Arrays.copyOfRange(payloadLen, 1, payloadLen.length);
            //如果是126，那么接下来的2个bytes解释为16bit的无符号整形作为负载数据的长度。
            //字节长度量以网络字节顺序表示
            payloadLenExtended = Utils.int2BinaryA2Byte(length);
        } else {
            //如果是127，那么接下来的8个bytes解释为一个64bit的无符号整形（最高位的bit必须为0）作为负载数据的长度。
            // TODO: 2023/6/1 超过65535太长了，用不着
        }
        return new Pair<>(payloadLen, payloadLenExtended);
    }

    public static WebsocketFrame parse(ChannelWrapped channelWrapped) {
        //byte[] result4 = getResult(channelWrapped.cumulation());

        //打印
        //byte[] frame = channelWrapped.cumulation().binaryString();
        byte[] frame = getResult(channelWrapped.cumulation());
        if (frame==null){
            return null;
        }
        if (frame[0] != 1) {
            LOGGER.error("not 1");
            return null;
        }
        String s = Utils.buildBinaryReadable(frame);
        LOGGER.info("receive frame {} {}", s, channelWrapped.uuid());
        //表示这是消息的最后一个片段。第一个片段也有可能是最后一个片段。
        int off = 0;
        byte fin = frame[off];
        //必须设置为0，除非扩展了非0值含义的扩展。如果收到了一个非0值但是没有扩展任何非0值的含义，接收终端必须断开WebSocket连接。
        off++;
        byte[] rsv = Arrays.copyOfRange(frame, off, off + 3);
        off += 3;
        for (int i = 0; i < rsv.length; i++) {
            if (rsv[i] != 0x00) {
                LOGGER.info("rsv 前面三位必须为0");
                return null;
            }
        }
        byte[] opcode = Arrays.copyOfRange(frame, off, off + 4);
        off += 4;
        //定义“有效负载数据”是否添加掩码。默认1，掩码的键值存在于Masking-Key中
        byte mask = frame[off];
        off++;
        byte[] payloadLenBinary = Arrays.copyOfRange(frame, off, off + 7);
        byte[] payloadLenExtended = null;
        off += 7;
        //表示有多少个字节，而不是01。
        int payloadLen = Utils.binary2Int(payloadLenBinary);
        if (payloadLen <= 125) {
            //如果值为0-125，那么就表示负载数据的长度。
        } else if (payloadLen == 126) {
            //如果是126，那么接下来的2个bytes解释为16bit的无符号整形作为负载数据的长度。
            payloadLenExtended = Arrays.copyOfRange(frame, off, (off + 2 * 8));
            off += 2 * 8;
            payloadLen = Utils.binary2Int(payloadLenExtended);
        } else {
            //如果是127，那么接下来的8个bytes解释为一个64bit的无符号整形（最高位的bit必须为0）作为负载数据的长度。
            payloadLenExtended = Arrays.copyOfRange(frame, off, (off + 8 * 8));
            off += 8 * 8;
            payloadLen = Utils.binary2Int(payloadLenExtended);
        }
        //所有从客户端发往服务端的数据帧都已经与一个包含在这一帧中的32 bit的掩码进行过了运算。
        //如果mask标志位（1 bit）为1，那么这个字段存在，如果标志位为0，那么这个字段不存在。
        byte[] maskingKey = null;
        if (mask == 1) {
            maskingKey = Arrays.copyOfRange(frame, off, (off + 4 * 8));
            off += 4 * 8;
        }
        //“有效负载数据”是指“扩展数据”和“应用数据”。
        byte[] payloadData = Arrays.copyOfRange(frame, off, (off + payloadLen * 8));
        off += payloadLen * 8;
        return WebsocketFrame.builder()
                .fin(fin)
                .rsv(rsv)
                .opcode(opcode)
                .mask(mask)
                .payloadLen(payloadLenBinary)
                .payloadLenExtended(payloadLenExtended)
                .maskingKey(maskingKey)
                .payloadData(payloadData);
    }

    private static byte[] getResult(CompositeByteBuf cumulation) {
        //可读数量
        int remaining = cumulation.remaining();
        cumulation.mark();
        if (remaining < 2) {
            LOGGER.warn("最少要2位数据包不完整 {} ",remaining);
            cumulation.reset();
            return null;
        }
        //1bit fin,3bit rsv,4bit opencode
        int off = 0;
        byte b = cumulation.get();
        off++;
        //1bit mask,7bit payload
        byte b1 = cumulation.get();
        off++;
        byte[] byte1 = Utils.bytes2Binary(b1);
        byte[] payloadLenByte = Arrays.copyOfRange(byte1, 1, byte1.length);
        int payloadLen = Utils.binary2Int(payloadLenByte);
        byte[] extendedPlay = null;
        int finalLen = payloadLen;

        if (payloadLen < 126) {
        } else if (payloadLen >= 126 && payloadLen <= 65535) {
            if (remaining < off+2) {
                cumulation.reset();
                LOGGER.warn("extendedPlay 数据包不完整");
                return null;
            }
            //如果是126，那么接下来的2个bytes解释为16bit的无符号整形作为负载数据的长度。
            //字节长度量以网络字节顺序表示
            extendedPlay = new byte[]{cumulation.get(), cumulation.get()};
            off += 2;
            finalLen = Utils.byteToIntV2(extendedPlay[0]) * 256 + Utils.byteToIntV2(extendedPlay[1]);
        } else {
            //如果是127，那么接下来的8个bytes解释为一个64bit的无符号整形（最高位的bit必须为0）作为负载数据的长度。
            // TODO: 2023/6/1 超过65535太长了，用不着
        }
        //maskkey
        byte[] maskKey = null;
        if (byte1[0] == 0x01) {
            //maskkey
            if (remaining < off+4) {
                cumulation.reset();
                LOGGER.info("mask 位数不够");
                return null;
            }
            maskKey = new byte[]{cumulation.get(), cumulation.get(), cumulation.get(), cumulation.get()};
            off += 4;
        }

        if (remaining < off+finalLen) {
            cumulation.reset();
            LOGGER.warn("payloadLen 不够");
            return null;
        }

        byte[] data = null;
        if (finalLen > 0) {
            data = new byte[finalLen];
            for (int i = 0; i < finalLen; i++) {
                data[i] = cumulation.get();
            }
            off += finalLen;
        }
        byte[] fra = new byte[off];
        off = 0;
        fra[off++] = b;
        fra[off++] = b1;
        if (Objects.nonNull(extendedPlay)) {
            off = copy(off, fra, extendedPlay);
        }
        if (Objects.nonNull(maskKey)) {
            off = copy(off, fra, maskKey);
        }
        if (Objects.nonNull(data)) {
            off = copy(off, fra, data);
        }
        //fra转成binnary
        byte[] result = new byte[off * 8];
        for (int i = 0; i < off; i++) {
            byte[] dest = Utils.bytes2Binary(fra[i]);
            System.arraycopy(dest, 0, result, i * 8, dest.length);
        }
        return result;
    }

    public byte[] build() {
        int off = 0;
        byte[] bytes = new byte[length];
        bytes[off++] = fin;
        off = copy(off, bytes, rsv);
        off = copy(off, bytes, opcode);
        bytes[off++] = mask;
        off = copy(off, bytes, payloadLen);
        if (Objects.nonNull(payloadLenExtended)) {
            off = copy(off, bytes, payloadLenExtended);
        }
        off = length / 8;
        if (Objects.nonNull(maskingKey)) {
            off += maskingKey.length;
        }
        if (Objects.nonNull(payloadData)) {
            off += payloadData.length;
        }
        byte[] result = new byte[off];
        Utils.binary2Bytes(bytes, result);

        off = length / 8;
        if (Objects.nonNull(maskingKey)) {
            off = copy(off, result, maskingKey);
        }
        if (Objects.nonNull(payloadData)) {
            off = copy(off, result, payloadData);
        }
        return result;
    }

    private WebsocketFrame self() {
        return this;
    }

    public WebsocketFrame fin(byte fin) {
        this.fin = fin;
        length += 1;
        return self();
    }

    public WebsocketFrame rsv(byte[] rsv) {
        this.rsv = rsv;
        length += rsv.length;
        return self();
    }

    public WebsocketFrame opcode(byte[] opcode) {
        this.opcode = opcode;
        length += opcode.length;
        return self();
    }

    public WebsocketFrame mask(byte mask) {
        this.mask = mask;
        length += 1;
        return self();
    }

    public WebsocketFrame payloadLen(byte[] payloadLen) {
        if (Objects.nonNull(payloadLen)) {
            this.payloadLen = payloadLen;
            length += payloadLen.length;
        } else {
            this.payloadLen = DEFAULT_PAYLOAD_LEN;
            length += DEFAULT_PAYLOAD_LEN.length;
        }
        return self();
    }

    public WebsocketFrame payloadLenExtended(byte[] payloadLenExtended) {
        if (Objects.nonNull(payloadLenExtended)) {
            this.payloadLenExtended = payloadLenExtended;
            length += payloadLenExtended.length;
        }
        return self();
    }

    public WebsocketFrame maskingKey(byte[] maskingKey) {
        if (Objects.nonNull(maskingKey)) {
            this.maskingKey = maskingKey;
        }
        return self();
    }

    public WebsocketFrame payloadData(byte[] payloadData) {
        if (Objects.nonNull(payloadData)) {
            this.payloadData = payloadData;
        }
        return self();
    }

    public byte fin() {
        return fin;
    }

    public byte[] rsv() {
        return rsv;
    }

    public byte[] opcode() {
        return opcode;
    }

    public byte mask() {
        return mask;
    }

    public byte[] payloadLen() {
        return payloadLen;
    }

    public byte[] payloadLenExtended() {
        return payloadLenExtended;
    }

    public byte[] maskingKey() {
        return maskingKey;
    }

    public byte[] payloadData() {
        return payloadData;
    }

    public void write(SocketChannel channel, String uuid) throws IOException {
        byte[] response = build();
        LOGGER.info("send frame {} {} ", Arrays.toString(Utils.bytes2Binary(response)), uuid);
        ByteBuffer byteBuffer = ByteBuffer.wrap(response);
        channel.write(byteBuffer);
    }

    @Override
    public String toString() {
        return "Frame{" +
                "fin=" + fin +
                ", rsv=" + Arrays.toString(rsv) +
                ", opcode=" + Arrays.toString(opcode) +
                ", mask=" + mask +
                ", payloadLen=" + Arrays.toString(payloadLen) +
                ", payloadLenExtended=" + Arrays.toString(payloadLenExtended) +
                ", maskingKey=" + Arrays.toString(maskingKey) +
                ", payloadData=" + Arrays.toString(payloadData) +
                '}';
    }
}
