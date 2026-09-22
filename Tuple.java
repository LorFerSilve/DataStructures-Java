package DataStructures;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Immutable, Python-style tuple.
 *
 * <p>A Tuple has a fixed size, can contain heterogeneous values, supports
 * negative indexing and Python-style slicing, and uses structural equality
 * and hashing. The tuple structure itself is immutable; contained objects
 * are not defensively deep-copied.</p>
 *
 * <pre>{@code
 * Tuple tuple = Tuple.of("Lorenzo", 4, Tuple.of(3, 1), 2.5);
 * }</pre>
 */
public final class Tuple implements Iterable<Object> {

    private static final Object[] EMPTY_ARRAY = new Object[0];

    /** Shared canonical empty tuple. */
    public static final Tuple EMPTY = new Tuple(EMPTY_ARRAY, true);

    private final Object[] data;

    // ============================================================
    // Constructors / factories
    // ============================================================

    /** Creates an empty tuple: {@code ()}. */
    public Tuple() {
        this.data = EMPTY_ARRAY;
    }

    /**
     * Creates a tuple containing the supplied values.
     * The input array is defensively copied.
     */
    public Tuple(Object... values) {
        Objects.requireNonNull(values, "values");
        this.data = values.length == 0
            ? EMPTY_ARRAY
            : Arrays.copyOf(values, values.length);
    }

    /** Internal constructor for arrays that are already exclusively owned. */
    private Tuple(Object[] values, boolean trusted) {
        this.data = values.length == 0
            ? EMPTY_ARRAY
            : (trusted ? values : Arrays.copyOf(values, values.length));
    }

    /** Convenient factory method. */
    public static Tuple of(Object... values) {
        Objects.requireNonNull(values, "values");
        return values.length == 0 ? EMPTY : new Tuple(values);
    }

    /**
     * Creates a tuple from any Iterable.
     * If the Iterable is already a Tuple, that same immutable instance is returned.
     */
    public static Tuple from(Iterable<?> values) {
        Objects.requireNonNull(values, "values");

        if (values instanceof Tuple tuple) {
            return tuple;
        }

        Object[] collected = collect(values);
        return collected.length == 0 ? EMPTY : new Tuple(collected, true);
    }

    /** Creates a tuple from a standard Java List. */
    public static Tuple fromJavaList(java.util.List<?> values) {
        Objects.requireNonNull(values, "values");

        if (values.isEmpty()) {
            return EMPTY;
        }

        return new Tuple(values.toArray(), true);
    }

    // ============================================================
    // Basic access
    // ============================================================

    /**
     * Returns the element at the specified index.
     * Negative indices follow Python semantics.
     */
    public Object get(int index) {
        return data[normalizeIndex(index)];
    }

    /**
     * Typed convenience getter.
     *
     * @throws ClassCastException if a non-null value is not of the requested type
     */
    public <T> T get(int index, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = get(index);
        return value == null ? null : type.cast(value);
    }

    /** Returns the first element. */
    public Object first() {
        if (isEmpty()) {
            throw new NoSuchElementException("Empty tuple has no first element.");
        }

        return data[0];
    }

    /** Returns the final element. */
    public Object last() {
        if (isEmpty()) {
            throw new NoSuchElementException("Empty tuple has no last element.");
        }

        return data[data.length - 1];
    }

    // ============================================================
    // Size
    // ============================================================

    /** Equivalent to Python's {@code len(tuple)}. */
    public int size() {
        return data.length;
    }

    public boolean isEmpty() {
        return data.length == 0;
    }

    // ============================================================
    // Searching
    // ============================================================

    /** Equivalent to Python's {@code value in tuple}. */
    public boolean contains(Object value) {
        return indexOf(value) >= 0;
    }

    /** Java-style search. Returns {@code -1} when absent. */
    public int indexOf(Object value) {
        for (int i = 0; i < data.length; i++) {
            if (Objects.equals(data[i], value)) {
                return i;
            }
        }

        return -1;
    }

    /** Returns the last occurrence, or {@code -1} when absent. */
    public int lastIndexOf(Object value) {
        for (int i = data.length - 1; i >= 0; i--) {
            if (Objects.equals(data[i], value)) {
                return i;
            }
        }

        return -1;
    }

    /** Python-style {@code tuple.index(value)}. */
    public int index(Object value) {
        return index(value, 0, data.length);
    }

    /** Python-style {@code tuple.index(value, start)}. */
    public int index(Object value, int start) {
        return index(value, start, data.length);
    }

    /** Python-style {@code tuple.index(value, start, end)}. */
    public int index(Object value, int start, int end) {
        int normalizedStart = normalizeBound(start);
        int normalizedEnd = normalizeBound(end);

        for (int i = normalizedStart; i < normalizedEnd; i++) {
            if (Objects.equals(data[i], value)) {
                return i;
            }
        }

        throw new NoSuchElementException("Value not found in tuple: " + value);
    }

    /** Equivalent to Python's {@code tuple.count(value)}. */
    public int count(Object value) {
        int occurrences = 0;

        for (Object element : data) {
            if (Objects.equals(element, value)) {
                occurrences++;
            }
        }

        return occurrences;
    }

