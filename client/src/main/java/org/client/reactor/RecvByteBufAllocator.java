package org.client.reactor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;

public class RecvByteBufAllocator {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    private static final int MIN_CAPACITY = 64;
    private static final int MAX_CAPACITY = 65535;
    private static final int DEFAULT_CAPACITY = 2048;
    //上次读取数量
    private int lastBytesRead;
    //本次读取所有数量
    private int totalBytesRead;
    //下次分配容量
    private int nextAllocateBufferSize;
    private boolean decreaseNow;
    private final String uuid;

    public RecvByteBufAllocator(String uuid) {
        this.totalBytesRead = 0;
        this.nextAllocateBufferSize = DEFAULT_CAPACITY;
        this.decreaseNow = false;
        this.uuid = uuid;
    }

    public ByteBuffer allocate() {
        LOGGER.debug("申请容量:{} {} ", nextAllocateBufferSize, uuid);
        return ByteBuffer.allocate(nextAllocateBufferSize);
    }

    public void lastBytesRead(int lastBytesRead) {
        this.lastBytesRead = lastBytesRead;
        if (lastBytesRead > 0) {
            totalBytesRead += lastBytesRead;
            LOGGER.debug("当前读取总数:{} {} ", totalBytesRead, uuid);
        }
    }

    //是否还有更多的数据可以读取
    public boolean maybeMoreDataSupplier() {
        return nextAllocateBufferSize == lastBytesRead;
    }

    public void release() {
    }

    /**
     * 对容量进行自动扩容缩容
     * 每次扩容缩容一倍大小，不能低于64，不能高于65535
     * 连续2次判断缩容才会执行。
     */
    public void autoScaling() {
        int nextAllocate = Math.max(nextAllocateBufferSize >> 1, MIN_CAPACITY);
        if (totalBytesRead <= nextAllocate) {
            if (decreaseNow) {
                LOGGER.debug("执行缩容 之前:{},现在:{} {} ", nextAllocateBufferSize, nextAllocate, uuid);
                nextAllocateBufferSize = nextAllocate;
                decreaseNow = false;
            } else {
                decreaseNow = true;
            }
        } else if (totalBytesRead >= nextAllocateBufferSize) {
            nextAllocate = Math.min(nextAllocateBufferSize << 1, MAX_CAPACITY);
            LOGGER.debug("执行扩容,之前:{},现在:{} {} ", nextAllocateBufferSize, nextAllocate, uuid);
            nextAllocateBufferSize = nextAllocate;
            decreaseNow = false;
        }
    }

    public void reset() {
        this.totalBytesRead = 0;
    }
}
