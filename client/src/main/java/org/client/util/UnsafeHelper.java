package org.client.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.client.reactor.SlaveReactor;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class UnsafeHelper {
    private final static Logger LOGGER = LogManager.getLogger(SlaveReactor.class);
    private final static Unsafe UNSAFE;

    static {
        UNSAFE = AccessController.doPrivileged((PrivilegedAction<Unsafe>) () -> {
            try {
                final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(null);
            } catch (Exception e) {
                LOGGER.warn("get unsafe fail", e);
            }
            return null;
        });
    }

    public static void putObject(Object o, long offset, Object x) {
        UNSAFE.putObject(o, offset, x);
    }

    public static long objectFieldOffset(Field f) {
        return UNSAFE.objectFieldOffset(f);
    }

    public static boolean hasUnsafe() {
        return null != UNSAFE;
    }
}