    // ============================================================
    // Slicing
    // ============================================================

    /** Python equivalent: {@code tuple[start:end]}. */
    public Tuple slice(int start, int end) {
        return sliceInternal(start, end, 1);
    }

    /** Python equivalent: {@code tuple[start:end:step]}. */
    public Tuple slice(int start, int end, int step) {
        return sliceInternal(start, end, step);
    }

    /** Python equivalent: {@code tuple[start:]}. */
    public Tuple sliceFrom(int start) {
        return sliceInternal(start, null, 1);
    }

    /** Python equivalent: {@code tuple[start::step]}. */
    public Tuple sliceFrom(int start, int step) {
        return sliceInternal(start, null, step);
    }

    /** Python equivalent: {@code tuple[:end]}. */
    public Tuple sliceTo(int end) {
        return sliceInternal(null, end, 1);
    }

    /** Python equivalent: {@code tuple[:end:step]}. */
    public Tuple sliceTo(int end, int step) {
        return sliceInternal(null, end, step);
    }

    /** Python equivalent: {@code tuple[:]}. */
    public Tuple sliceAll() {
        return this;
    }

    /** Python equivalent: {@code tuple[::step]}. */
    public Tuple sliceAll(int step) {
        return sliceInternal(null, null, step);
    }

    // ============================================================
    // Tuple operations
    // ============================================================

    /** Java equivalent of Python's {@code tupleA + tupleB}. */
    public Tuple concat(Tuple other) {
        Objects.requireNonNull(other, "other");

        if (other.isEmpty()) {
            return this;
        }

        if (isEmpty()) {
            return other;
        }

        int newLength = checkedCombinedLength(data.length, other.data.length);
        Object[] result = Arrays.copyOf(data, newLength);
        System.arraycopy(other.data, 0, result, data.length, other.data.length);

        return new Tuple(result, true);
    }

    /**
     * Java-friendly overload that appends all values from an Iterable.
     * Python itself only permits tuple + tuple.
     */
    public Tuple concat(Iterable<?> other) {
        Objects.requireNonNull(other, "other");
        return concat(Tuple.from(other));
    }

    /** Java equivalent of Python's {@code tuple * n}. */
    public Tuple repeat(int times) {
        if (times <= 0 || isEmpty()) {
            return EMPTY;
        }

        if (times == 1) {
            return this;
        }

        if (data.length > Integer.MAX_VALUE / times) {
            throw new OutOfMemoryError("Repeated tuple would be too large.");
        }

        Object[] result = new Object[data.length * times];
        for (int repetition = 0; repetition < times; repetition++) {
            System.arraycopy(data, 0, result, repetition * data.length, data.length);
        }

        return new Tuple(result, true);
    }

    /** Returns a tuple with reversed element order. */
    public Tuple reversed() {
        return sliceAll(-1);
    }

    // ============================================================
    // Sorting
    // ============================================================

    /**
     * Python-style {@code sorted(tuple)}.
     * Returns the custom mutable DataStructures.List, as Python sorted() returns a list.
     */
    public List<Object> sorted() {
        return toDataList().sorted();
    }

    /** Sorts into a new custom List using a Java Comparator. */
    public List<Object> sorted(Comparator<? super Object> comparator) {
        Objects.requireNonNull(comparator, "comparator");
        return toDataList().sorted(comparator);
    }

    /** Sorts into a new custom List using a Comparator and optional reverse order. */
    public List<Object> sorted(Comparator<? super Object> comparator, boolean reverse) {
        Objects.requireNonNull(comparator, "comparator");
        return toDataList().sorted(comparator, reverse);
    }

    /** Python-style {@code sorted(tuple, key=...)}. */
    public <K extends Comparable<? super K>> List<Object> sorted(
        Function<? super Object, ? extends K> key
    ) {
        Objects.requireNonNull(key, "key");
        return toDataList().sorted(key);
    }

    /** Python-style {@code sorted(tuple, key=..., reverse=...)}. */
    public <K extends Comparable<? super K>> List<Object> sorted(
        Function<? super Object, ? extends K> key,
        boolean reverse
    ) {
        Objects.requireNonNull(key, "key");
        return toDataList().sorted(key, reverse);
    }

    // ============================================================
    // Conversion
    // ============================================================

    /** Returns a defensive copy of the tuple contents. */
    public Object[] toArray() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * Converts this Tuple to the custom mutable {@code DataStructures.List<Object>}.
     * The name makes the intentional dependency on the custom List explicit.
     */
    public List<Object> toDataList() {
        List<Object> result = new List<>(data.length);

        for (Object value : data) {
            result.append(value);
        }

        return result;
    }

    /**
     * Converts this Tuple to an unmodifiable standard Java List.
     * Null values are preserved.
     */
    public java.util.List<Object> toJavaList() {
        return Collections.unmodifiableList(Arrays.asList(toArray()));
    }

    // ============================================================
    // Streams / iteration
    // ============================================================

