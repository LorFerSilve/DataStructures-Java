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
 * A LIFO stack backed by its own growable {@code Object[]}.
 *
 * <p>{@link #push(Object)} is amortized O(1); {@link #pop()} and
 * {@link #peek()} are O(1). Null elements are allowed because empty-stack
 * operations throw instead of using null as an empty sentinel. Iteration,
 * conversions and the representation run from the bottom of the stack to the
 * top, while {@link #search(Object)} counts from the top using one-based
 * positions, matching {@link java.util.Stack#search(Object)}.</p>
 *
 * <p>This class is not thread-safe. Iterators and spliterators are fail-fast
 * on a best-effort basis and do not support removal. Equality and hashing use
 * object identity, so mutating a stack never changes its identity hash.</p>
 *
 * @param <T> element type
 */
public final class Stack<T> implements Iterable<T> {
    private static final int DEFAULT_CAPACITY = 10;

    private Object[] data;
    private int size;
    private int modCount;

    public Stack() {
        this(DEFAULT_CAPACITY);
    }

    /** Reserves exactly the requested capacity; zero is allowed. */
    public Stack(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException(
                "Initial capacity cannot be negative: " + initialCapacity
            );
        }
        data = new Object[initialCapacity];
    }

    /** Copies values in iteration order; the last copied value becomes the top. */
    public Stack(Iterable<? extends T> values) {
        this();
        Objects.requireNonNull(values, "values");
        for (T value : values) {
            push(value);
        }
        modCount = 0;
    }

    @SafeVarargs
    public static <T> Stack<T> of(T... values) {
        Objects.requireNonNull(values, "values");
        Stack<T> result = new Stack<>(values.length);
        for (T value : values) {
            result.push(value);
        }
        result.modCount = 0;
        return result;
    }

    /** Pushes one value and returns it, matching the conventional stack API. */
    public T push(T value) {
        if (size == Integer.MAX_VALUE) {
            throw new OutOfMemoryError("Stack would be too large.");
        }
        ensureCapacity(size + 1);
        data[size++] = value;
        modCount++;
        return value;
    }

    /** Removes and returns the top value. */
    public T pop() {
        requireNonEmpty();
        int top = --size;
        T value = elementAt(top);
        data[top] = null;
        modCount++;
        return value;
    }

    /** Returns the top value without removing it. */
    public T peek() {
        requireNonEmpty();
        return elementAt(size - 1);
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

    /** Removes all values while retaining the allocated capacity. */
    public void clear() {
        if (size == 0) {
            return;
        }
        Arrays.fill(data, 0, size, null);
        size = 0;
        modCount++;
    }

    /** Releases unused capacity without invalidating existing iterators. */
    public void trimToSize() {
        if (data.length != size) {
            data = Arrays.copyOf(data, size);
        }
    }

    public boolean contains(Object value) {
        return search(value) >= 0;
    }

    /**
     * Returns the one-based distance from the top, or -1 when absent.
     * When duplicates exist, the occurrence nearest the top is returned.
     */
    public int search(Object value) {
        for (int index = size - 1, distance = 1; index >= 0; index--, distance++) {
            if (Objects.equals(data[index], value)) {
                return distance;
            }
        }
        return -1;
    }

    /** Returns an independent shallow copy with the same storage order. */
    public Stack<T> copy() {
        Stack<T> result = new Stack<>(0);
        result.data = Arrays.copyOf(data, size);
        result.size = size;
        return result;
    }

    /** Returns a bottom-to-top shallow array snapshot. */
    public Object[] toArray() {
        return Arrays.copyOf(data, size);
    }

    /** Returns a bottom-to-top custom-list snapshot. */
    public List<T> toDataList() {
        return new List<>(this);
    }

    /** Returns an unmodifiable bottom-to-top Java-list snapshot. */
    public java.util.List<T> toJavaList() {
        java.util.List<T> result = new ArrayList<>(size);
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

    /** Iterates from bottom to top. */
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

    /** Returns a late-binding, ordered and sized spliterator. */
    @Override
    public Spliterator<T> spliterator() {
        return new StackSpliterator(0, -1, 0);
    }

    @Override
    public String toString() {
        return PythonRepr.sequence(this, "Stack([", "])", "Stack([...])", false);
    }

    private final class StackSpliterator implements Spliterator<T> {
        private int index;
        private int fence;
        private int expectedModCount;

        private StackSpliterator(int index, int fence, int expectedModCount) {
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
            int mid = (low + high) >>> 1;
            if (low >= mid) {
                return null;
            }
            index = mid;
            return new StackSpliterator(low, mid, expectedModCount);
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
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

    private void requireNonEmpty() {
        if (size == 0) {
            throw new NoSuchElementException("Stack is empty.");
        }
    }

    private void checkForModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException("Stack was modified during traversal.");
        }
    }

    private void ensureCapacity(int minimum) {
        if (minimum <= data.length) {
            return;
        }
        long grown = data.length == 0
            ? DEFAULT_CAPACITY
            : (long) data.length + Math.max(1L, data.length / 2L);
        int capacity = (int) Math.min(Integer.MAX_VALUE, Math.max(grown, minimum));
        data = Arrays.copyOf(data, capacity);
    }

    @SuppressWarnings("unchecked")
    private T elementAt(int index) {
        return (T) data[index];
    }
}
