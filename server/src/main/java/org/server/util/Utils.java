package org.server.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.server.entity.CompositeByteBuf;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

public class Utils {
    protected static final Logger LOGGER = LogManager.getLogger(Utils.class);
    private static final Random r = new Random();

    /**
     * 将二进制数据转成字符串打印
     *
     * @param allocate
     * @param fix
     */
    public static void printString(ByteBuffer allocate, String fix) {
        if (allocate.remaining() > 0) {
            byte[] bytes = Arrays.copyOfRange(allocate.array(), allocate.position(), allocate.limit());
            try {
                String s = new String(bytes, "utf-8");
                LOGGER.info(" response \r\n{} ", s);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 将allocate转成十六进制打印
     *
     * @param allocate
     * @param fix
     */
    public static void printHex(ByteBuffer allocate, String fix) {
        if (allocate.remaining() > 0) {
            StringBuilder stringBuilder = new StringBuilder();
            byte[] bytes = Arrays.copyOfRange(allocate.array(), allocate.position(), allocate.limit());
            for (int i = 0; i < bytes.length; i++) {
                stringBuilder.append(byteToHex(bytes[i])).append(" ");
            }
            LOGGER.info(" {} {} ", fix, stringBuilder.toString());
        }
    }

    public static String byteToHex(byte b) {
        String hex = Integer.toHexString(b & 0xFF);
        if (hex.length() < 2) {
            hex = "0" + hex;
        }
        return hex;
    }

    /**
     * 二进制转十进制
     *
     * @param b
     * @return
     */
    public static int byteToIntV2(byte b) {
        return b & 0xFF;
    }



    public static int javaVersion() {
        String key = "java.specification.version";
        String version = "1.6";
        String value;
        if (System.getSecurityManager() == null) {
            value = System.getProperty(key);
        } else {
            value = AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty(key));

        }
        version = value == null ? version : value;
        return majorVersion(version);
    }

    public static int majorVersion(String version) {
        final String[] components = version.split("\\.");
        int[] javaVersion = new int[components.length];
        for (int i = 0; i < javaVersion.length; i++) {
            javaVersion[i] = Integer.parseInt(components[i]);
        }
        if (javaVersion[0] == 1) {
            assert javaVersion[1] >= 6;
            return javaVersion[1];
        }
        return javaVersion[0];
    }

    public static ClassLoader getSystemClassLoader() {
        if (System.getSecurityManager() == null) {
            return ClassLoader.getSystemClassLoader();
        } else {
            return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> ClassLoader.getSystemClassLoader());
        }
    }

    /**
     * ByteBuffer转成01显示
     *
     * @param byteBuffer
     * @return
     */
    public static byte[] bytes2Binary(ByteBuffer byteBuffer) {
        byte[] result = null;
        if (byteBuffer.remaining() > 0) {
            byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
            result = bytes2Binary(bytes);
        }
        return result;
    }

    /**
     * 字节转成01显示
     *
     * @param bytes
     * @return
     */
    public static byte[] bytes2Binary(byte[] bytes) {
        byte[] result = new byte[bytes.length * 8];
        for (int i = 0; i < bytes.length; i++) {
            byte[] dest = bytes2Binary(bytes[i]);
            System.arraycopy(dest, 0, result, i * 8, 8);
        }
        return result;
    }

    /**
     * 获取单个字节0101二进制显示。
     *
     * @param aByte
     * @return
     */
    public static byte[] bytes2Binary(byte aByte) {
        return new byte[]{(byte) ((aByte >> 7) & 0x1)
                , (byte) ((aByte >> 6) & 0x1)
                , (byte) ((aByte >> 5) & 0x1)
                , (byte) ((aByte >> 4) & 0x1)
                , (byte) ((aByte >> 3) & 0x1)
                , (byte) ((aByte >> 2) & 0x1)
                , (byte) ((aByte >> 1) & 0x1)
                , (byte) (aByte & 0x1)
        };
    }

    /**
     * 01的bit数组转成字节数组
     *
     * @param payloadData
     * @return
     */
    public static byte[] binary2Bytes(byte[] payloadData) {
        byte[] result = new byte[payloadData.length / 8];
        int r = 0;
        byte[] bytes1 = new byte[8];
        for (int i = 0; i < payloadData.length; i++) {
            int i1 = i % 8;
            if (i != 0 && i1 == 0) {
                result[r++] = (byte) Utils.binary2Int(bytes1);
            }
            bytes1[i1] = payloadData[i];
        }
        result[r] = (byte) Utils.binary2Int(bytes1);
        return result;
    }

    /**
     * 01的bit数组转成字节数组到result
     * @param payloadData
     * @param result
     */
    public static void binary2Bytes(byte[] payloadData,byte[]result) {
        int r = 0;
        byte[] bytes1 = new byte[8];
        for (int i = 0; i < payloadData.length; i++) {
            int i1 = i % 8;
            if (i != 0 && i1 == 0) {
                result[r++] = (byte) Utils.binary2Int(bytes1);
            }
            bytes1[i1] = payloadData[i];
        }
        result[r] = (byte) Utils.binary2Int(bytes1);
    }

    /**
     * 01转成单个int
     * 按照大端表示法
     *
     * @param bytes
     * @return
     */
    public static int binary2Int(byte[] bytes) {
        int result = 0;
        int off = 0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            result += (bytes[i] << off);
            off++;
        }
        return result;
    }

