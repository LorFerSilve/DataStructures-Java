package DataStructures;

import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An array-backed binary min-heap with optional comparator ordering.
 *
 * <p>The smallest element according to the comparator is at the head. Use
 * {@link Comparator#reverseOrder()} for a max-heap. Null elements are rejected,
 * duplicates are allowed, and equal priorities have no stable removal order.
 * Natural ordering requires mutually comparable elements.</p>
 *
 * <p>Insertion and head removal take O(log n) comparisons; array growth makes
 * insertion amortized O(log n). Head inspection is O(1), equality-based search
 * and removal are O(n), and iterable construction uses O(n) bottom-up heapify.
 * Iteration, conversions and the representation expose arbitrary heap storage
 * order, not priority order. Use {@link #sorted()} for priority order.</p>
 *
 * <p>Elements must not change their ordering while stored in the heap. This
 * class is not thread-safe. Iterators are fail-fast on a best-effort basis and
 * do not support removal. Equality and hashing use object identity, as with
 * {@link java.util.PriorityQueue}.</p>
 *
 * @param <T> element type
 */
public final class BinaryHeap<T> implements Iterable<T> {
    private static final int DEFAULT_CAPACITY = 10;

    private Object[] data;
    private int size;
    private int modCount;
    private final Comparator<? super T> comparator;

    public BinaryHeap() {
        this(DEFAULT_CAPACITY, null);
    }

    public BinaryHeap(int initialCapacity) {
        this(initialCapacity, null);
    }

    /** A null comparator selects natural ordering. */
    public BinaryHeap(Comparator<? super T> comparator) {
        this(DEFAULT_CAPACITY, comparator);
    }

    /** A zero initial capacity is allowed; a negative capacity is rejected. */
    public BinaryHeap(int initialCapacity, Comparator<? super T> comparator) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException(
                "Initial capacity cannot be negative: " + initialCapacity);
        }
        this.data = new Object[initialCapacity];
        this.comparator = comparator;
    }

    /** Copies the values and builds their heap in O(n) time. */
    public BinaryHeap(Iterable<? extends T> values) {
        this(values, null);
    }

    /** Copies the values and builds their heap in O(n) time. */
    public BinaryHeap(Iterable<? extends T> values, Comparator<? super T> comparator) {
        this(DEFAULT_CAPACITY, comparator);
        Objects.requireNonNull(values, "values");
        for (T value : values) {
            requireElement(value);
            ensureCapacity(size + 1);
            data[size++] = value;
        }
        for (int index = (size >>> 1) - 1; index >= 0; index--) {
            // Construction has not published this heap, so it is safe to
            // move each value directly while heapifying its subtree.
            T value = elementAt(index);
            int hole = index;
            while (hole < (size >>> 1)) {
                int child = (hole << 1) + 1;
                if (child + 1 < size
                    && compare(elementAt(child + 1), elementAt(child), 0) < 0) {
                    child++;
                }
                if (compare(value, elementAt(child), 0) <= 0) {
                    break;
                }
                data[hole] = data[child];
                hole = child;
            }
            data[hole] = value;
        }
    }

    @SafeVarargs
    public static <T> BinaryHeap<T> of(T... values) {
        Objects.requireNonNull(values, "values");
        List<T> input = new List<>(values.length);
        for (T value : values) {
            input.append(value);
        }
        return new BinaryHeap<>(input);
    }

    /**
     * Adds one element. If comparison fails, this operation makes no changes.
     * Changes made by a comparator callback itself are retained and detected.
     */
    public boolean add(T value) {
        requireElement(value);
        int expectedModCount = modCount;
        int destination = insertionPosition(size, value, expectedModCount);
        ensureCapacity(size + 1);
        int hole = size;
        while (hole > destination) {
            int parent = (hole - 1) >>> 1;
            data[hole] = data[parent];
            hole = parent;
        }
        data[destination] = value;
        size++;
        modCount++;
        return true;
    }

    public boolean offer(T value) {
        return add(value);
    }

    /** Returns the highest-priority element, or null when empty. */
    public T peek() {
        return size == 0 ? null : elementAt(0);
    }

    /** Returns the highest-priority element, or throws when empty. */
    public T element() {
        if (size == 0) {
            throw new NoSuchElementException("Heap is empty.");
        }
        return elementAt(0);
    }

    /** Removes the highest-priority element, or returns null when empty. */
    public T poll() {
        if (size == 0) {
            return null;
        }
        return removeAt(0);
    }

    /** Removes the highest-priority element, or throws when empty. */
    public T remove() {
        if (size == 0) {
            throw new NoSuchElementException("Heap is empty.");
        }
        return removeAt(0);
    }

    /** Tests equality, independently of the priority comparator. */
    public boolean contains(Object value) {
        return indexOf(value) >= 0;
    }

    /** Removes one equal occurrence; returns false for null or an absent value. */
    public boolean remove(Object value) {
        int index = indexOf(value);
        if (index < 0) {
            return false;
        }
        removeAt(index);
        return true;
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

    /** Releases unused array slots without invalidating existing iterators. */
    public void trimToSize() {
        if (data.length != size) {
            data = Arrays.copyOf(data, size);
        }
    }

    /** Removes all values while retaining capacity. Clearing an empty heap is a no-op. */
    public void clear() {
        if (size != 0) {
            Arrays.fill(data, 0, size, null);
            size = 0;
            modCount++;
        }
    }

    /** Returns the comparator, or null when natural ordering is used. */
    public Comparator<? super T> comparator() {
        return comparator;
    }

    /** Returns an independent shallow copy, preserving comparator and storage order. */
    public BinaryHeap<T> copy() {
        BinaryHeap<T> result = new BinaryHeap<>(0, comparator);
        result.data = Arrays.copyOf(data, size);
        result.size = size;
        return result;
    }

    public Object[] toArray() {
        return Arrays.copyOf(data, size);
    }

    public List<T> toDataList() {
        return new List<>(this);
    }

    /** Returns an unmodifiable shallow snapshot in arbitrary heap storage order. */
    public java.util.List<T> toJavaList() {
        java.util.List<T> result = new java.util.ArrayList<>(size);
        for (T value : this) {
            result.add(value);
        }
        return java.util.Collections.unmodifiableList(result);
    }

    /** Returns a shallow list in priority order in O(n log n), preserving this heap. */
    public List<T> sorted() {
        BinaryHeap<T> remaining = copy();
        List<T> result = new List<>(size);
        while (!remaining.isEmpty()) {
            result.append(remaining.remove());
        }
        return result;
    }

    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    public Stream<T> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }

    /** Iterates in arbitrary heap order; iterator.remove() is unsupported. */
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

    /** Returns a late-binding, sized spliterator without an encounter-order guarantee. */
    @Override
    public Spliterator<T> spliterator() {
        return new HeapSpliterator(0, -1, 0);
    }

    @Override
    public String toString() {
        return PythonRepr.sequence(this, "BinaryHeap([", "])", "BinaryHeap([...])", false);
    }

    private final class HeapSpliterator implements Spliterator<T> {
        private int index;
        private int fence;
        private int expectedModCount;

        private HeapSpliterator(int index, int fence, int expectedModCount) {
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
            return new HeapSpliterator(low, mid, expectedModCount);
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
            return Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL;
        }
    }

    private int indexOf(Object value) {
        if (value == null) {
            return -1;
        }
        int expectedModCount = modCount;
        for (int index = 0; index < size; index++) {
            boolean equal = value.equals(data[index]);
            checkForModification(expectedModCount);
            if (equal) {
                return index;
            }
        }
        return -1;
    }

    private T removeAt(int index) {
        T removed = elementAt(index);
        int last = size - 1;
        if (index == last) {
            data[last] = null;
            size--;
            modCount++;
            return removed;
        }

        T replacement = elementAt(last);
        int expectedModCount = modCount;
        int destination = insertionPosition(index, replacement, expectedModCount);
        if (destination < index) {
            // All comparisons have succeeded before any existing slot moves.
            int hole = index;
            while (hole > destination) {
                int parent = (hole - 1) >>> 1;
                data[hole] = data[parent];
                hole = parent;
            }
            data[destination] = replacement;
        } else {
            // At most 30 child edges exist in an int-indexed array. Record the
            // chosen path so a later comparator failure cannot corrupt the heap.
            int[] path = new int[32];
            int length = 0;
            int hole = index;
            while (hole < (last >>> 1)) {
                int child = (hole << 1) + 1;
                if (child + 1 < last
                    && compare(elementAt(child + 1), elementAt(child), expectedModCount) < 0) {
                    child++;
                }
                if (compare(replacement, elementAt(child), expectedModCount) <= 0) {
                    break;
                }
                path[length++] = child;
                hole = child;
            }
            hole = index;
            for (int step = 0; step < length; step++) {
                int child = path[step];
                data[hole] = data[child];
                hole = child;
            }
            data[hole] = replacement;
        }
        data[last] = null;
        size--;
        modCount++;
        return removed;
    }

    private int insertionPosition(int index, T value, int expectedModCount) {
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (compare(value, elementAt(parent), expectedModCount) >= 0) {
                break;
            }
            index = parent;
        }
        return index;
    }

    private void requireElement(T value) {
        Objects.requireNonNull(value, "value");
        if (comparator == null && !(value instanceof Comparable<?>)) {
            throw new ClassCastException("Natural ordering requires Comparable elements.");
        }
    }

    @SuppressWarnings("unchecked")
    private T elementAt(int index) {
        return (T) data[index];
    }

    @SuppressWarnings("unchecked")
    private int compare(T left, T right, int expectedModCount) {
        int result = comparator == null
            ? ((Comparable<? super T>) left).compareTo(right)
            : comparator.compare(left, right);
        checkForModification(expectedModCount);
        return result;
    }

    private void checkForModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException("Heap was modified during an operation.");
        }
    }

    private void ensureCapacity(int minimum) {
        if (minimum < 0) {
            throw new OutOfMemoryError("Required heap capacity is too large.");
        }
        if (minimum > data.length) {
            long grown = data.length + Math.max(1L, data.length / 2L);
            int capacity = (int) Math.min(Integer.MAX_VALUE, Math.max(grown, minimum));
            data = Arrays.copyOf(data, capacity);
        }
    }
}
