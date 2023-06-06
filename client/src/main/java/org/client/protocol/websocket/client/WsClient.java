package org.client.protocol.websocket.client;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.client.entity.ChannelWrapped;
import org.client.entity.CompositeByteBuf;
import org.client.protocol.websocket.entity.WebsocketFrame;
import org.client.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class WsClient implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    final String host;
    final Integer port;
    Selector remoteSelector;
    SocketChannel remoteChannel;
    volatile boolean flag;
    Map<Integer, SocketChannel> channelMap = new ConcurrentHashMap<>();
    AtomicInteger atomicInteger = new AtomicInteger();

    public static void main(String[] args) {
        new WsClient("127.0.0.1", 8000).connect();
    }

    public WsClient(String host, Integer port) {
        this.host = host;
        this.port = port;
        this.flag = true;
    }

    public WsClient connect() {
        try {
            this.remoteChannel = SocketChannel.open();
            remoteChannel.connect(new InetSocketAddress(host, port));
            LOGGER.debug("remote connect success remoteAddress {} ", remoteChannel.getRemoteAddress());
            remoteChannel.configureBlocking(false);
            while (!remoteChannel.finishConnect()) {
            }
            this.remoteSelector = Selector.open();
            SelectionKey selectionKey = remoteChannel.register(remoteSelector, 0);
            ChannelWrapped channelWrapped = ChannelWrapped.builder().key(selectionKey).channel(remoteChannel);
            Runnable handler = new WsClientHandler(channelWrapped,this);
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
            if (Objects.nonNull(remoteChannel)) {
                try {
                    remoteSelector.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (Objects.nonNull(remoteSelector)) {
                try {
                    remoteSelector.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public int seqId() {
        return atomicInteger.incrementAndGet();
    }

    public void connect(String host, String port, SocketChannel channel, String uuid, int seqId) throws IOException {
        //channel添加到map
        channelMap.put(seqId, channel);
        //序列化值
        Gson gson = new Gson();
        Map<String, Object> map = new HashMap<>();
        map.put("host", host);
        map.put("port", port);
        String value = gson.toJson(map);

        byte[] cmdByte = "connect".getBytes();
        //占用2字节
        byte[] seqIdByte = Utils.int2Byte(seqId);
        byte[] bytes = value.getBytes();
        WebsocketFrame.write(cmdByte, seqIdByte, bytes, uuid,remoteChannel);
    }

    public void write(CompositeByteBuf cumulation, int seqId, String uuid) throws IOException {
        SocketChannel channel = channelMap.get(seqId);
        if (channel != null) {
            byte[] cmdByte = "write".getBytes();
            //占用2字节
            byte[] seqIdByte = Utils.int2Byte(seqId);
            byte[] bytes = cumulation.readAllByte();
            WebsocketFrame.write(cmdByte, seqIdByte, bytes, uuid,remoteChannel);
        }
    }

    public void remove(int seqId) {
        LOGGER.info("该channel关闭 seqId "+seqId);
        channelMap.remove(seqId);
    }
}
