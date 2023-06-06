package org.client.reactor;

import java.nio.channels.SelectionKey;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 用数组实现set集合
 */
public class SelectedSelectionKeySet extends AbstractSet<SelectionKey> {
    SelectionKey[] keys;
    int size;

    public SelectedSelectionKeySet() {
        keys = new SelectionKey[1024];
    }

    @Override
    public boolean add(SelectionKey selectionKey) {
        if (selectionKey == null) {
            return false;
        }
        keys[size++] = selectionKey;
        if (size == keys.length) {
            increaseCapacity();
        }
        return true;
    }

    private void increaseCapacity() {
        SelectionKey[] arr = new SelectionKey[keys.length << 1];
        System.arraycopy(keys, 0, arr, 0, size);
        keys = arr;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Iterator<SelectionKey> iterator() {
        return new Iterator<SelectionKey>() {
            private int idx;

            @Override
            public boolean hasNext() {
                return idx < size;
            }

            @Override
            public SelectionKey next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return keys[idx++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public void reset() {
        Arrays.fill(keys, 0, size, null);
        size = 0;
    }
}
