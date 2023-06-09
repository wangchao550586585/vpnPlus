package org.server.protocol.websocket.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.server.entity.ChannelWrapped;
import org.server.entity.CompositeByteBuf;
import org.server.protocol.websocket.server.WebsocketReceive;
import org.server.util.Pair;
import org.server.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import static org.server.util.Utils.copy;

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

    public static void write(byte[] cmdByte, byte[] seqIdByte, byte[] bytes, String uuid, WebsocketReceive websocketReceive) throws IOException {
        //存储字节长度
        byte[] payload = new byte[cmdByte.length + seqIdByte.length + bytes.length + 2];
        int off = 0;
        payload[off++] = (byte) cmdByte.length;
        off = Utils.copy(off, payload, cmdByte);
        payload[off++] = (byte) seqIdByte.length;
        off = Utils.copy(off, payload, seqIdByte);
        if (bytes.length > 0) {
            off = Utils.copy(off, payload, bytes);
        }
        WebsocketFrame.serverSendByte(payload, websocketReceive, uuid);
    }

    public static void serverSendByte(byte[] payloadData, WebsocketReceive websocketReceive, String uuid) throws IOException {
        //构建长度
        Pair<byte[], byte[]> pair = getLength(payloadData.length, uuid);
        //“负载字段”是用UTF-8编码的文本数据。
        WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.SEND_BINARY,
                DEFAULT_MASK,
                pair.getFirst(),
                pair.getSecond(),
                null,
                payloadData,
                websocketReceive,
                uuid);
    }

    /**
     * @param opcode
     * @param mask
     * @param payloadLen
     * @param payloadLenExtended
     * @param maskingKey
     * @param payloadData        字节显示。
     * @param websocketReceive
     * @param uuid
     * @throws IOException
     */
    public static void defaultFrame(OpcodeEnum opcode, byte mask, byte[] payloadLen, byte[] payloadLenExtended, byte[] maskingKey, byte[] payloadData, WebsocketReceive websocketReceive, String uuid) throws IOException {
        WebsocketFrame.builder()//构建状态行
                .fin(DEFAULT_FIN)//最后一个包含数据的帧的 FIN （ FIN 帧）字段必须设置为 1 。
                .rsv(DEFAULT_RSV)//固定
                .opcode(opcode.send())  //构建响应头
                .mask(mask)//客户端需要掩码
                .payloadLen(payloadLen)
                .payloadLenExtended(payloadLenExtended)
                .maskingKey(maskingKey)
                .payloadData(payloadData)//构建响应体
                .write(websocketReceive, uuid);
    }

    /**
     * 获取长度和扩展字段
     *
     * @param length
     * @return
     */
    private static Pair<byte[], byte[]> getLength(int length, String uuid) {
        byte[] payloadLenExtended = null;
        byte[] payloadLen;
        if (length < 126) {
            payloadLen = Utils.bytes2Binary((byte) length);
        } else if (length >= 126 && length <= 65535) {
            payloadLen = Utils.bytes2Binary((byte) 126);
            //如果是126，那么接下来的2个bytes解释为16bit的无符号整形作为负载数据的长度。
            //字节长度量以网络字节顺序表示
            payloadLenExtended = Utils.int2BinaryA2Byte(length);
        } else {
            //如果是127，那么接下来的8个bytes解释为一个64bit的无符号整形（最高位的bit必须为0）作为负载数据的长度。
            payloadLen = Utils.bytes2Binary((byte) 127);
            payloadLenExtended = Utils.long2BinaryA4Byte(length);
        }
        //这里len只有7位
        payloadLen = Arrays.copyOfRange(payloadLen, 1, payloadLen.length);
        return new Pair<>(payloadLen, payloadLenExtended);
    }

    /**
     * 获取解析之后的结果字节显示。
     *
     * @param cumulation
     * @return
     */
    private static byte[] getResult(CompositeByteBuf cumulation) {
        //可读数量
        int remaining = cumulation.remaining();
        cumulation.mark();
        if (remaining < 2) {
            LOGGER.warn("最少要2位数据包不完整 {} ", remaining);
            cumulation.reset();
            return null;
        }
        //1bit fin,3bit rsv,4bit opencode
        int off = 0;
        byte b = cumulation.get();
        //校验fin
        byte[] finByte = Utils.bytes2Binary(b);
        if (finByte[0] != 1) {
            LOGGER.error("fin 不为1");
            cumulation.reset();
            return null;
        }
        //校验code
        byte[] rsv = Arrays.copyOfRange(finByte, 1, 4);
        for (int i = 0; i < rsv.length; i++) {
            if (rsv[i] != 0x00) {
                LOGGER.info("rsv 前面三位必须为0");
                cumulation.reset();
                return null;
            }
        }

        ////
        off++;
        //1bit mask,7bit payload
        byte b1 = cumulation.get();
        off++;
        byte[] byte1 = Utils.bytes2Binary(b1);
        byte[] payloadLenByte = Arrays.copyOfRange(byte1, 1, byte1.length);
        int payloadLen = Utils.binary2Int(payloadLenByte);
        byte[] extendedPlay = null;
        long finalLen = payloadLen;
        if (payloadLen < 126) {
        } else if (payloadLen == 126) {
            if (remaining < off + 2) {
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
            if (remaining < off + 8) {
                cumulation.reset();
                LOGGER.warn("extendedPlay 数据包不完整");
                return null;
            }
            //如果是127，那么接下来的8个bytes解释为一个64bit的无符号整形（最高位的bit必须为0）作为负载数据的长度。
            extendedPlay = new byte[8];
            for (int i = 0; i < extendedPlay.length; i++) {
                extendedPlay[i] = cumulation.get();
            }
            finalLen = Utils.byteToLong(extendedPlay);
            off += 8;
        }
        //maskkey
        byte[] maskKey = null;
        if (byte1[0] == 0x01) {
            //maskkey
            if (remaining < off + 4) {
                cumulation.reset();
                LOGGER.info("mask 位数不够");
                return null;
            }
            maskKey = new byte[]{cumulation.get(), cumulation.get(), cumulation.get(), cumulation.get()};
            off += 4;
        }

        if (remaining < off + finalLen) {
            cumulation.reset();
            LOGGER.warn("payloadLen 不够");
            return null;
        }

        byte[] data = null;
        if (finalLen > 0) {
            data = new byte[(int) finalLen];
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
        return fra;
    }

    public static WebsocketFrame parse(ChannelWrapped channelWrapped) {
        byte[] frame = getResult(channelWrapped.cumulation());
        //表示获取frame索引
        int index = 0;
        if (frame == null) {
            return null;
        }
        //1bit fin,3bit rsv,4bit opencode
        byte[] finByte = Utils.bytes2Binary(frame[index++]);
        //表示这是消息的最后一个片段。第一个片段也有可能是最后一个片段。
        int off = 0;
        byte fin = finByte[off++];

        //必须设置为0，除非扩展了非0值含义的扩展。如果收到了一个非0值但是没有扩展任何非0值的含义，接收终端必须断开WebSocket连接。
        byte[] rsv = Arrays.copyOfRange(finByte, off, off + 3);
        off += 3;

        byte[] opcode = Arrays.copyOfRange(finByte, off, off + 4);
        off = 0;
        //1bit mask,7bit payload
        //定义“有效负载数据”是否添加掩码。默认1，掩码的键值存在于Masking-Key中
        byte[] tempByte = Utils.bytes2Binary(frame[index++]);
        byte mask = tempByte[off++];
        byte[] payloadLenBinary = Arrays.copyOfRange(tempByte, off, off + 7);
        byte[] payloadLenExtended = null;
        off = 0;
        //表示有多少个字节，而不是01。
        long payloadLen = Utils.binary2Int(payloadLenBinary);
        if (payloadLen <= 125) {
            //如果值为0-125，那么就表示负载数据的长度。
        } else if (payloadLen == 126) {
            payloadLenExtended = new byte[]{frame[index++], frame[index++]};
            payloadLen = Utils.bytes2Int(payloadLenExtended);
        } else {
            //如果是127，那么接下来的8个bytes解释为一个64bit的无符号整形（最高位的bit必须为0）作为负载数据的长度。
            payloadLenExtended = Arrays.copyOfRange(frame, index, (index + 8));
            index += 8;
            payloadLen = Utils.byteToLong(payloadLenExtended);
        }
        //所有从客户端发往服务端的数据帧都已经与一个包含在这一帧中的32 bit的掩码进行过了运算。
        //如果mask标志位（1 bit）为1，那么这个字段存在，如果标志位为0，那么这个字段不存在。
        byte[] maskingKey = null;
        if (mask == 1) {
            maskingKey = Arrays.copyOfRange(frame, index, (index + 4));
            index += 4;
        }
        //“有效负载数据”是指“扩展数据”和“应用数据”。
        byte[] payloadData = Arrays.copyOfRange(frame, index, (int) (index + payloadLen));
        index += payloadLen;
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

    public void write(WebsocketReceive websocketReceive, String uuid) throws IOException {
        byte[] response = build();
        ByteBuffer byteBuffer = ByteBuffer.wrap(response);
        websocketReceive.write(byteBuffer);
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