    /** Returns an ordered, sized sequential stream. */
    public Stream<Object> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /** Returns an ordered, sized parallel stream. */
    public Stream<Object> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }

    /** Allows enhanced-for iteration. */
    @Override
    public Iterator<Object> iterator() {
        return new Iterator<>() {
            private int cursor;

            @Override
            public boolean hasNext() {
                return cursor < data.length;
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                return data[cursor++];
            }
        };
    }

    /**
     * Provides an efficient exact-size Spliterator.
     * Tuple's structure is immutable, so IMMUTABLE is valid.
     */
    @Override
    public Spliterator<Object> spliterator() {
        return Spliterators.spliterator(
            data,
            Spliterator.ORDERED
                | Spliterator.SIZED
                | Spliterator.SUBSIZED
                | Spliterator.IMMUTABLE
        );
    }

    // ============================================================
    // Equality / hashing
    // ============================================================

    /** Structural equality, analogous to Python tuple equality. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Tuple other)) {
            return false;
        }

        return Arrays.equals(data, other.data);
    }

    /**
     * Structural hash code.
     * Mutable contained objects should not change equals/hashCode-relevant state
     * while this Tuple is used as a hash-based key.
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    // ============================================================
    // String representation
    // ============================================================

    /**
     * Python-like representation, including the trailing comma for one-element tuples.
     */
    @Override
    public String toString() {
        return PythonRepr.sequence(this, "(", ")", "(...)", true);
    }

    // ============================================================
    // Private helpers
    // ============================================================

    /**
     * Central implementation for all slicing methods.
     * A null start/end represents an omitted Python slice boundary.
     */
    private Tuple sliceInternal(Integer start, Integer end, int step) {
        if (step == 0) {
            throw new IllegalArgumentException("Slice step cannot be zero.");
        }

        int from = normalizeSliceStart(start, step);
        int to = normalizeSliceEnd(end, step);
        int resultLength = calculateSliceLength(from, to, step);

        if (resultLength == 0) {
            return EMPTY;
        }

        if (step == 1 && from == 0 && to == data.length) {
            return this;
        }

        Object[] result = new Object[resultLength];
        int sourceIndex = from;

        for (int i = 0; i < resultLength; i++) {
            result[i] = data[sourceIndex];
            sourceIndex += step;
        }

        return new Tuple(result, true);
    }

    /** Normalizes a Python-style element index. */
    private int normalizeIndex(int index) {
        int normalized = index < 0 ? data.length + index : index;

        if (normalized < 0 || normalized >= data.length) {
            throw new IndexOutOfBoundsException(
                "Index " + index + " out of bounds for tuple of size " + data.length
            );
        }

        return normalized;
    }

    /** Normalizes index(value, start, end) bounds to [0, size]. */
    private int normalizeBound(int index) {
        int normalized = index < 0 ? data.length + index : index;
        return Math.max(0, Math.min(normalized, data.length));
    }

    private int normalizeSliceStart(Integer start, int step) {
        if (start == null) {
            return step > 0 ? 0 : data.length - 1;
        }

        return normalizeExplicitSliceBound(start, step);
    }

    private int normalizeSliceEnd(Integer end, int step) {
        if (end == null) {
            return step > 0 ? data.length : -1;
        }

        return normalizeExplicitSliceBound(end, step);
    }

    /**
     * Normalizes an explicitly supplied Python slice boundary.
     * Omitted boundaries are represented by null because, for negative steps,
     * an omitted end is semantically different from an explicit -1.
     */
    private int normalizeExplicitSliceBound(int index, int step) {
        int normalized = index;

        if (normalized < 0) {
            normalized += data.length;
        }

        if (step > 0) {
            return normalized < 0 ? 0 : Math.min(normalized, data.length);
        }

        return normalized < 0 ? -1 : Math.min(normalized, data.length - 1);
    }

    /** Calculates the exact size of an already-normalized slice. */
    private static int calculateSliceLength(int start, int end, int step) {
        if (step > 0) {
            if (start >= end) {
                return 0;
            }

            long distance = (long) end - start;
            return (int) ((distance + step - 1L) / step);
        }

        if (start <= end) {
            return 0;
        }

        long positiveStep = -(long) step;
        long distance = (long) start - end;
        return (int) ((distance + positiveStep - 1L) / positiveStep);
    }

    /**
     * Collects any Iterable into a compact Object[] without an ArrayList dependency.
     */
    private static Object[] collect(Iterable<?> values) {
        Object[] buffer = new Object[8];
        int size = 0;

        for (Object value : values) {
            if (size == buffer.length) {
                int newCapacity = buffer.length + (buffer.length >> 1) + 1;
                buffer = Arrays.copyOf(buffer, newCapacity);
            }

            buffer[size++] = value;
        }

        return size == 0 ? EMPTY_ARRAY : Arrays.copyOf(buffer, size);
    }

    /** Checks for integer overflow before concatenation. */
    private static int checkedCombinedLength(int left, int right) {
        long combined = (long) left + right;

        if (combined > Integer.MAX_VALUE) {
            throw new OutOfMemoryError("Concatenated tuple would be too large.");
        }

        return (int) combined;
    }
}
