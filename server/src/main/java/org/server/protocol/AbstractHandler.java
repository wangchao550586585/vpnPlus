package org.server.protocol;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.server.entity.ChannelWrapped;
import org.server.reactor.RecvByteBufAllocator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public abstract class AbstractHandler implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    protected ChannelWrapped channelWrapped;

    public AbstractHandler(ChannelWrapped channelWrapped) {
        this.channelWrapped = channelWrapped;
    }

    public ChannelWrapped getChannelWrapped() {
        return channelWrapped;
    }

    public void closeChildChannel() {
        try {
            channelWrapped.channel().close();
            channelWrapped.cumulation().clear();
            after();
        } catch (IOException e) {
            LOGGER.error("close childChannel " + channelWrapped.uuid(), e);
            throw new RuntimeException(e);
        }
    }

    public void after() {
    }

    public String uuid() {
        return channelWrapped.uuid();
    }

    public void run() {
        SocketChannel channel = channelWrapped.channel();
        String uuid = channelWrapped.uuid();
        RecvByteBufAllocator recvByteBufAllocator = channelWrapped.recvByteBufAllocator();
        //重置读取总数
        recvByteBufAllocator.reset();
        int length = -1;
        try {
            //1.获取分配器
            do {
                //2.分配byteBuff,记录读取数据数量到分配器中
                ByteBuffer buffer = recvByteBufAllocator.allocate();
                //3.读取数据，
                length = channel.read(buffer);
                recvByteBufAllocator.lastBytesRead(length);
                //4.说明读取结束
                if (length <= 0) {
                    //释放buffer
                    recvByteBufAllocator.release();
                    buffer = null;
                    //说明连接断开
                    if (length < 0) {
                        //说明读取结束
                        LOGGER.info(" child read end {}", uuid);
                        closeChildChannel();
                    }
                    break;
                }
                //5.执行业务方法
                process(buffer);
            } while (recvByteBufAllocator.maybeMoreDataSupplier());
            //如果buffer没装满则退出循环
            //4.通过本次读取数据数量，来判断下次读取数量
            if (length > 0) {
                recvByteBufAllocator.autoScaling();
            }
        } catch (Exception e) {
            closeChildChannel();
            LOGGER.error("childChannel read fail " + uuid, e);
        }
    }

    private void process(ByteBuffer buffer) throws Exception {
        //粘包处理
        //1.获取累加的bytebuffer
        buffer.flip();
        channelWrapped.cumulation().composite(buffer);
        //2.将buffer数据存储到累加buffer中
        //3.执行exec方法
        exec();
    }

    protected abstract void exec() throws Exception;
}
