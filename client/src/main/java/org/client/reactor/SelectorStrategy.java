package org.client.reactor;

import java.util.concurrent.atomic.AtomicInteger;

public class SelectorStrategy {
    private SlaveReactor[] slaveReactor;
    private AtomicInteger index;
    private static int slaveReactorNum = Runtime.getRuntime().availableProcessors() * 2 + 1;

    public SelectorStrategy() {
        this(slaveReactorNum);
    }

    public SelectorStrategy(int size) {
        slaveReactor = new SlaveReactor[size];
        for (int i = 0; i < slaveReactor.length; i++) {
            slaveReactor[i] = new SlaveReactor();
        }
        index = new AtomicInteger();
    }

    public SlaveReactor getSlaveReactor() {
        int andIncrement = index.getAndIncrement();
        int i = andIncrement % (slaveReactor.length);
        return slaveReactor[i];
    }
}
