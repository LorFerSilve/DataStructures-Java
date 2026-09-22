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
 * A double-ended queue backed by its own growable circular {@code Object[]}.
 *
 * <p>Adding at either end takes amortized O(1); accessing or removing either
 * end takes O(1). Searching and removing an occurrence take O(n). Elements
 * cannot be null, so a null returned by a poll or peek means the deque is empty.
 * Capacity grows automatically and shrinks only through {@link #trimToSize()}.
 * Copies and conversions are shallow and preserve first-to-last order.</p>
 *
 * <p>This class implements {@link Iterable}, not {@link java.util.Deque}.
 * Iterators do not support removal. Structural changes invalidate iterators
 * and bound spliterators; capacity-only changes preserve their traversal.
 * Fail-fast checks are best-effort diagnostics, not synchronization: this
 * class is not thread-safe. Equality and hashing use object identity.</p>
 *
 * @param <T> element type
 */
public final class ArrayDeque<T> implements Iterable<T> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    private Object[] data;
    private int head;
    private int size;
    private int modCount;

    // ============================================================
    // Constructors / factories
    // ============================================================

    public ArrayDeque() {
        this(DEFAULT_CAPACITY);
    }

    /** Reserves exactly the given capacity; zero is allowed. */
    public ArrayDeque(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException(
                "Initial capacity cannot be negative: " + initialCapacity
            );
        }
        data = new Object[initialCapacity];
    }

    /** Copies the source in iteration order, rejecting null elements. */
    public ArrayDeque(Iterable<? extends T> values) {
        this();
        Objects.requireNonNull(values, "values");
        for (T value : values) {
            addLast(value);
        }
    }

    @SafeVarargs
    public static <T> ArrayDeque<T> of(T... values) {
        Objects.requireNonNull(values, "values");
        ArrayDeque<T> result = new ArrayDeque<>(values.length);
        for (T value : values) {
            result.addLast(value);
        }
        return result;
    }

    // ============================================================
    // Size / capacity
    // ============================================================

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int capacity() {
        return data.length;
    }

    /** Reduces capacity to size in O(n), preserving existing iterators. */
    public void trimToSize() {
        if (data.length != size) {
            resize(size);
        }
    }

    /** Removes all elements in O(n), retaining capacity for reuse. */
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

    // ============================================================
    // Double-ended queue operations
    // ============================================================

    public void addFirst(T value) {
        Objects.requireNonNull(value, "value");
        ensureRoom();
        head = head == 0 ? data.length - 1 : head - 1;
        data[head] = value;
        size++;
        modCount++;
    }

    public void addLast(T value) {
        Objects.requireNonNull(value, "value");
        ensureRoom();
        data[physicalIndex(size)] = value;
        size++;
        modCount++;
    }

    /** Adds at the front and returns true; this deque has no fixed bound. */
    public boolean offerFirst(T value) {
        addFirst(value);
        return true;
    }

    /** Adds at the back and returns true; this deque has no fixed bound. */
    public boolean offerLast(T value) {
        addLast(value);
        return true;
    }

    /** Removes the first element, throwing if empty. */
    public T removeFirst() {
        requireNonEmpty();
        return pollFirst();
    }

    /** Removes the last element, throwing if empty. */
    public T removeLast() {
        requireNonEmpty();
        return pollLast();
    }

    public T pollFirst() {
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

    public T pollLast() {
        if (size == 0) {
            return null;
        }
        int last = physicalIndex(size - 1);
        T value = elementAt(size - 1);
        data[last] = null;
        if (--size == 0) {
            head = 0;
        }
        modCount++;
        return value;
    }

    /** Returns the first element, throwing if empty. */
    public T getFirst() {
        requireNonEmpty();
        return elementAt(0);
    }

    /** Returns the last element, throwing if empty. */
    public T getLast() {
        requireNonEmpty();
        return elementAt(size - 1);
    }

    public T peekFirst() {
        return size == 0 ? null : elementAt(0);
    }

    public T peekLast() {
        return size == 0 ? null : elementAt(size - 1);
    }

    // ============================================================
    // Queue / stack aliases
    // ============================================================

    /** Queue insertion: appends to the back. */
    public boolean add(T value) {
        return offerLast(value);
    }

    public boolean offer(T value) {
        return offerLast(value);
    }

    public T remove() {
        return removeFirst();
    }

    public T poll() {
        return pollFirst();
    }

    public T element() {
        return getFirst();
    }

    public T peek() {
        return peekFirst();
    }

    /** Stack insertion: pushes onto the front. */
    public void push(T value) {
        addFirst(value);
    }

    public T pop() {
        return removeFirst();
    }

    // ============================================================
    // Search / occurrence removal
    // ============================================================

    /** Returns false for null, which cannot be stored. */
    public boolean contains(Object value) {
        return findOccurrence(value, false) >= 0;
    }

    public boolean removeFirstOccurrence(Object value) {
        int index = findOccurrence(value, false);
        if (index < 0) {
            return false;
        }
        removeAt(index);
        return true;
    }

    public boolean removeLastOccurrence(Object value) {
        int index = findOccurrence(value, true);
        if (index < 0) {
            return false;
        }
        removeAt(index);
        return true;
    }

    /** Removes the first matching occurrence; distinct from queue remove(). */
    public boolean remove(Object value) {
        return removeFirstOccurrence(value);
    }

    // ============================================================
    // Conversion / copying
    // ============================================================

    public Object[] toArray() {
        Object[] result = new Object[size];
        copyElements(result);
        return result;
    }

    public List<T> toDataList() {
        return new List<>(this);
    }

    /** Returns an unmodifiable snapshot, in first-to-last order. */
    public java.util.List<T> toJavaList() {
        ArrayList<T> result = new ArrayList<>(size);
        for (T value : this) {
            result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    /** Returns a shallow independent copy with capacity equal to size. */
    public ArrayDeque<T> copy() {
        ArrayDeque<T> result = new ArrayDeque<>(0);
        result.data = toArray();
        result.size = size;
        return result;
    }

    // ============================================================
    // Iterable / streams
    // ============================================================

    /** First-to-last, fail-fast iteration; remove() is unsupported. */
    @Override
    public Iterator<T> iterator() {
        return new DequeIterator(false);
    }

    /** Last-to-first, fail-fast iteration; remove() is unsupported. */
    public Iterator<T> descendingIterator() {
        return new DequeIterator(true);
    }

    /** Late-binding, ordered and sized traversal without copying elements. */
    @Override
    public Spliterator<T> spliterator() {
        return new DequeSpliterator(0, -1, 0);
    }

    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    public Stream<T> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }

    @Override
    public String toString() {
        return PythonRepr.sequence(this, "ArrayDeque([", "])", "ArrayDeque([...])", false);
    }

    private final class DequeIterator implements Iterator<T> {
        private final boolean descending;
        private final int expectedModCount = modCount;
        private int cursor;

        private DequeIterator(boolean descending) {
            this.descending = descending;
        }

        @Override
        public boolean hasNext() {
            checkModification(expectedModCount);
            return cursor < size;
        }

        @Override
        public T next() {
            checkModification(expectedModCount);
            if (cursor >= size) {
                throw new NoSuchElementException();
            }
            return elementAt(descending ? size - 1 - cursor++ : cursor++);
        }
    }

    private final class DequeSpliterator implements Spliterator<T> {
        private int index;
        private int fence;
        private int expectedModCount;

        private DequeSpliterator(int index, int fence, int expectedModCount) {
            this.index = index;
            this.fence = fence;
            this.expectedModCount = expectedModCount;
        }

        private int getFence() {
            if (fence < 0) {
                expectedModCount = modCount;
                fence = size;
            }
            return fence;
        }

        @Override
        public Spliterator<T> trySplit() {
            int high = getFence();
            checkModification(expectedModCount);
            int low = index;
            int mid = low + (high - low) / 2;
            if (low >= mid) {
                return null;
            }
            index = mid;
            return new DequeSpliterator(low, mid, expectedModCount);
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action, "action");
            int high = getFence();
            checkModification(expectedModCount);
            if (index >= high) {
                return false;
            }
            action.accept(elementAt(index++));
            checkModification(expectedModCount);
            return true;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            Objects.requireNonNull(action, "action");
            int high = getFence();
            checkModification(expectedModCount);
            while (index < high) {
                action.accept(elementAt(index++));
                checkModification(expectedModCount);
            }
        }

        @Override
        public long estimateSize() {
            int high = getFence();
            checkModification(expectedModCount);
            return (long) high - index;
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED
                | Spliterator.SUBSIZED | Spliterator.NONNULL;
        }
    }

    // ============================================================
    // Circular storage helpers
    // ============================================================

    private void requireNonEmpty() {
        if (size == 0) {
            throw new NoSuchElementException("Deque is empty.");
        }
    }

    private void checkModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException("Deque was modified during traversal.");
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

    private int findOccurrence(Object value, boolean descending) {
        if (value == null) {
            return -1;
        }
        int expectedModCount = modCount;
        for (int offset = 0; offset < size; offset++) {
            int index = descending ? size - 1 - offset : offset;
            boolean matches = value.equals(data[physicalIndex(index)]);
            // User-defined equals must not let us remove a different element
            // after a reentrant mutation of this deque.
            checkModification(expectedModCount);
            if (matches) {
                return index;
            }
        }
        return -1;
    }

    /** Shifts the shorter side of the ring to close a single gap. */
    private void removeAt(int index) {
        if (index < size / 2) {
            for (int i = index; i > 0; i--) {
                data[physicalIndex(i)] = data[physicalIndex(i - 1)];
            }
            data[head] = null;
            head = head + 1 == data.length ? 0 : head + 1;
        } else {
            for (int i = index; i < size - 1; i++) {
                data[physicalIndex(i)] = data[physicalIndex(i + 1)];
            }
            data[physicalIndex(size - 1)] = null;
        }
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
            throw new OutOfMemoryError("Deque would be too large.");
        }
        int required = size + 1;
        long preferred = data.length == 0 ? DEFAULT_CAPACITY
            : (long) data.length + Math.max(1, data.length / 2);
        int capacity = (int) Math.max(required, Math.min(preferred, SOFT_MAX_ARRAY_LENGTH));
        // Allocate and copy before publishing the new buffer, so a failed
        // allocation cannot change the deque or its iterators.
        resize(capacity);
    }
}
