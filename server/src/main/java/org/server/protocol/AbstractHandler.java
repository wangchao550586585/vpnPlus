package org.server.protocol;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.server.entity.ChannelWrapped;
import org.server.reactor.RecvByteBufAllocator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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


    //1.计算要发送的数据大小
    //2.将数据添加链表末尾
    //3.查看是否超过限制，超过则发送不可写事件。
    //4.将flush指针指向头结点，指向头结点的指针重置为null
    //5.将所有节点设置为不可取消，当更新后的水位线低于低水位线 `DEFAULT_LOW_WATER_MARK = 32 * 1024` 时，就将当前 channel 设置为可写状态。
    //6.判断channel是否打开和连接
    //7.循环刷出数据，数据为空则重置opwrite事件
    //每次发送2048数据，<0则注册opwrite事件，每次发送后更新刷入容量。2048可修改。
    //单个节点全部写完则删除节点
    //退出循环时<16则说明
    private LinkedBlockingQueue<ByteBuffer> linkedBlockingQueue = new LinkedBlockingQueue<>();

    public void write(ByteBuffer byteBuffer) {
        linkedBlockingQueue.add(byteBuffer);
        //1、查看状态是否可写，可写则刷出数据。不可写则添加到缓冲区。
    }

    private final ReentrantLock takeLock = new ReentrantLock();
    private final Condition condition = takeLock.newCondition();

    public void doWrite() {
        try {
            //是否被标记了写事件
            boolean isWrite=false;
            while (true) {
                ByteBuffer poll = linkedBlockingQueue.peek();
                if (Objects.isNull(poll)&&isWrite) {
                    //说明读取结束，则删除写事件
                    incompleteWrite(false);
                    LOGGER.info("删除写模式");
                    isWrite=false;
                } else if(Objects.nonNull(poll)) {
                    int localWrittenBytes = channelWrapped.channel().write(poll);
                    if (localWrittenBytes <= 0) {
                        //如果依旧可读，则放入链表头部，继续发送.
                        //2.返回值<=0说明缓冲区已满，则设置状态为不可写，并注册写事件。
                        if (!isWrite){
                            incompleteWrite(true);
                            isWrite=true;
                            LOGGER.info("缓冲区数据过多，切换写模式");
                        }
                        //自旋等待被唤醒。
                        await();
                    }else{
                        if (poll.remaining()<=0){
                            linkedBlockingQueue.remove();
                        }else {
                            LOGGER.info("数据没写完");
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void await() {
        try {
            takeLock.lock();
            condition.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            takeLock.unlock();
        }

    }

    public void signal() {
        try {
            takeLock.lock();
            condition.signal();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            takeLock.unlock();
        }
    }

    public void incompleteWrite(boolean setOpWrite) {
        if (setOpWrite) {
            setOpWrite();
        } else {
            clearOpWrite();
        }
    }

    public void clearOpWrite() {
        SelectionKey key = channelWrapped.key();
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
        }
    }

    public void setOpWrite() {
        SelectionKey key = channelWrapped.key();
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(interestOps | SelectionKey.OP_WRITE);
        }
    }

}
