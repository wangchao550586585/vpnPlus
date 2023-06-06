package org.server.protocol.http.entity;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Resource {
    SocketChannel remoteChannel;
    Selector remoteSelector;

    private Resource self() {
        return this;
    }

    public SocketChannel remoteChannel() {
        return remoteChannel;
    }

    public Resource remoteChannel(SocketChannel remoteChannel) {
        this.remoteChannel = remoteChannel;
        return self();
    }

    public Resource remoteSelector(Selector remoteSelector) {
        this.remoteSelector = remoteSelector;
        return self();
    }

    public void closeRemote() throws IOException {
        remoteChannel.close();
        //close调用会调用wakeup
        remoteSelector.close();
    }

}
