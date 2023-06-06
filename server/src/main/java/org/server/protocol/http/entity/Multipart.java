package org.server.protocol.http.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.server.entity.CompositeByteBuf;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * ------WebKitFormBoundarynPWPyRh984Z0DFpT
 * Content-Disposition: form-data; name="fname"
 * <p>
 * Bill
 * ------WebKitFormBoundarynPWPyRh984Z0DFpT
 * Content-Disposition: form-data; name="lname"
 * <p>
 * Gates
 * ------WebKitFormBoundarynPWPyRh984Z0DFpT
 * Content-Disposition: form-data; name="upload1"; filename="￨﾿ﾑ￦ﾜﾟ￩ﾝﾢ￨ﾯﾕ￩ﾗﾮ￩ﾢﾘ￦ﾔﾶ￩ﾛﾆ￧ﾚﾄ￥ﾉﾯ￦ﾜﾬ.txt"
 * Content-Type: text/plain
 * <p>
 * 111111
 * 111111
 * 22222222
 * 33333333
 * 44444444
 * 555555555
 * 666666
 * ------WebKitFormBoundarynPWPyRh984Z0DFpT
 * Content-Disposition: form-data; name="upload2"; filename=""
 * Content-Type: application/octet-stream
 * <p>
 * <p>
 * ------WebKitFormBoundarynPWPyRh984Z0DFpT--
 * <p>
 * 第一行：--boundary \r\n
 * 第二行：Content-Disposition\ r\n
 * 普通正文
 * 第三行：\r\n
 * 第四行：表单提交过来的值 \r\n
 * 流
 * 第三行：Content-Type \r\n
 * 第四行：\r\n
 * 第五行：正文 \r\n
 * 结束流空行
 * 第三行：Content-Type \r\n
 * 第四行：\r\n
 * 第五行：正文(这里为空则直接换行) \r\n
 * 第六行：boundary -- \r\n
 */
public class Multipart {
    protected static final Logger LOGGER = LogManager.getLogger(Multipart.class);
    private String name;
    private String value;
    private String filename;
    private String contentType;
    private CompositeByteBuf compositeByteBuf;


    private Multipart self() {
        return this;
    }

    public String name() {
        return name;
    }

    public Multipart name(String name) {
        this.name = name;
        return self();
    }

    public String value() {
        return value;
    }

    public Multipart value(String value) {
        this.value = value;
        return self();
    }

    public String filename() {
        return filename;
    }

    public Multipart filename(String filename) {
        this.filename = filename;
        return self();
    }

    public String contentType() {
        return contentType;
    }

    public Multipart contentType(String contentType) {
        this.contentType = contentType;
        return self();
    }

    public CompositeByteBuf compositeByteBuf() {
        return compositeByteBuf;
    }

    public Multipart compositeByteBuf(CompositeByteBuf compositeByteBuf) {
        this.compositeByteBuf = compositeByteBuf;
        return self();
    }

    public static List<Multipart> parse(CompositeByteBuf cumulation, String contentType) {
        List<Multipart> multiparts = new ArrayList<>();
        String[] split1 = contentType.split(";");
        String boundary2 = split1[1].trim();
        String segmentation = "--" + boundary2.substring(boundary2.indexOf("=") + 1);
        byte[] segmentationByte = segmentation.getBytes();
        String boundary = cumulation.readLine();
        if (!boundary.equals(segmentation)) {
            LOGGER.info("协议格式不对，返回400");
            throw new RuntimeException("prottocol 不对");
        }
        while (cumulation.remaining() > 0) {
            String disposition = cumulation.readLine();
            //说明读取结束，则关闭流，连续读取到2个\r\n则退出
            if (disposition.length() == 0) {
                break;
            }
            //如果是个附件，这里则是content-type。否则是个正文
            String content = cumulation.readLine();
            //针对附件的匹配
            int index = disposition.indexOf("name");
            String dis = disposition.substring(index, disposition.length());
            if (content.contains("Content-Type")) {
                String[] split = dis.split("; ");
                int i2 = split[0].indexOf("\"");
                int i1 = split[0].lastIndexOf("\"");
                String name = split[0].substring(i2 + 1, i1);

                i2 = split[1].indexOf("\"");
                i1 = split[1].lastIndexOf("\"");
                String filename = split[1].substring(i2 + 1, i1);

                LOGGER.info("name: {} filename: {}  Content-Type: {} ", name, filename, content);
                //附件的话，第四行是空行。
                String blank = cumulation.readLine();
                if (!blank.isEmpty()) {
                    //返回404
                    LOGGER.info("协议格式不对，返回400");
                    throw new RuntimeException("prottocol 不对");
                }
                //第五行才是二进制正文
                //读取到segmentationByte结束
                CompositeByteBuf data = read(cumulation, segmentationByte);
                multiparts.add(new Multipart().name(name).filename(filename).contentType(content.split(":")[1].trim()).compositeByteBuf(data));
            } else {
                //正文的话，第三行是空行。需要再次读取才能读取到value
                if (!content.isEmpty()) {
                    LOGGER.info("协议格式不对，返回400");
                    throw new RuntimeException("prottocol 不对");
                }
                content = cumulation.readLine();
                int i2 = dis.indexOf("\"");
                int i1 = dis.lastIndexOf("\"");
                String name = dis.substring(i2 + 1, i1);
                LOGGER.info("name: {} content: {}", name, content);
                boundary = cumulation.readLine();
                multiparts.add(new Multipart().name(name).value(content));
            }
        }
        return multiparts;
    }

    private static CompositeByteBuf read(CompositeByteBuf cumulation, byte[] end) {
        CompositeByteBuf result = new CompositeByteBuf();
        int index = 0;
        boolean flag = false;
        int capacity = 2048;
        ByteBuffer allocate = ByteBuffer.allocate(capacity);
        int length = "\r\n".getBytes().length;
        int removeLen = end.length + length;
        while (true) {
            byte b = cumulation.get();
            allocate.put(b);
            //查看是否在记录
            if (flag) {
                //查看当前是否和end一样，一样则记录
                if (b == end[index]) {
                    index++;
                    //说明读取结束。
                    if (index == end.length) {
                        //将allocate数据剔除对应长度。
                        int finalLen = allocate.position() - removeLen;
                        //说明截取的不够，则需要剔除result里面的。
                        if (finalLen < 0) {
                            result.remove(removeLen - allocate.position());
                            //及时释放
                            allocate = null;
                        } else {
                            allocate.position(finalLen);
                            //如果没有值，说明是空字符串。
                            allocate.flip();
                            if (allocate.remaining() > 0) {
                                result.composite(allocate);
                            } else {
                                //及时释放
                                allocate = null;
                            }
                        }
                        break;
                    }
                } else {
                    //不一样，则重新记录
                    index = 0;
                    flag = false;
                }
            } else if (b == end[index]) {
                index++;
                flag = true;
            }
            if (allocate.remaining() == 0) {
                allocate.flip();
                result.composite(allocate);
                allocate = ByteBuffer.allocate(capacity);
            }
        }
        byte b = cumulation.get();
        flag = false;
        if (b == '-') {
            b = cumulation.get();
            if (b == '-') {
                b = cumulation.get();
                flag = true;
            }
        }
        if (b == '\r') {
            b = cumulation.get();
            if (b == '\n') {
                //说明最终结束
                if (flag) {
                    LOGGER.info("fail read end");
                } else {
                    //说明当前读取的附件结束
                    LOGGER.info("read end");
                }
            }
        }
        return result;
    }
}
