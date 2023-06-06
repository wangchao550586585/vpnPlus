package org.client.protocol.websocket.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.client.entity.ChannelWrapped;
import org.client.protocol.websocket.entity.WebsocketFrame;
import org.client.util.Utils;

import java.io.IOException;

import static org.client.protocol.websocket.entity.WebsocketFrame.DEFAULT_HAS_MASK;

public class Heartbeat implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    private final ChannelWrapped channelWrapped;
    private volatile int num;

    Heartbeat(ChannelWrapped channelWrapped) {
        this.channelWrapped = channelWrapped;
        num = 0;
    }

    public void num(int num) {
        this.num = num;
    }

    public int num() {
        return num;
    }

    @Override
    public void run() {
        try {
            String uuid = channelWrapped.uuid();
            LOGGER.info("ping {} ", uuid);
            WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.PING, DEFAULT_HAS_MASK, null, null, Utils.buildMask(), null, this.channelWrapped.channel(), uuid);
            num++;
            //15秒没连接上，则退出。
/*            if (num >= 3) {
                channelWrapped.channel().close();
                System.exit(0);
            }*/
        } catch (IOException e) {
            LOGGER.error("ping error ", e);
            throw new RuntimeException(e);
        }
    }
}