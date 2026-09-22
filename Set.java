package DataStructures;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Mutable, Python-style hash set implemented with open addressing.
 *
 * <p>The implementation does not wrap {@code java.util.HashSet}. Elements are
 * stored directly in an internal hash table using linear probing and tombstones.
 * Duplicate values are rejected according to {@link Objects#equals(Object, Object)}
 * and {@link Object#hashCode()}.</p>
 *
 * <p>As with any hash-based collection, elements must keep a stable
 * {@code equals/hashCode} state while they are stored in the set. In particular,
 * this mutable {@code Set} cannot itself be stored inside another instance of
 * {@code DataStructures.Set}; use an immutable set type for that use case.</p>
 *
 * @param <T> element type
 */
public final class Set<T> implements Iterable<T> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final int MAX_CAPACITY = 1 << 30;
    private static final double LOAD_FACTOR = 0.65;
    private static final double SHRINK_FACTOR = 0.20;

    private static final Object EMPTY_SLOT = new Object();
    private static final Object DELETED_SLOT = new Object();

    private static final int SPLITERATOR_CHARACTERISTICS =
        Spliterator.DISTINCT | Spliterator.SIZED | Spliterator.SUBSIZED;

    private Object[] table;
    private int size;
    private int usedSlots;
    private int modCount;


    // ============================================================
    // Constructors / factories
    // ============================================================

    /** Creates an empty set. */
    public Set() {
        table = newTable(DEFAULT_CAPACITY);
    }

    /**
     * Creates an empty set sized for approximately {@code expectedSize}
     * elements before the first resize.
     */
    public Set(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException(
                "Expected size cannot be negative: " + expectedSize
            );
        }

        table = newTable(capacityForExpectedSize(expectedSize));
    }

    /** Creates a set containing all unique values from an Iterable. */
    public Set(Iterable<? extends T> values) {
        Objects.requireNonNull(values, "values");
        table = newTable(DEFAULT_CAPACITY);

        for (T value : values) {
            add(value);
        }
    }

    /** Convenient factory method. Duplicate values are automatically removed. */
    @SafeVarargs
    public static <T> Set<T> of(T... values) {
        Objects.requireNonNull(values, "values");

        Set<T> result = new Set<>(values.length);
        for (T value : values) {
            result.add(value);
        }
        return result;
    }

    /** Creates a custom Set from a standard Java set. */
    public static <T> Set<T> fromJavaSet(java.util.Set<? extends T> values) {
        Objects.requireNonNull(values, "values");
        return new Set<>(values);
    }


    // ============================================================
    // Basic mutation
    // ============================================================

    /**
     * Python equivalent: {@code set.add(value)}.
     *
     * @return true if the set changed, false if the value already existed
     */
    public boolean add(T value) {
        validateElement(value);
        int hash = spreadHash(value);

        // Check for duplicates before any possible rehash. A duplicate add is
        // not a structural modification and therefore must not rearrange the
        // internal table behind an active iterator.
        if (findIndex(value, hash) >= 0) {
            return false;
        }

        ensureCapacityForInsert();

        int mask = table.length - 1;
        int index = hash & mask;
        int firstDeleted = -1;

        while (true) {
            Object slot = table[index];

            if (slot == EMPTY_SLOT) {
                int insertionIndex = firstDeleted >= 0 ? firstDeleted : index;
                if (firstDeleted < 0) {
                    usedSlots++;
                }

                table[insertionIndex] = value;
                size++;
                modCount++;
                return true;
            }

            if (slot == DELETED_SLOT && firstDeleted < 0) {
                firstDeleted = index;
            }

            index = (index + 1) & mask;
        }
    }

    /**
     * Python equivalent: {@code set.remove(value)}.
     * Throws if the value is absent.
     */
    public void remove(Object value) {
        int index = findIndex(value);
        if (index < 0) {
            throw new NoSuchElementException("Value not found in set: " + value);
        }

        removeAtIndex(index);
    }

    /**
     * Python equivalent: {@code set.discard(value)}.
     * Does not throw if the value is absent.
     *
     * @return true if an element was removed
     */
    public boolean discard(Object value) {
        int index = findIndex(value);
        if (index < 0) {
            return false;
        }

        removeAtIndex(index);
        return true;
    }

    /**
     * Python equivalent: {@code set.pop()}.
     * Removes and returns an arbitrary element.
     */
    public T pop() {
        if (isEmpty()) {
            throw new NoSuchElementException("Cannot pop from an empty set.");
        }

        for (int i = 0; i < table.length; i++) {
            if (isOccupied(table[i])) {
                T value = elementAt(i);
                removeAtIndex(i);
                return value;
            }
        }

        throw new IllegalStateException("Set state is inconsistent.");
    }

    /** Removes all elements. */
    public void clear() {
        if (size == 0) {
            return;
        }

        table = newTable(DEFAULT_CAPACITY);
        size = 0;
        usedSlots = 0;
        modCount++;
    }

    /**
     * Python equivalent: {@code set.update(iterable)}.
     */
    public void update(Iterable<? extends T> values) {
        Objects.requireNonNull(values, "values");

        if (values == this) {
            return; // A | A = A
        }

        for (T value : values) {
            add(value);
        }
    }

    /**
     * Python equivalent: {@code set.update(*others)}.
     * Accepts zero or more iterables.
     */
    @SafeVarargs
    public final void update(Iterable<? extends T>... others) {
        Objects.requireNonNull(others, "others");

        for (int i = 0; i < others.length; i++) {
            Iterable<? extends T> other = Objects.requireNonNull(
                others[i], "others[" + i + "]"
            );
            update(other);
        }
    }


    // ============================================================
    // Size / capacity
    // ============================================================

    /** Equivalent to Python {@code len(set)}. */
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Returns the current internal hash-table capacity. */
    public int capacity() {
        return table.length;
    }


    // ============================================================
    // Membership
    // ============================================================

    /** Equivalent to Python {@code value in set}. */
    public boolean contains(Object value) {
        return findIndex(value) >= 0;
    }


    // ============================================================
    // Non-mutating set operations
    // ============================================================

    /** Returns a shallow copy. */
    public Set<T> copy() {
        Set<T> result = new Set<>(0);
        result.table = Arrays.copyOf(table, table.length);
        result.size = size;
        result.usedSlots = usedSlots;
        return result;
    }

    /** Python equivalent: {@code self | other}. */
    public Set<T> union(Iterable<? extends T> other) {
        Objects.requireNonNull(other, "other");

        Set<T> result = copy();
        result.update(other);
        return result;
    }

    /** Python equivalent: {@code self.union(*others)}. */
    @SafeVarargs
    public final Set<T> union(Iterable<? extends T>... others) {
        Objects.requireNonNull(others, "others");

        Set<T> result = copy();
        result.update(others);
        return result;
    }

    /** Python equivalent: {@code self & other}. */
    public Set<T> intersection(Iterable<?> other) {
        Objects.requireNonNull(other, "other");

        Set<?> membership = asMembershipSet(other);
        Set<T> result = new Set<>(Math.min(size, membership.size()));

        for (T value : this) {
            if (membership.contains(value)) {
                result.add(value);
            }
        }

        return result;
    }

    /** Python equivalent: {@code self.intersection(*others)}. */
    public final Set<T> intersection(Iterable<?>... others) {
        Objects.requireNonNull(others, "others");

        Set<T> result = copy();
        result.intersectionUpdate(others);
        return result;
    }

    /** Python equivalent: {@code self - other}. */
    public Set<T> difference(Iterable<?> other) {
        Objects.requireNonNull(other, "other");

        Set<?> membership = asMembershipSet(other);
        Set<T> result = new Set<>(size);

        for (T value : this) {
            if (!membership.contains(value)) {
                result.add(value);
            }
        }

        return result;
    }

    /** Python equivalent: {@code self.difference(*others)}. */
    public final Set<T> difference(Iterable<?>... others) {
        Objects.requireNonNull(others, "others");

        Set<T> result = copy();
        result.differenceUpdate(others);
        return result;
    }

    /** Python equivalent: {@code self ^ other}. */
    public Set<T> symmetricDifference(Iterable<? extends T> other) {
        Objects.requireNonNull(other, "other");

        Set<T> otherSet = new Set<>(other);
        Set<T> result = copy();

        for (T value : otherSet) {
            if (!result.discard(value)) {
                result.add(value);
            }
        }

        return result;
    }


    // ============================================================
    // Mutating set operations
    // ============================================================

    /** Python equivalent: {@code self.intersection_update(other)}. */
    public void intersectionUpdate(Iterable<?> other) {
        Objects.requireNonNull(other, "other");

        if (other == this) {
            return;
        }

        Set<?> membership = asMembershipSet(other);
        boolean changed = false;

        for (int i = 0; i < table.length; i++) {
            Object slot = table[i];
            if (isOccupied(slot) && !membership.contains(slot)) {
                if (!changed) {
                    // A later hashCode/equals callback may throw. Iterators must
                    // still notice any removals that have already completed.
                    modCount++;
                    changed = true;
                }
                table[i] = DELETED_SLOT;
                size--;
            }
        }

        if (changed) {
            compactAfterBulkRemoval();
        }
    }

    /** Python equivalent: {@code self.intersection_update(*others)}. */
    public final void intersectionUpdate(Iterable<?>... others) {
        Objects.requireNonNull(others, "others");

        for (int i = 0; i < others.length; i++) {
            Iterable<?> other = Objects.requireNonNull(
                others[i], "others[" + i + "]"
            );
            intersectionUpdate(other);

            if (isEmpty()) {
                return;
            }
        }
    }

    /** Python equivalent: {@code self.difference_update(other)}. */
    public void differenceUpdate(Iterable<?> other) {
        Objects.requireNonNull(other, "other");

        if (other == this) {
            clear();
            return;
        }

        Set<?> membership = asMembershipSet(other);
        boolean changed = false;

        for (int i = 0; i < table.length; i++) {
            Object slot = table[i];
            if (isOccupied(slot) && membership.contains(slot)) {
                if (!changed) {
                    modCount++;
                    changed = true;
                }
                table[i] = DELETED_SLOT;
                size--;
            }
        }

        if (changed) {
            compactAfterBulkRemoval();
        }
    }

    /** Python equivalent: {@code self.difference_update(*others)}. */
    public final void differenceUpdate(Iterable<?>... others) {
        Objects.requireNonNull(others, "others");

        for (int i = 0; i < others.length; i++) {
            Iterable<?> other = Objects.requireNonNull(
                others[i], "others[" + i + "]"
            );
            differenceUpdate(other);

            if (isEmpty()) {
                return;
            }
        }
    }

    /** Python equivalent: {@code self.symmetric_difference_update(other)}. */
    public void symmetricDifferenceUpdate(Iterable<? extends T> other) {
        Objects.requireNonNull(other, "other");

        if (other == this) {
            clear();
            return;
        }

        Set<T> otherSet = new Set<>(other);
        for (T value : otherSet) {
            if (!discard(value)) {
                add(value);
            }
        }
    }


    // ============================================================
    // Set relations
    // ============================================================

    /** Python equivalent: {@code self.issubset(other)}. */
    public boolean isSubsetOf(Iterable<?> other) {
        Objects.requireNonNull(other, "other");

        Set<?> membership = asMembershipSet(other);
        if (size > membership.size()) {
            return false;
        }

        for (T value : this) {
            if (!membership.contains(value)) {
                return false;
            }
        }

        return true;
    }

    /** Strict subset equivalent to Python {@code self < other}. */
    public boolean isProperSubsetOf(Iterable<?> other) {
        Objects.requireNonNull(other, "other");

        Set<?> membership = asMembershipSet(other);
        return size < membership.size() && isSubsetOf(membership);
    }

    /** Python equivalent: {@code self.issuperset(other)}. */
    public boolean isSupersetOf(Iterable<?> other) {
        Objects.requireNonNull(other, "other");

        Set<?> membership = asMembershipSet(other);
        if (size < membership.size()) {
            return false;
        }

        for (Object value : membership) {
            if (!contains(value)) {
                return false;
            }
        }

        return true;
    }

    /** Strict superset equivalent to Python {@code self > other}. */
    public boolean isProperSupersetOf(Iterable<?> other) {
        Objects.requireNonNull(other, "other");

        Set<?> membership = asMembershipSet(other);
        return size > membership.size() && isSupersetOf(membership);
    }

    /** Python equivalent: {@code self.isdisjoint(other)}. */
    public boolean isDisjoint(Iterable<?> other) {
        Objects.requireNonNull(other, "other");

        if (other instanceof Set<?> set) {
            Set<?> smaller = size <= set.size ? this : set;
            Set<?> larger = size <= set.size ? set : this;

            for (Object value : smaller) {
                if (larger.contains(value)) {
                    return false;
                }
            }
            return true;
        }

        for (Object value : other) {
            if (contains(value)) {
                return false;
            }
        }
        return true;
    }


    // ============================================================
    // Conversion
    // ============================================================

    /** Returns the current elements in an array. Order is unspecified. */
    public Object[] toArray() {
        Object[] result = new Object[size];
        int index = 0;

        for (Object slot : table) {
            if (isOccupied(slot)) {
                result[index++] = slot;
            }
        }

        return result;
    }

    /** Converts this set to the custom DataStructures.List. */
    public List<T> toDataList() {
        List<T> result = new List<>(size);
        for (T value : this) {
            result.append(value);
        }
        return result;
    }

    /** Converts this set to an unmodifiable standard Java Set. */
    public java.util.Set<T> toJavaSet() {
        int initialCapacity = Math.max(16, (int) Math.ceil(size / 0.75d) + 1);
        java.util.Set<T> result = new java.util.HashSet<>(initialCapacity);

        for (T value : this) {
            result.add(value);
        }

        return java.util.Collections.unmodifiableSet(result);
    }


    // ============================================================
    // Streams / iteration
    // ============================================================

    public Stream<T> stream() {
        return StreamSupport.stream(
            this::spliterator,
            SPLITERATOR_CHARACTERISTICS,
            false
        );
    }

    public Stream<T> parallelStream() {
        return StreamSupport.stream(
            this::spliterator,
            SPLITERATOR_CHARACTERISTICS,
            true
        );
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int cursor;
            private int nextIndex = findNextOccupiedIndex(0);
            private final int expectedModCount = modCount;

            @Override
            public boolean hasNext() {
                checkForConcurrentModification();
                return nextIndex >= 0;
            }

            @Override
            public T next() {
                checkForConcurrentModification();

                if (nextIndex < 0) {
                    throw new NoSuchElementException();
                }

                T value = elementAt(nextIndex);
                cursor = nextIndex + 1;
                nextIndex = findNextOccupiedIndex(cursor);
                return value;
            }

            private void checkForConcurrentModification() {
                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException(
                        "Set was structurally modified while iterating."
                    );
                }
            }
        };
    }

    /**
     * Returns an unordered, distinct, exactly-sized Spliterator.
     *
     * <p>The iterator used underneath remains fail-fast.</p>
     */
    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(
            iterator(),
            size,
            SPLITERATOR_CHARACTERISTICS
        );
    }


    // ============================================================
    // Equality / hashing
    // ============================================================

    /** Structural, order-independent set equality. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Set<?> other)) {
            return false;
        }

        if (size != other.size) {
            return false;
        }

        for (T value : this) {
            if (!other.contains(value)) {
                return false;
            }
        }

        return true;
    }

    /** Order-independent hash code compatible with equals(). */
    @Override
    public int hashCode() {
        int hash = 0;

        for (Object slot : table) {
            if (isOccupied(slot)) {
                hash += Objects.hashCode(slot);
            }
        }

        return hash;
    }


    // ============================================================
    // String representation
    // ============================================================

    /**
     * Python-like representation.
     *
     * <p>Empty: {@code set()}</p>
     * <p>Non-empty: {@code {1, 2, 'Lorenzo'}}</p>
     *
     * <p>Iteration order is intentionally unspecified.</p>
     */
    @Override
    public String toString() {
        if (isEmpty()) {
            return "set()";
        }
        return PythonRepr.sequence(this, "{", "}", "{...}", false);
    }


    // ============================================================
    // Private hash-table helpers
    // ============================================================

    private int findIndex(Object value) {
        return findIndex(value, spreadHash(value));
    }

    private int findIndex(Object value, int hash) {
        int mask = table.length - 1;
        int index = hash & mask;

        while (true) {
            Object slot = table[index];

            if (slot == EMPTY_SLOT) {
                return -1;
            }

            if (slot != DELETED_SLOT && Objects.equals(slot, value)) {
                return index;
            }

            index = (index + 1) & mask;
        }
    }

    private void removeAtIndex(int index) {
        table[index] = DELETED_SLOT;
        size--;
        modCount++;
        compactAfterRemoval();
    }

    private void ensureCapacityForInsert() {
        int threshold = resizeThreshold(table.length);
        if (usedSlots + 1 <= threshold) {
            return;
        }

        if (size + 1 <= threshold) {
            rehash(table.length);
            return;
        }

        if (table.length >= MAX_CAPACITY) {
            // Probing terminates at an empty slot, so never fill the last one.
            if (usedSlots < table.length - 1) {
                return;
            }
            if (size < usedSlots) {
                rehash(table.length);
                return;
            }
            throw new OutOfMemoryError("Set has reached maximum capacity.");
        }

        rehash(table.length << 1);
    }

    private void compactAfterRemoval() {
        if (size == 0) {
            Arrays.fill(table, EMPTY_SLOT);
            usedSlots = 0;
            return;
        }

        if (table.length > DEFAULT_CAPACITY
            && size < (int) (table.length * SHRINK_FACTOR)) {

            int targetCapacity = Math.max(
                DEFAULT_CAPACITY,
                capacityForExpectedSize(size)
            );
            rehash(targetCapacity);
            return;
        }

        int tombstones = usedSlots - size;
        if (tombstones > table.length / 4) {
            rehash(table.length);
        }
    }

    private void compactAfterBulkRemoval() {
        if (size == 0) {
            table = newTable(DEFAULT_CAPACITY);
            usedSlots = 0;
            return;
        }

        int targetCapacity = table.length;
        if (table.length > DEFAULT_CAPACITY
            && size < (int) (table.length * SHRINK_FACTOR)) {

            targetCapacity = Math.max(
                DEFAULT_CAPACITY,
                capacityForExpectedSize(size)
            );
        }

        rehash(targetCapacity);
    }

    private void rehash(int requestedCapacity) {
        int newCapacity = Math.max(DEFAULT_CAPACITY, tableSizeFor(requestedCapacity));
        Object[] replacement = newTable(newCapacity);

        for (Object slot : table) {
            if (isOccupied(slot)) {
                insertDuringRehash(replacement, slot);
            }
        }

        // Hashing user elements can throw. Publish the replacement only after
        // it is complete, preserving all entries if rebuilding fails.
        table = replacement;
        usedSlots = size;
    }

    private static void insertDuringRehash(Object[] destination, Object value) {
        int mask = destination.length - 1;
        int index = spreadHash(value) & mask;

        while (destination[index] != EMPTY_SLOT) {
            index = (index + 1) & mask;
        }

        destination[index] = value;
    }

    private int findNextOccupiedIndex(int start) {
        for (int i = start; i < table.length; i++) {
            if (isOccupied(table[i])) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private T elementAt(int index) {
        return (T) table[index];
    }

    private static boolean isOccupied(Object slot) {
        return slot != EMPTY_SLOT && slot != DELETED_SLOT;
    }

    private static int spreadHash(Object value) {
        int hash = Objects.hashCode(value);
        return hash ^ (hash >>> 16);
    }

    private static int resizeThreshold(int capacity) {
        return Math.min(capacity - 1, (int) (capacity * LOAD_FACTOR));
    }

    private static Object[] newTable(int capacity) {
        Object[] result = new Object[capacity];
        Arrays.fill(result, EMPTY_SLOT);
        return result;
    }

    private static int capacityForExpectedSize(int expectedSize) {
        if (expectedSize <= 0) {
            return DEFAULT_CAPACITY;
        }

        long required = (long) Math.ceil(expectedSize / LOAD_FACTOR);
        if (required > MAX_CAPACITY) {
            throw new IllegalArgumentException(
                "Expected size is too large: " + expectedSize
            );
        }

        return Math.max(DEFAULT_CAPACITY, tableSizeFor((int) required));
    }

    private static int tableSizeFor(int requestedCapacity) {
        if (requestedCapacity <= 1) {
            return 1;
        }
        if (requestedCapacity >= MAX_CAPACITY) {
            return MAX_CAPACITY;
        }

        int highest = Integer.highestOneBit(requestedCapacity - 1);
        return highest << 1;
    }

    private static Set<?> asMembershipSet(Iterable<?> values) {
        if (values instanceof Set<?> set) {
            return set;
        }

        Set<Object> result = new Set<>();
        for (Object value : values) {
            result.add(value);
        }
        return result;
    }

    private void validateElement(T value) {
        if (value instanceof Set<?>) {
            throw new IllegalArgumentException(
                "A mutable DataStructures.Set cannot be stored inside another Set "
                    + "because its hashCode changes when its contents change."
            );
        }
    }
}