    /**
     * 大端字节，转成单个int
     * @param bytes
     * @return
     */
    public static int bytes2Int(byte[] bytes) {
        return binary2Int(bytes2Binary(bytes));
    }



    /**
     * 打印01组成的数组
     * 格式如下：
     * 11100101 00111111 00100000 01110011
     *
     * @param frame
     */
    public static String buildBinaryReadable(byte[] frame) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < frame.length; i++) {
            if (i != 0 && (i % 8) == 0) {
                sb.append(" ");
            }
            if (i != 0 && (i % 64) == 0) {
                sb.append("\r\n");
            }
            sb.append(frame[i]);
        }
        return sb.toString();
    }


    /**
     * 反掩码并编译成字符串
     *
     * @param payloadData 01组成数组
     * @param maskingKey  01组成数组
     * @return
     */
    public static String unmask(byte[] payloadData, byte[] maskingKey) {
        byte[] result = binary2Bytes(payloadData);
        byte[] mask = binary2Bytes(maskingKey);
        return new String(mask(result, mask));
    }

    /**
     * 反掩码并编译成字节数组
     *
     * @param payloadData 01组成数组
     * @param maskingKey  01组成数组
     * @return
     */
    public static byte[] unmaskBytes(byte[] payloadData, byte[] maskingKey) {
        byte[] result = binary2Bytes(payloadData);
        byte[] mask = binary2Bytes(maskingKey);
        return mask(result, mask);
    }

    /**
     * 进行掩码
     * original-octet-i：为原始数据的第i字节。
     * transformed-octet-i：为转换后的数据的第i字节。
     * j：为i mod 4的结果。
     * masking-key-octet-j：为mask key第j字节。
     * 算法描述为：
     * original-octet-i与 masking-key-octet-j异或后，得到 transformed-octet-i。
     * j = i MOD 4
     * transformed-octet-i = original-octet-i XOR masking-key-octet-j
     *
     * @param payloadData 字节
     * @param maskingKey  字节
     * @return
     */
    public static byte[] mask(byte[] payloadData, byte[] maskingKey) {
        for (int i = 0; i < payloadData.length; i++) {
            payloadData[i] = (byte) (payloadData[i] ^ maskingKey[i % 4]);
        }
        return payloadData;
    }

    /**
     * int转成按照网络大端存储，采用二进制显示
     *
     * @param code
     * @return
     */
    public static byte[] int2BinaryA2Byte(int code) {
        byte[] sendPayloadData;
        int i = code / 256;
        int i1 = code % 256;
        byte[] bytes = Utils.bytes2Binary((byte) i);
        byte[] bytes2 = Utils.bytes2Binary((byte) i1);
        sendPayloadData = new byte[bytes.length + bytes2.length];
        System.arraycopy(bytes, 0, sendPayloadData, 0, bytes.length);
        System.arraycopy(bytes2, 0, sendPayloadData, bytes.length, bytes2.length);
        return sendPayloadData;
    }

    /**
     * int 转成2字节存储
     *
     * @param code
     * @return
     */
    public static byte[] int2Byte(int code) {
        int i = code / 256;
        int i1 = code % 256;
        return new byte[]{(byte) i, (byte) i1};
    }

    /**
     * copy: src -> desc
     * src从索引0开始拷贝
     * 从dest数组off下标位置，拷贝src数组的长度。
     *
     * @param off
     * @param dest
     * @param src
     * @return
     */
    public static int copy(int off, byte[] dest, byte[] src) {
        System.arraycopy(src, 0, dest, off, src.length);
        return (off + src.length);
    }

    /**
     * 合并2个字节数组
     *
     * @param arr1
     * @param arr2
     * @return
     */
    public static byte[] merge(byte[] arr1, byte[] arr2) {
        byte[] result = new byte[arr1.length + arr2.length];
        copy(0, result, arr1);
        copy(arr1.length, result, arr2);
        return result;
    }

    public static <T> T parse(CompositeByteBuf cumulation, Class<T> clazz) throws Exception {
        T t = clazz.getConstructor().newInstance();
        while (cumulation.remaining() > 0) {
            String requestLine = cumulation.readLine();
            //说明读取结束，则关闭流，连续读取到2个\r\n则退出
            if (requestLine.length() == 0) {
                break;
            }
            String[] arr = requestLine.split(":");
            try {
                String key = arr[0];
                Object value = arr[1].trim();
                int pre = key.indexOf("-");
                if (pre > 0) {
                    String substring = key.substring(0, pre).toLowerCase();
                    String s = key.substring(pre, key.length()).replaceAll("-", "");
                    key = substring + s;
                } else {
                    key = key.toLowerCase();
                }
                Field field = t.getClass().getDeclaredField(key);
                if (field.getType() != String.class) {
                    Class<?> type = field.getType();
                    value = type.getConstructor(String.class).newInstance(value);
                }
                field.setAccessible(true);
                field.set(t, value);
            } catch (NoSuchFieldException e) {
                LOGGER.error("NoSuchFieldException {}: {}", arr[0], arr[1]);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return t;
    }

    private static final byte[] MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes();

    /**
     * 请求必须包含一个名为`Sec-WebSocket-Key`的header字段。这个header字段的值必须是由一个随机生成的16字节的随机数通过base64
     * （见[RFC4648的第四章][3]）编码得到的。每一个连接都必须随机的选择随机数。
     * 注意：例如，如果随机选择的值的字节顺序为0x01 0x02 0x03 0x04 0x05 0x06 0x07 0x08 0x09 0x0a 0x0b 0x0c 0x0d 0x0e 0x0f 0x10，
     * 那么header字段的值就应该是"AQIDBAUGBwgJCgsMDQ4PEC=="。
     * <p>
     * 如果客户端收到的`Sec-WebSocket-Accept`header字段或者`Sec-WebSocket-Accept`header字段不等于通过`Sec-WebSocket-Key`字段的值
     * （作为一个字符串，而不是base64解码后）和"258EAFA5-E914-47DA-95CA-C5AB0DC85B11"串联起来，
     * 忽略所有前后空格进行SHA-1编码然后base64值，那么客户端必须关闭连接。
     *
     * @param s
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String getKey(String s) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA1");
        md.update(s.getBytes());
        md.update(MAGIC);
        return Base64.getEncoder().encodeToString(md.digest());
    }

    /**
     * 构建4字节掩码
     *
     * @return
     */
    public static byte[] buildMask() {
        byte[] maskingKey = new byte[4];
        for (int i = 0; i < maskingKey.length; i++) {
            int i1 = r.nextInt(256);
            maskingKey[i] = (byte) i1;
        }
        return maskingKey;
    }

}
