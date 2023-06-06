package org.client.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.client.util.Utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * 只提供读和恢复
 */
public class CompositeByteBuf {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    private final List<ByteBuffer> buffers = new LinkedList<>();
    private int readIndex;
    private int lastReadIndex;

    public CompositeByteBuf() {
        lastReadIndex = readIndex = 0;
    }

    /**
     * 添加之前需要切换为可读状态
     *
     * @param buffer
     */
    public void composite(ByteBuffer buffer) {
        //每次添加新的buffer时，判断上次是否读到边界处了
        buffers.add(buffer);
    }

    public int remaining() {
        return buffers.stream().mapToInt(ByteBuffer::remaining).sum();
    }


    public void mark() {
        buffers.forEach(it -> {
            it.mark();
        });
        lastReadIndex = readIndex;
    }

    public void reset() {
        buffers.forEach(it -> {
            it.reset();
        });
        readIndex = lastReadIndex;
    }

    public byte get() {
        ByteBuffer byteBuffer = buffers.get(readIndex);
        byte value = byteBuffer.get();
        //说明读到结尾处
        if (byteBuffer.remaining() <= 0) {
            //不允许超过边界
            readIndex = Math.min(readIndex + 1, buffers.size());
        }

        return value;
    }

    public void clear() {
        for (int i = readIndex - 1; i >= 0; i--) {
            buffers.remove(i);
        }
        lastReadIndex = readIndex = 0;
    }

    public void write(SocketChannel remoteClient) throws IOException {
        for (int i = readIndex; i < buffers.size(); i++) {
            ByteBuffer byteBuffer = buffers.get(i);
            //printString(buffer, uuid + ": 写入远程服务器数据为：");
            //printHex(buffer, uuid + " child -> remote ：");
            remoteClient.write(byteBuffer);
        }
        clearAll();
    }

    public void clearAll() {
        lastReadIndex = readIndex = 0;
        buffers.clear();
    }

    /**
     * start-line     = request-line / status-line
     *
     * @return
     */
    public String readLine() {
        int remaining = remaining();
        ByteBuffer byteBuffer = buffers.get(readIndex);
        int position = byteBuffer.position();
        int i = 0;
        for (; i < remaining; i++) {
            byte b = get();
            if (b == '\r') {
                b = get();
                if (b == '\n') {
                    break;
                }
            }
        }
        byte[] bytes = new byte[i];
        System.arraycopy(byteBuffer.array(), position, bytes, 0, i);
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }


    public String read(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = get();
        }
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] readByte(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = get();
        }
        return bytes;
    }

    /**
     * 打印尚未读取的数据
     */
    public void print(String uuid) {
        for (int i = readIndex; i < buffers.size(); i++) {
            Utils.printString(buffers.get(i), uuid);
        }
    }

    public void remove(int len) {
        for (int i = buffers.size() - 1; i >= 0 && len > 0; i--) {
            ByteBuffer byteBuffer = buffers.get(i);
            int remaining = byteBuffer.remaining();
            int finalLen = remaining - len;
            if (finalLen > 0) {
                byteBuffer.limit(finalLen);
                //切换读模式
                break;
            } else {
                //从缓冲集合中剔除,他这里会释放集合里面的元素，所以不用管
                len -= remaining;
                buffers.remove(i);
            }
        }

    }

    /**
     * 将所有byte转成字符串
     *
     * @return
     */
    public String readAll() {
        return read(remaining());
    }

    /**
     * 将所有byte转成字符串
     *
     * @return
     */
    public byte[] readAllByte() {
        return readByte(remaining());
    }

    /**
     * 数据写入本地
     *
     * @param fileChannel
     */
    public void write(FileChannel fileChannel) {
        try {
            for (int i = readIndex; i < buffers.size(); i++) {
                fileChannel.write(buffers.get(i));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                fileChannel.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
