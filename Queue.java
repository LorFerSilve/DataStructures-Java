package DataStructures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A FIFO queue backed by its own growable circular {@code Object[]}.
 *
 * <p>Insertion at the back and removal from the front are amortized O(1).
 * Elements cannot be null, so {@link #poll()} and {@link #peek()} can return
 * null unambiguously when the queue is empty. Searching and removing a matching
 * value are O(n). Capacity grows geometrically and shrinks only through
 * {@link #trimToSize()}.</p>
 *
 * <p>This class implements {@link Iterable}, not {@link java.util.Queue}.
 * Iteration, snapshots and the string representation run from front to back.
 * Iterators and spliterators are fail-fast on a best-effort basis and do not
 * support removal. Equality and hashing use object identity.</p>
 *
 * @param <T> element type
 */
public final class Queue<T> implements Iterable<T> {
    private static final int DEFAULT_CAPACITY = 16;
    private static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    private Object[] data;
    private int head;
    private int size;
    private int modCount;

    public Queue() {
        this(DEFAULT_CAPACITY);
    }

    /** Reserves exactly the requested capacity; zero is allowed. */
    public Queue(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException(
                "Initial capacity cannot be negative: " + initialCapacity
            );
        }
        data = new Object[initialCapacity];
    }

    /** Copies values in iteration order, rejecting null elements. */
    public Queue(Iterable<? extends T> values) {
        this();
        Objects.requireNonNull(values, "values");
        for (T value : values) {
            add(value);
        }
        modCount = 0;
    }

    @SafeVarargs
    public static <T> Queue<T> of(T... values) {
        Objects.requireNonNull(values, "values");
        Queue<T> result = new Queue<>(values.length);
        for (T value : values) {
            result.add(value);
        }
        result.modCount = 0;
        return result;
    }

    /** Adds to the back and returns true; this queue has no fixed bound. */
    public boolean add(T value) {
        return offer(value);
    }

    /** Adds to the back and returns true; this queue has no fixed bound. */
    public boolean offer(T value) {
        Objects.requireNonNull(value, "value");
        ensureRoom();
        data[physicalIndex(size)] = value;
        size++;
        modCount++;
        return true;
    }

    /** Removes and returns the front element, throwing when empty. */
    public T remove() {
        requireNonEmpty();
        return poll();
    }

    /** Removes and returns the front element, or null when empty. */
    public T poll() {
        if (size == 0) {
            return null;
        }
        T value = elementAt(0);
        data[head] = null;
        head = head + 1 == data.length ? 0 : head + 1;
        if (--size == 0) {
            head = 0;
        }
        modCount++;
        return value;
    }

    /** Returns the front element without removing it, throwing when empty. */
    public T element() {
        requireNonEmpty();
        return elementAt(0);
    }

    /** Returns the front element without removing it, or null when empty. */
    public T peek() {
        return size == 0 ? null : elementAt(0);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int capacity() {
        return data.length;
    }

    /** Removes all elements while retaining allocated capacity. */
    public void clear() {
        if (size == 0) {
            return;
        }
        int firstPart = Math.min(size, data.length - head);
        Arrays.fill(data, head, head + firstPart, null);
        Arrays.fill(data, 0, size - firstPart, null);
        head = 0;
        size = 0;
        modCount++;
    }

    /** Reduces capacity to size without invalidating existing iterators. */
    public void trimToSize() {
        if (data.length != size) {
            resize(size);
        }
    }

    /** Returns false for null, which cannot be stored. */
    public boolean contains(Object value) {
        return indexOf(value) >= 0;
    }

    /** Removes the first equal value, or returns false when absent. */
    public boolean remove(Object value) {
        int index = indexOf(value);
        if (index < 0) {
            return false;
        }
        removeAt(index);
        return true;
    }

    /** Returns an independent shallow copy with capacity equal to size. */
    public Queue<T> copy() {
        Queue<T> result = new Queue<>(0);
        result.data = toArray();
        result.size = size;
        return result;
    }

    /** Returns a front-to-back shallow array snapshot. */
    public Object[] toArray() {
        Object[] result = new Object[size];
        copyElements(result);
        return result;
    }

    /** Returns a front-to-back custom-list snapshot. */
    public List<T> toDataList() {
        return new List<>(this);
    }

    /** Returns an unmodifiable front-to-back Java-list snapshot. */
    public java.util.List<T> toJavaList() {
        ArrayList<T> result = new ArrayList<>(size);
        for (T value : this) {
            result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    public Stream<T> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }

    /** Iterates from front to back. */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int cursor;
            private final int expectedModCount = modCount;

            @Override
            public boolean hasNext() {
                checkForModification(expectedModCount);
                return cursor < size;
            }

            @Override
            public T next() {
                checkForModification(expectedModCount);
                if (cursor >= size) {
                    throw new NoSuchElementException();
                }
                return elementAt(cursor++);
            }
        };
    }

    /** Returns a late-binding, ordered, sized and non-null spliterator. */
    @Override
    public Spliterator<T> spliterator() {
        return new QueueSpliterator(0, -1, 0);
    }

    @Override
    public String toString() {
        return PythonRepr.sequence(this, "Queue([", "])", "Queue([...])", false);
    }

    private final class QueueSpliterator implements Spliterator<T> {
        private int index;
        private int fence;
        private int expectedModCount;

        private QueueSpliterator(int index, int fence, int expectedModCount) {
            this.index = index;
            this.fence = fence;
            this.expectedModCount = expectedModCount;
        }

        private int getFence() {
            if (fence < 0) {
                fence = size;
                expectedModCount = modCount;
            }
            return fence;
        }

        @Override
        public Spliterator<T> trySplit() {
            int high = getFence();
            checkForModification(expectedModCount);
            int low = index;
            int mid = low + (high - low) / 2;
            if (low >= mid) {
                return null;
            }
            index = mid;
            return new QueueSpliterator(low, mid, expectedModCount);
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action, "action");
            int high = getFence();
            checkForModification(expectedModCount);
            if (index >= high) {
                return false;
            }
            action.accept(elementAt(index++));
            checkForModification(expectedModCount);
            return true;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            Objects.requireNonNull(action, "action");
            int high = getFence();
            checkForModification(expectedModCount);
            while (index < high) {
                action.accept(elementAt(index++));
                checkForModification(expectedModCount);
            }
        }

        @Override
        public long estimateSize() {
            int high = getFence();
            checkForModification(expectedModCount);
            return (long) high - index;
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED
                | Spliterator.SUBSIZED | Spliterator.NONNULL;
        }
    }

    private void requireNonEmpty() {
        if (size == 0) {
            throw new NoSuchElementException("Queue is empty.");
        }
    }

    private void checkForModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException("Queue was modified during traversal.");
        }
    }

    /** Maps a logical index without overflowing head + index. */
    private int physicalIndex(int index) {
        int remaining = data.length - head;
        return index >= remaining ? index - remaining : head + index;
    }

    @SuppressWarnings("unchecked")
    private T elementAt(int index) {
        return (T) data[physicalIndex(index)];
    }

    private int indexOf(Object value) {
        if (value == null) {
            return -1;
        }
        int expectedModCount = modCount;
        for (int index = 0; index < size; index++) {
            boolean equal = value.equals(data[physicalIndex(index)]);
            checkForModification(expectedModCount);
            if (equal) {
                return index;
            }
        }
        return -1;
    }

    /** Closes an interior gap by shifting later queue elements toward the front. */
    private void removeAt(int index) {
        for (int current = index; current < size - 1; current++) {
            data[physicalIndex(current)] = data[physicalIndex(current + 1)];
        }
        data[physicalIndex(size - 1)] = null;
        if (--size == 0) {
            head = 0;
        }
        modCount++;
    }

    private void copyElements(Object[] target) {
        int firstPart = Math.min(size, data.length - head);
        System.arraycopy(data, head, target, 0, firstPart);
        System.arraycopy(data, 0, target, firstPart, size - firstPart);
    }

    private void resize(int capacity) {
        Object[] replacement = new Object[capacity];
        copyElements(replacement);
        data = replacement;
        head = 0;
    }

    private void ensureRoom() {
        if (size < data.length) {
            return;
        }
        if (size == Integer.MAX_VALUE) {
            throw new OutOfMemoryError("Queue would be too large.");
        }
        int required = size + 1;
        long preferred = data.length == 0 ? DEFAULT_CAPACITY
            : (long) data.length + Math.max(1, data.length / 2);
        int capacity = (int) Math.max(required, Math.min(preferred, SOFT_MAX_ARRAY_LENGTH));
        resize(capacity);
    }
}
