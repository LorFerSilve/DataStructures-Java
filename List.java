package DataStructures;

import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A Python-inspired dynamically sized list implementation.
 *
 * <p>The class is backed by an {@code Object[]} and implements its own
 * dynamic-array behaviour rather than wrapping {@link java.util.ArrayList}.
 * Negative indices are supported by index-based operations.</p>
 *
 * @param <T> element type
 */
public class List<T> implements Iterable<T> {

    private static final int DEFAULT_CAPACITY = 10;

    private Object[] data;
    private int size;

    /**
     * Modification counter used by fail-fast iterators.
     */
    private int modCount;

    // ============================================================
    // Constructors / factories
    // ============================================================

    public List() {
        data = new Object[DEFAULT_CAPACITY];
    }

    public List(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException(
                "Initial capacity cannot be negative: " + initialCapacity
            );
        }

        data = new Object[initialCapacity];
    }

    public List(Iterable<? extends T> values) {
        this();
        Objects.requireNonNull(values, "values");

        // Populate directly during construction to avoid calling an
        // overridable public method before construction has completed.
        for (T value : values) {
            ensureCapacity(size + 1);
            data[size++] = value;
        }
    }

    @SafeVarargs
    public static <T> List<T> of(T... values) {
        Objects.requireNonNull(values, "values");

        List<T> result = new List<>(values.length);
        for (T value : values) {
            result.append(value);
        }
        return result;
    }

    public static <T> List<T> fromJavaList(java.util.List<? extends T> javaList) {
        Objects.requireNonNull(javaList, "javaList");
        return new List<>(javaList);
    }

    // ============================================================
    // Basic operations
    // ============================================================

    /**
     * Python equivalent: {@code list.append(value)}.
     * Amortized O(1).
     */
    public void append(T value) {
        ensureCapacity(size + 1);
        data[size++] = value;
        modCount++;
    }

    /**
     * Python equivalent: {@code list.extend(iterable)}.
     */
    public void extend(Iterable<? extends T> values) {
        Objects.requireNonNull(values, "values");

        if (values == this) {
            values = copy();
        }

        for (T value : values) {
            append(value);
        }
    }

    /**
     * Python equivalent: {@code list.insert(index, value)}.
     * Out-of-range indices are clamped, as in Python.
     */
    public void insert(int index, T value) {
        int normalizedIndex = normalizeInsertIndex(index);

        ensureCapacity(size + 1);
        System.arraycopy(
            data,
            normalizedIndex,
            data,
            normalizedIndex + 1,
            size - normalizedIndex
        );

        data[normalizedIndex] = value;
        size++;
        modCount++;
    }

    /**
     * Python equivalent: {@code list[index]}.
     */
    public T get(int index) {
        return elementAt(normalizeIndex(index));
    }

    /**
     * Python equivalent: {@code list[index] = value}.
     *
     * @return previous value at the index
     */
    public T set(int index, T value) {
        int normalizedIndex = normalizeIndex(index);
        T oldValue = elementAt(normalizedIndex);
        data[normalizedIndex] = value;
        return oldValue;
    }

    // ============================================================
    // Removal
    // ============================================================

    /**
     * Python equivalent: {@code list.remove(value)}.
     */
    public void remove(T value) {
        int index = indexOf(value);

        if (index < 0) {
            throw new NoSuchElementException("Value not found in list: " + value);
        }

        removeAt(index);
    }

    public T removeAt(int index) {
        int normalizedIndex = normalizeIndex(index);
        T removedValue = elementAt(normalizedIndex);

        int elementsToMove = size - normalizedIndex - 1;
        if (elementsToMove > 0) {
            System.arraycopy(
                data,
                normalizedIndex + 1,
                data,
                normalizedIndex,
                elementsToMove
            );
        }

        data[--size] = null;
        modCount++;
        return removedValue;
    }

    /**
     * Python equivalent: {@code list.pop()}.
     */
    public T pop() {
        return pop(-1);
    }

    /**
     * Python equivalent: {@code list.pop(index)}.
     */
    public T pop(int index) {
        if (isEmpty()) {
            throw new NoSuchElementException("Cannot pop from an empty list.");
        }
        return removeAt(index);
    }

    /**
     * Python equivalent: {@code list.clear()}.
     */
    public void clear() {
        if (size == 0) {
            return;
        }

        Arrays.fill(data, 0, size, null);
        size = 0;
        modCount++;
    }

    /**
     * Python equivalent: {@code del list[start:end]}.
     */
    public void removeSlice(int start, int end) {
        int from = normalizeBound(start);
        int to = normalizeBound(end);

        if (to <= from) {
            return;
        }

        int removedCount = to - from;
        int tailLength = size - to;

        if (tailLength > 0) {
            System.arraycopy(data, to, data, from, tailLength);
        }

        int newSize = size - removedCount;
        Arrays.fill(data, newSize, size, null);
        size = newSize;
        modCount++;
    }

    /**
     * Python equivalent: {@code del list[start:end:step]}.
     * Remaining elements retain their original order.
     */
    public void removeSlice(int start, int end, int step) {
        if (step == 0) {
            throw new IllegalArgumentException("Slice step cannot be zero.");
        }
        if (step == 1) {
            removeSlice(start, end);
            return;
        }

        int from = normalizeSliceBound(start, step);
        int to = normalizeSliceBound(end, step);
        int removedCount = calculateSliceLength(from, to, step);
        if (removedCount == 0) {
            return;
        }

        // Visit removed indices in ascending order so compaction takes O(n)
        // time, including when the requested slice runs backwards.
        long nextRemoved = step > 0
            ? from
            : from + (long) (removedCount - 1) * step;
        long distance = Math.abs((long) step);
        int writeIndex = 0;
        int remaining = removedCount;
        for (int readIndex = 0; readIndex < size; readIndex++) {
            if (remaining > 0 && readIndex == nextRemoved) {
                nextRemoved += distance;
                remaining--;
            } else {
                data[writeIndex++] = data[readIndex];
            }
        }

        Arrays.fill(data, writeIndex, size, null);
        size = writeIndex;
        modCount++;
    }

    /**
     * Python equivalent: {@code list[start:end] = values} for a unit-step slice.
     * The replacement may have a different length from the removed slice.
     */
    public void setSlice(int start, int end, Iterable<? extends T> values) {
        Objects.requireNonNull(values, "values");

        // Snapshot before mutating this list. This also makes setSlice(..., this) safe.
        List<T> replacement = new List<>();
        for (T value : values) {
            replacement.append(value);
        }

        int from = normalizeBound(start);
        int to = normalizeBound(end);

        // For a forward unit-step slice, stop < start denotes an empty slice
        // inserted at start.
        if (to < from) {
            to = from;
        }

        int removedCount = to - from;
        int replacementCount = replacement.size;
        int newSize = checkedSize((long) size - removedCount + replacementCount);

        ensureCapacity(newSize);

        int tailLength = size - to;
        int newTailStart = from + replacementCount;

        if (tailLength > 0 && newTailStart != to) {
            System.arraycopy(data, to, data, newTailStart, tailLength);
        }

        for (int i = 0; i < replacementCount; i++) {
            data[from + i] = replacement.data[i];
        }

        if (newSize < size) {
            Arrays.fill(data, newSize, size, null);
        }

        if (removedCount != 0 || replacementCount != 0) {
            size = newSize;
            modCount++;
        }
    }

    /**
     * Python equivalent: {@code list[start:end:step] = values}.
     * For a non-unit step, the replacement must have exactly the slice's
     * length. Values are snapshotted before assignment, allowing self-assignment.
     */
    public void setSlice(int start, int end, int step, Iterable<? extends T> values) {
        if (step == 0) {
            throw new IllegalArgumentException("Slice step cannot be zero.");
        }
        if (step == 1) {
            setSlice(start, end, values);
            return;
        }

        Objects.requireNonNull(values, "values");
        List<T> replacement = new List<>(values);
        int from = normalizeSliceBound(start, step);
        int to = normalizeSliceBound(end, step);
        int replacementCount = calculateSliceLength(from, to, step);
        if (replacement.size != replacementCount) {
            throw new IllegalArgumentException(
                "Replacement size " + replacement.size
                    + " does not match extended slice size " + replacementCount
            );
        }

        long index = from;
        for (int i = 0; i < replacementCount; i++, index += step) {
            data[(int) index] = replacement.data[i];
        }
        if (replacementCount != 0) {
            modCount++;
        }
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

    public void trimToSize() {
        if (data.length != size) {
            data = Arrays.copyOf(data, size);
        }
    }

    // ============================================================
    // Search
    // ============================================================

    public boolean contains(T value) {
        return indexOf(value) >= 0;
    }

    /**
     * Java-style search: returns -1 when absent.
     */
    public int indexOf(T value) {
        for (int i = 0; i < size; i++) {
            if (Objects.equals(data[i], value)) {
                return i;
            }
        }
        return -1;
    }

    public int lastIndexOf(T value) {
        for (int i = size - 1; i >= 0; i--) {
            if (Objects.equals(data[i], value)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Python equivalent: {@code list.index(value)}.
     */
    public int index(T value) {
        return index(value, 0, size);
    }

    /**
     * Python equivalent: {@code list.index(value, start)}.
     */
    public int index(T value, int start) {
        return index(value, start, size);
    }

    /**
     * Python equivalent: {@code list.index(value, start, end)}.
     */
    public int index(T value, int start, int end) {
        int normalizedStart = normalizeBound(start);
        int normalizedEnd = normalizeBound(end);

        for (int i = normalizedStart; i < normalizedEnd; i++) {
            if (Objects.equals(data[i], value)) {
                return i;
            }
        }

        throw new NoSuchElementException("Value not found in list: " + value);
    }

    public int count(T value) {
        int occurrences = 0;
        for (int i = 0; i < size; i++) {
            if (Objects.equals(data[i], value)) {
                occurrences++;
            }
        }
        return occurrences;
    }

    // ============================================================
    // Reverse / copy
    // ============================================================

    public void reverse() {
        int left = 0;
        int right = size - 1;

        while (left < right) {
            Object temp = data[left];
            data[left] = data[right];
            data[right] = temp;
            left++;
            right--;
        }

        if (size > 1) {
            modCount++;
        }
    }

    /**
     * Shallow copy, like Python's {@code list.copy()}.
     */
    public List<T> copy() {
        List<T> result = new List<>(size);
        result.data = Arrays.copyOf(data, size);
        result.size = size;
        return result;
    }

    // ============================================================
    // Sorting
    // ============================================================

    /**
     * Python equivalent: {@code list.sort()}.
     */
    public void sort() {
        sort(naturalComparator(), false);
    }

    public void sort(Comparator<? super T> comparator) {
        sort(comparator, false);
    }

    /**
     * Similar to Python's {@code list.sort(reverse=...)}.
     * Sorting is stable. If comparison fails, this method leaves the original
     * order intact. Comparators must not modify this list.
     */
    public void sort(Comparator<? super T> comparator, boolean reverse) {
        Objects.requireNonNull(comparator, "comparator");

        // Do not negate comparator results: -Integer.MIN_VALUE overflows.
        Comparator<? super T> actualComparator =
            reverse ? comparator.reversed() : comparator;

        Comparator<Object> objectComparator = (left, right) ->
            actualComparator.compare(cast(left), cast(right));

        int expectedModCount = modCount;
        Object[] sorted = Arrays.copyOf(data, size);
        Arrays.sort(sorted, objectComparator);
        checkSortModification(expectedModCount);
        System.arraycopy(sorted, 0, data, 0, sorted.length);

        if (sorted.length > 1) {
            modCount++;
        }
    }

    /**
     * Python-style key sorting:
     * {@code list.sort(key=lambda value: ...)}.
     */
    public <K extends Comparable<? super K>> void sort(
        Function<? super T, ? extends K> key
    ) {
        sort(key, false);
    }

    /**
     * Python-style key sorting with reverse ordering.
     * The key is evaluated exactly once per element, in the original order.
     * Equal keys retain their original order, including when reversed.
     * Key functions must not modify this list. If key evaluation or comparison
     * fails, this method leaves the original order intact.
     */
    public <K extends Comparable<? super K>> void sort(
        Function<? super T, ? extends K> key,
        boolean reverse
    ) {
        Objects.requireNonNull(key, "key");
        int expectedModCount = modCount;
        Object[] snapshot = Arrays.copyOf(data, size);

        // The array is private to this invocation and only receives entries
        // with these exact type parameters.
        @SuppressWarnings("unchecked")
        KeyedValue<T, K>[] entries =
            (KeyedValue<T, K>[]) new KeyedValue<?, ?>[snapshot.length];
        for (int i = 0; i < snapshot.length; i++) {
            T value = cast(snapshot[i]);
            K sortKey = key.apply(value);
            checkSortModification(expectedModCount);
            entries[i] = new KeyedValue<>(value, sortKey);
        }

        Arrays.sort(entries, (left, right) -> reverse
            ? right.key.compareTo(left.key)
            : left.key.compareTo(right.key));
        checkSortModification(expectedModCount);
        for (int i = 0; i < entries.length; i++) {
            data[i] = entries[i].value;
        }
        if (entries.length > 1) {
            modCount++;
        }
    }

    /**
     * Python equivalent: {@code sorted(list)}.
     */
    public List<T> sorted() {
        List<T> result = copy();
        result.sort();
        return result;
    }

    public List<T> sorted(Comparator<? super T> comparator) {
        List<T> result = copy();
        result.sort(comparator);
        return result;
    }

    public List<T> sorted(Comparator<? super T> comparator, boolean reverse) {
        List<T> result = copy();
        result.sort(comparator, reverse);
        return result;
    }

    public <K extends Comparable<? super K>> List<T> sorted(
        Function<? super T, ? extends K> key
    ) {
        List<T> result = copy();
        result.sort(key);
        return result;
    }

    public <K extends Comparable<? super K>> List<T> sorted(
        Function<? super T, ? extends K> key,
        boolean reverse
    ) {
        List<T> result = copy();
        result.sort(key, reverse);
        return result;
    }

    // ============================================================
    // Python-style slicing
    // ============================================================

    /**
     * Python equivalent: {@code list[start:end]}.
     */
    public List<T> slice(int start, int end) {
        return slice(start, end, 1);
    }

    /**
     * Python equivalent: {@code list[start:end:step]}.
     */
    public List<T> slice(int start, int end, int step) {
        return sliceInternal(start, end, step);
    }

    /** Python equivalent: {@code list[start:]}. */
    public List<T> sliceFrom(int start) {
        return sliceInternal(start, null, 1);
    }

    /** Python equivalent: {@code list[start::step]}. */
    public List<T> sliceFrom(int start, int step) {
        return sliceInternal(start, null, step);
    }

    /** Python equivalent: {@code list[:end]}. */
    public List<T> sliceTo(int end) {
        return sliceInternal(null, end, 1);
    }

    /** Python equivalent: {@code list[:end:step]}. */
    public List<T> sliceTo(int end, int step) {
        return sliceInternal(null, end, step);
    }

    /** Python equivalent: {@code list[:]}. Returns an independent shallow copy. */
    public List<T> sliceAll() {
        return copy();
    }

    /** Python equivalent: {@code list[::step]}. */
    public List<T> sliceAll(int step) {
        return sliceInternal(null, null, step);
    }

    // ============================================================
    // Python-like operators
    // ============================================================

    /**
     * Java equivalent of Python's {@code left + right} for lists.
     */
    public List<T> concat(Iterable<? extends T> other) {
        Objects.requireNonNull(other, "other");
        List<T> result = copy();
        result.extend(other);
        return result;
    }

    /**
     * Java equivalent of Python's {@code list * times}.
     */
    public List<T> repeat(int times) {
        if (times <= 0 || size == 0) {
            return new List<>(0);
        }

        if (size > Integer.MAX_VALUE / times) {
            throw new OutOfMemoryError("Repeated list would be too large.");
        }

        List<T> result = new List<>(size * times);

        for (int repetition = 0; repetition < times; repetition++) {
            for (int i = 0; i < size; i++) {
                result.append(elementAt(i));
            }
        }

        return result;
    }

    // ============================================================
    // Conversion / Java interoperability
    // ============================================================

    public Object[] toArray() {
        return Arrays.copyOf(data, size);
    }

    /**
     * Exposes this list through Java's Stream API without copying it.
     *
     * <p>The custom late-binding spliterator reports the exact size and the
     * {@link Spliterator#ORDERED}, {@link Spliterator#SIZED} and
     * {@link Spliterator#SUBSIZED} characteristics. This lets stream pipelines
     * optimize size-preserving operations such as {@code count()} while still
     * providing fail-fast traversal if the list is structurally modified.</p>
     */
    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    // ============================================================
    // Iterable / Spliterator
    // ============================================================

    /**
     * Returns a late-binding, ordered and sized spliterator.
     *
     * <p>Late binding means the list's size and modification count are captured
     * only when traversal or size inspection actually begins. This mirrors the
     * behaviour of standard Java collection spliterators more closely than
     * eagerly snapshotting {@code size} when {@code stream()} is called.</p>
     */
    @Override
    public Spliterator<T> spliterator() {
        return new ListSpliterator(0, -1, 0);
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int cursor;
            private final int expectedModCount = modCount;

            @Override
            public boolean hasNext() {
                checkForConcurrentModification();
                return cursor < size;
            }

            @Override
            public T next() {
                checkForConcurrentModification();

                if (cursor >= size) {
                    throw new NoSuchElementException();
                }

                return elementAt(cursor++);
            }

            private void checkForConcurrentModification() {
                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException(
                        "List was modified while iterating."
                    );
                }
            }
        };
    }

    /**
     * Spliterator used by {@link #stream()} and parallel stream pipelines.
     */
    private final class ListSpliterator implements Spliterator<T> {
        private int index;
        private int fence;
        private int expectedModCount;

        private ListSpliterator(int origin, int fence, int expectedModCount) {
            this.index = origin;
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
            int low = index;
            int mid = (low + high) >>> 1;

            if (low >= mid) {
                return null;
            }

            index = mid;
            return new ListSpliterator(low, mid, expectedModCount);
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action, "action");

            int high = getFence();
            if (index >= high) {
                return false;
            }

            checkForConcurrentModification();
            T value = elementAt(index++);
            action.accept(value);
            checkForConcurrentModification();
            return true;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            Objects.requireNonNull(action, "action");

            int high = getFence();
            while (index < high) {
                checkForConcurrentModification();
                action.accept(elementAt(index++));
            }
            checkForConcurrentModification();
        }

        @Override
        public long estimateSize() {
            return (long) getFence() - index;
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED
                | Spliterator.SIZED
                | Spliterator.SUBSIZED;
        }

        private void checkForConcurrentModification() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException(
                    "List was modified while traversing its spliterator."
                );
            }
        }
    }

    // ============================================================
    // Object contract
    // ============================================================

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof List<?> other)) {
            return false;
        }

        if (size != other.size) {
            return false;
        }

        for (int i = 0; i < size; i++) {
            if (!Objects.equals(data[i], other.data[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (int i = 0; i < size; i++) {
            hash = 31 * hash + Objects.hashCode(data[i]);
        }
        return hash;
    }

    @Override
    public String toString() {
        return PythonRepr.sequence(this, "[", "]", "[...]", false);
    }

    // ============================================================
    // Private helpers
    // ============================================================

    private static final class KeyedValue<V, K extends Comparable<? super K>> {
        private final V value;
        private final K key;

        private KeyedValue(V value, K key) {
            this.value = value;
            this.key = key;
        }
    }

    private void checkSortModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException("List was modified while sorting.");
        }
    }

    /** A null bound represents an omitted Python slice boundary. */
    private List<T> sliceInternal(Integer start, Integer end, int step) {
        if (step == 0) {
            throw new IllegalArgumentException("Slice step cannot be zero.");
        }

        int from = start == null
            ? (step > 0 ? 0 : size - 1)
            : normalizeSliceBound(start, step);
        int to = end == null
            ? (step > 0 ? size : -1)
            : normalizeSliceBound(end, step);
        int resultSize = calculateSliceLength(from, to, step);
        List<T> result = new List<>(resultSize);
        long index = from;
        for (int i = 0; i < resultSize; i++, index += step) {
            result.data[i] = data[(int) index];
        }
        result.size = resultSize;
        return result;
    }

    /** Calculates slice length using long arithmetic for extreme int steps. */
    private static int calculateSliceLength(int start, int end, int step) {
        long distance = step > 0 ? (long) end - start : (long) start - end;
        if (distance <= 0) {
            return 0;
        }
        long stride = Math.abs((long) step);
        return (int) (1 + (distance - 1) / stride);
    }

    private static int checkedSize(long requestedSize) {
        if (requestedSize > Integer.MAX_VALUE) {
            throw new OutOfMemoryError("List would be too large.");
        }
        return (int) requestedSize;
    }

    private int normalizeIndex(int index) {
        int normalizedIndex = index < 0 ? size + index : index;

        if (normalizedIndex < 0 || normalizedIndex >= size) {
            throw new IndexOutOfBoundsException(
                "Index " + index + " out of bounds for list of size " + size
            );
        }

        return normalizedIndex;
    }

    /**
     * Python insert() clamps instead of throwing.
     */
    private int normalizeInsertIndex(int index) {
        if (index < 0) {
            return Math.max(0, size + index);
        }
        return Math.min(index, size);
    }

    /**
     * Normalizes a forward-slice/search boundary to [0, size].
     * Shared by index ranges, forward slices, removeSlice and setSlice.
     */
    private int normalizeBound(int index) {
        int normalized = index < 0 ? size + index : index;
        return Math.max(0, Math.min(normalized, size));
    }

    /**
     * Normalizes an explicit boundary. With a negative step, an explicit -1
     * means the last element, whereas an omitted end means before the first.
     */
    private int normalizeSliceBound(int index, int step) {
        if (step > 0) {
            return normalizeBound(index);
        }
        int normalized = index < 0 ? size + index : index;

        if (normalized < 0) {
            return -1;
        }

        return Math.min(normalized, size - 1);
    }

    /**
     * Dynamic-array growth using approximately a 1.5x growth factor.
     */
    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= data.length) {
            return;
        }

        int grownCapacity;
        if (data.length == 0) {
            grownCapacity = DEFAULT_CAPACITY;
        } else {
            int growth = data.length >> 1;
            grownCapacity = data.length + Math.max(1, growth);
        }

        int newCapacity = Math.max(grownCapacity, requiredCapacity);
        data = Arrays.copyOf(data, newCapacity);
    }

    @SuppressWarnings("unchecked")
    private T elementAt(int index) {
        return (T) data[index];
    }

    @SuppressWarnings("unchecked")
    private T cast(Object value) {
        return (T) value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Comparator<T> naturalComparator() {
        return (left, right) -> ((Comparable) left).compareTo(right);
    }
}
