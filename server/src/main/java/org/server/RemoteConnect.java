package org.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.server.protocol.AbstractHandler;
import org.server.protocol.http.entity.Resource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class RemoteConnect implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    final String host;
    final Integer port;
    final String uuid;
    final SocketChannel childChannel;
    Selector remoteSelector;
    SocketChannel remoteChannel;
    AbstractHandler connectionHandler;

    public RemoteConnect(String host, Integer port, String uuid, SocketChannel childChannel, AbstractHandler connectionHandler) {
        this.host = host;
        this.port = port;
        this.uuid = uuid;
        this.childChannel = childChannel;
        this.connectionHandler = connectionHandler;
    }

    public Resource connect() {
        Resource resource;
        try {
            this.remoteChannel = SocketChannel.open();
            Timer timer = new Timer();
            final SocketChannel finalSocketChannel = remoteChannel;
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (finalSocketChannel == null || !finalSocketChannel.isConnected()) {
                        try {
                            finalSocketChannel.close();
                            LOGGER.info("remote connect timeout {}", uuid);
                        } catch (Exception e) {
                            LOGGER.error("remote connect fail , so close fail " + uuid, e);
                            throw new RuntimeException(e);
                        }
                    }
                }
            }, 300);
            remoteChannel.connect(new InetSocketAddress(host, port));
            LOGGER.debug("remote connect success {} remoteAddress {} ", uuid, remoteChannel.getRemoteAddress());
            timer.cancel();
            remoteChannel.configureBlocking(false);
            while (!remoteChannel.finishConnect()) {
            }
            this.remoteSelector = Selector.open();
            remoteChannel.register(remoteSelector, SelectionKey.OP_READ);
            resource = new Resource().remoteChannel(remoteChannel).remoteSelector(remoteSelector);
            LOGGER.debug("remote register success {}", uuid);
        } catch (Exception exception) {
            //对方主动关闭，自己主动超时关闭
            if (exception instanceof AsynchronousCloseException || exception instanceof ClosedChannelException) {
                LOGGER.debug("remote connect fail {}", uuid);
            } else {
                LOGGER.error("remote connect fail " + uuid, exception);
            }
            LOGGER.info("remote close {}", uuid);
            closeRemote();
            //这里不能往上抛异常
            return null;
        }
        new Thread(this).start();
        return resource;
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (!remoteSelector.isOpen()) {
                    LOGGER.debug("selector 正常退出 {}", uuid);
                    break;
                }
                int n = remoteSelector.select();
                if (n == 0) {
                    if (!remoteSelector.isOpen()) {
                        LOGGER.debug("selector 正常退出 {}", uuid);
                        break;
                    }
                    continue;
                }
                if (n > 1) {
                    LOGGER.warn("监听过多 {}", uuid);
                }
                Set<SelectionKey> selectionKeys = remoteSelector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();
                    SocketChannel remoteChannel = (SocketChannel) selectionKey.channel();
                    if (selectionKey.isReadable()) {
                        ByteBuffer allocate = ByteBuffer.allocate(4096 * 5);
                        int read = remoteChannel.read(allocate);
                        if (read < 0) {
                            LOGGER.info("remote close ,because remote read end {}", uuid);
                            connectionHandler.closeChildChannel();
                            closeRemote();
                            break;
                        }
                        do {
                            allocate.flip();
                            childChannel.write(allocate);
                            allocate.clear();
                        } while (remoteChannel.read(allocate) > 0);
                        LOGGER.info("remote  -> child end {}", uuid);
                        //这里不能直接通知远端刷，因为异步通知远端后，读事件执行结束。后面select时，因为channel数据还没被读取，会导致再次select出来。
                    }
                }
            }
        } catch (Exception exception) {
            connectionHandler.closeChildChannel();
            closeRemote();
            LOGGER.error("remote select fail " + uuid, exception);
            throw new RuntimeException(exception);
        }
    }

    private void closeRemote() {
        if (null != remoteChannel) {
            try {
                remoteChannel.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (null != remoteSelector) {
            try {
                remoteSelector.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
