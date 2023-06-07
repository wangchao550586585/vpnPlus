package org.server.protocol.http.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.server.entity.ChannelWrapped;
import org.server.protocol.AbstractHandler;
import org.server.protocol.websocket.server.WebsocketReceive;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;


public class HttpClient implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    //远端的host
    final String host;
    //远端的port
    final Integer port;
    //最终访问的host
    final String targetHost;
    //最终访问的port
    final Integer targetPort;
    final Integer seqId;
    final SocketChannel wsChannel;
    final WebsocketReceive websocketReceive;
    Selector remoteSelector;
    SocketChannel remoteChannel;
    SelectionKey selectionKey;
    volatile boolean flag;

    public HttpClient(String host, Integer port, int seqId, SocketChannel wsChannel, String targetHost, Integer targetPort, WebsocketReceive websocketReceive) {
        this.host = host;
        this.port = port;
        this.flag = true;
        this.seqId = seqId;
        this.wsChannel = wsChannel;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.websocketReceive = websocketReceive;
    }

    public HttpClient connect() {
        try {
            this.remoteChannel = SocketChannel.open();
            remoteChannel.connect(new InetSocketAddress(host, port));
            LOGGER.debug("remote connect success remoteAddress {} ", remoteChannel.getRemoteAddress());
            remoteChannel.configureBlocking(false);
            while (!remoteChannel.finishConnect()) {
            }
            this.remoteSelector = Selector.open();
            selectionKey = remoteChannel.register(remoteSelector, 0);
            ChannelWrapped channelWrapped = ChannelWrapped.builder().key(selectionKey).channel(remoteChannel);
            Runnable handler = new HttpClientHandler(channelWrapped, seqId, targetHost, targetPort, wsChannel, this);
            selectionKey.attach(handler);
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            LOGGER.debug("remote register success");
        } catch (Exception exception) {
            //对方主动关闭，自己主动超时关闭
            if (exception instanceof AsynchronousCloseException || exception instanceof ClosedChannelException) {
                LOGGER.debug("remote connect fail");
            } else {
                LOGGER.error("remote connect fail ", exception);
            }
            LOGGER.info("remote close");
            //这里不能往上抛异常
            return null;
        }
        new Thread(this).start();
        return this;
    }

    @Override
    public void run() {
        try {
            while (flag) {
                int n = remoteSelector.select();
                if (n == 0) {
                    continue;
                }
                Set<SelectionKey> selectionKeys = remoteSelector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();
                    Runnable runnable = (Runnable) selectionKey.attachment();
                    runnable.run();
                }
            }
        } catch (Exception exception) {
            LOGGER.error("remote select fail ", exception);
            throw new RuntimeException(exception);
        } finally {
            if (Objects.nonNull(remoteSelector)) {
                try {
                    LOGGER.info("关闭 channel remoteSelector seqId {} success ", seqId);
                    remoteSelector.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    public void write(ByteBuffer wrap) throws IOException {
        remoteChannel.write(wrap);
    }

    public void close() {
        try {
            //删除channel
            //这里可能在升级状态中客户端关闭
            LOGGER.info("4.收到客户端删除http channel client seqId {}", seqId);
            Object attachment = selectionKey.attachment();
            if (attachment instanceof HttpClientHandler) {
                remoteChannel.close();
            } else {
                HttpProxyHandler handler = (HttpProxyHandler) attachment;
                ChannelWrapped channelWrapped = handler.getChannelWrapped();
                channelWrapped.channel().close();
                channelWrapped.cumulation().clearAll();
            }
            //最后删除selector
            LOGGER.info("5.收到客户端删除http selector client seqId {}", seqId);
            closeSelector();
        } catch (Exception e) {
            LOGGER.error("5.收到客户端删除http selector失败 client seqId " + seqId, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 是否主动删除
     */
    public void closeSelector() {
        //最后删除selector
        flag = false;
        remoteSelector.wakeup();
    }

    public void remove(Integer seqId) {
        websocketReceive.remove(seqId);
    }
}
