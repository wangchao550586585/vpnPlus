package org.server;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.server.reactor.SelectorStrategy;
import org.server.util.Utils;

import java.net.*;
import java.nio.channels.*;
import java.util.*;

/**
 * Hello world!
 */
public class ServerApp {
    protected final static Logger LOGGER = LogManager.getLogger(ServerApp.class);

    public static void main(String[] args) {
        //int Length = 65535;
        //byte[] bytes = Utils.int2BinaryA2Byte(Length);
        //Length = Utils.binary2Int(bytes);
        //System.out.println(Arrays.toString(bytes));
        //System.out.println(Length);
        //
        //long Length2 = 4294967298L;
        //byte[] bytes2 = Utils.long2BinaryA4Byte(Length2);
        //Length2 = Utils.binary2long(bytes2);
        //System.out.println(Arrays.toString(bytes2));
        //System.out.println(Length2);
        //
        //long l = Utils.byteToLong(Utils.binary2Bytes(bytes2));
        //System.out.println(l);
        //
        //byte Length3 = 2;
        //byte[] bytes3 = Utils.bytes2Binary(Length3);
        //int Length4 = Utils.binary2Int(bytes3);
        //System.out.println(Arrays.toString(bytes3));
        //System.out.println(Length4);
        new ServerApp().vpnStart();
    }

    private void vpnStart() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(8070));
            LOGGER.debug("MasterReactor bind success");
            serverSocketChannel.configureBlocking(false);
            Selector masterReactor = Selector.open();
            LOGGER.debug("MasterReactor selector open success");
            SelectorStrategy selectorStrategy = new SelectorStrategy();
            LOGGER.debug("slaveReactor open Selector all success");
            serverSocketChannel.register(masterReactor, SelectionKey.OP_ACCEPT);
            LOGGER.info("MasterReactor channel register success");
            while (true) {
                int n = masterReactor.select();
                if (n == 0) {
                    continue;
                }
                Set<SelectionKey> selectionKeys = masterReactor.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        SocketChannel childChannel = serverChannel.accept();
                        selectorStrategy.getSlaveReactor().register(childChannel);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("MasterReactor exec fail", e);
            throw new RuntimeException(e);
        }
    }

}
