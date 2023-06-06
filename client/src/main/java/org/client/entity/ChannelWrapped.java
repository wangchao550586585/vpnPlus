package org.client.entity;


import org.client.reactor.RecvByteBufAllocator;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.UUID;

public class ChannelWrapped {
    private SelectionKey key;
    private SocketChannel channel;
    private final String uuid;
    private final RecvByteBufAllocator recvByteBufAllocator;
    private final CompositeByteBuf cumulation;

    public ChannelWrapped() {
        this.uuid = UUID.randomUUID().toString().replaceAll("-", "");
        this.recvByteBufAllocator = new RecvByteBufAllocator(uuid);
        this.cumulation = new CompositeByteBuf();
    }

    public static ChannelWrapped builder() {
        return new ChannelWrapped();
    }

    public ChannelWrapped key(SelectionKey key) {
        this.key = key;
        return self();
    }

    public ChannelWrapped channel(SocketChannel channel) {
        this.channel = channel;
        return self();
    }

    private ChannelWrapped self() {
        return this;
    }

    public SelectionKey key() {
        return key;
    }

    public SocketChannel channel() {
        return channel;
    }

    public String uuid() {
        return uuid;
    }

    public RecvByteBufAllocator recvByteBufAllocator() {
        return recvByteBufAllocator;
    }

    public CompositeByteBuf cumulation() {
        return cumulation;
    }
}
