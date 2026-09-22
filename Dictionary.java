package DataStructures;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Mutable, Python-style dictionary implemented from scratch with open
 * addressing, linear probing and tombstones.
 *
 * <p>The hash table stores key/value entries directly and does not wrap
 * {@link java.util.HashMap}. In addition to the hash table, entries are linked
 * in insertion order so iteration and string representation follow modern
 * Python {@code dict} semantics.</p>
 *
 * <p>Keys must keep a stable {@code equals/hashCode} state while stored in the
 * dictionary. The mutable custom {@link List}, {@link LinkedList}, {@link Set} and
 * {@code Dictionary} types are therefore rejected as keys. Tuples are allowed
 * only when they do not recursively contain those mutable custom structures.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class Dictionary<K, V> implements Iterable<K> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final int MAX_CAPACITY = 1 << 30;
    private static final double LOAD_FACTOR = 0.65;
    private static final double SHRINK_FACTOR = 0.20;

    private static final Object EMPTY_SLOT = new Object();
    private static final Object DELETED_SLOT = new Object();

    private static final int SPLITERATOR_CHARACTERISTICS =
        Spliterator.ORDERED
            | Spliterator.DISTINCT
            | Spliterator.SIZED
            | Spliterator.SUBSIZED;

    private Object[] table;
    private int size;
    private int usedSlots;
    private int modCount;

    private Entry<K, V> head;
    private Entry<K, V> tail;


    // ============================================================
    // Constructors / factories
    // ============================================================

    /** Creates an empty dictionary. */
    public Dictionary() {
        table = newTable(DEFAULT_CAPACITY);
    }

    /**
     * Creates an empty dictionary sized for approximately
     * {@code expectedSize} mappings before the first resize.
     */
    public Dictionary(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException(
                "Expected size cannot be negative: " + expectedSize
            );
        }

        table = newTable(capacityForExpectedSize(expectedSize));
    }

    /**
     * Creates a shallow copy of another custom Dictionary.
     *
     * <p>Stored spread hashes are reused directly, so copying does not
     * revalidate keys or recompute their hashes.</p>
     */
    public Dictionary(Dictionary<? extends K, ? extends V> other) {
        Objects.requireNonNull(other, "other");
        table = newTable(capacityForExpectedSize(other.size));
        copyEntriesFrom(other);
    }

    /** Creates a dictionary from a standard Java Map. */
    public Dictionary(Map<? extends K, ? extends V> values) {
        Objects.requireNonNull(values, "values");
        table = newTable(capacityForExpectedSize(values.size()));
        update(values);
    }

    /** Creates a custom Dictionary from a standard Java Map. */
    public static <K, V> Dictionary<K, V> fromJavaMap(
        Map<? extends K, ? extends V> values
    ) {
        return new Dictionary<>(values);
    }

    /** Python equivalent: {@code dict.fromkeys(keys)}. */
    public static <K> Dictionary<K, Object> fromKeys(Iterable<? extends K> keys) {
        return fromKeys(keys, null);
    }

    /** Python equivalent: {@code dict.fromkeys(keys, value)}. */
    public static <K, V> Dictionary<K, V> fromKeys(
        Iterable<? extends K> keys,
        V value
    ) {
        Objects.requireNonNull(keys, "keys");

        Dictionary<K, V> result = new Dictionary<>();
        for (K key : keys) {
            result.put(key, value);
        }
        return result;
    }


    // ============================================================
    // Basic access / mutation
    // ============================================================

    /**
     * Python equivalent: {@code dictionary[key] = value}.
     *
     * <p>Replacing the value of an existing key does not change its insertion
     * position and is not considered a structural modification.</p>
     *
     * @return the previous value, or {@code null} if the key was absent or
     *         previously mapped to {@code null}
     */
    public V put(K key, V value) {
        validateKey(key);

        int hash = spreadHash(key);
        int existingIndex = findIndex(key, hash);

        if (existingIndex >= 0) {
            Entry<K, V> entry = entryAt(existingIndex);
            V previous = entry.value;
            entry.value = value;
            return previous;
        }

        ensureCapacityForInsert();
        insertNewEntry(new Entry<>(key, value, hash));
        return null;
    }

    /** Alias for {@link #put(Object, Object)} with Python assignment semantics. */
    public V set(K key, V value) {
        return put(key, value);
    }

    /**
     * Python equivalent: {@code dictionary[key]}.
     *
     * @throws NoSuchElementException if the key is absent
     */
    public V get(Object key) {
        Entry<K, V> entry = findEntry(key);
        if (entry == null) {
            throw new NoSuchElementException(
                "Key not found in dictionary: " + PythonRepr.format(key)
            );
        }
        return entry.value;
    }

    /** Python equivalent: {@code dictionary.get(key, defaultValue)}. */
    public V get(Object key, V defaultValue) {
        Entry<K, V> entry = findEntry(key);
        return entry == null ? defaultValue : entry.value;
    }

    /** Java-style alias for {@link #get(Object, Object)}. */
    public V getOrDefault(Object key, V defaultValue) {
        return get(key, defaultValue);
    }

    /**
     * Python equivalent: {@code dictionary.setdefault(key, defaultValue)}.
     * Existing keys retain both their value and insertion position.
     */
    public V setDefault(K key, V defaultValue) {
        validateKey(key);

        int hash = spreadHash(key);
        int existingIndex = findIndex(key, hash);
        if (existingIndex >= 0) {
            return entryAt(existingIndex).value;
        }

        ensureCapacityForInsert();
        insertNewEntry(new Entry<>(key, defaultValue, hash));
        return defaultValue;
    }

    /** Python equivalent: {@code key in dictionary}. */
    public boolean containsKey(Object key) {
        return findEntry(key) != null;
    }

    /** Returns whether at least one mapping has the specified value. */
    public boolean containsValue(Object value) {
        for (Entry<K, V> entry = head; entry != null; entry = entry.next) {
            if (Objects.equals(entry.value, value)) {
                return true;
            }
        }
        return false;
    }


    // ============================================================
    // Removal
    // ============================================================

    /**
     * Removes a key and returns its value.
     *
     * <p>This is the method equivalent of Python's {@code del d[key]}.</p>
     *
     * @throws NoSuchElementException if the key is absent
     */
    public V remove(Object key) {
        int hash = spreadHash(key);
        int index = findIndex(key, hash);

        if (index < 0) {
            throw new NoSuchElementException(
                "Key not found in dictionary: " + PythonRepr.format(key)
            );
        }

        return removeAtIndex(index);
    }

    /** Python equivalent: {@code dictionary.pop(key)}. */
    public V pop(Object key) {
        return remove(key);
    }

    /** Python equivalent: {@code dictionary.pop(key, defaultValue)}. */
    public V pop(Object key, V defaultValue) {
        int hash = spreadHash(key);
        int index = findIndex(key, hash);
        return index < 0 ? defaultValue : removeAtIndex(index);
    }

    /**
     * Python equivalent: {@code dictionary.popitem()}.
     * Removes the most recently inserted mapping (LIFO).
     */
    public Tuple popItem() {
        if (tail == null) {
            throw new NoSuchElementException("Cannot popitem from an empty dictionary.");
        }

        Entry<K, V> entry = tail;
        int index = findIndex(entry.key, entry.hash);

        if (index < 0) {
            throw new IllegalStateException("Dictionary state is inconsistent.");
        }

        removeAtIndex(index);
        return Tuple.of(entry.key, entry.value);
    }

    /** Removes all mappings and releases any oversized backing table. */
    public void clear() {
        if (size == 0 && table.length == DEFAULT_CAPACITY) {
            return;
        }

        table = newTable(DEFAULT_CAPACITY);
        size = 0;
        usedSlots = 0;
        head = null;
        tail = null;
        modCount++;
    }


    // ============================================================
    // Bulk update / merge
    // ============================================================

    /** Python-style {@code dictionary.update(otherDictionary)}. */
    public void update(Dictionary<? extends K, ? extends V> other) {
        Objects.requireNonNull(other, "other");

        if (other == this) {
            return;
        }

        for (Entry<? extends K, ? extends V> entry = other.head;
             entry != null;
             entry = entry.next) {
            put(entry.key, entry.value);
        }
    }

    /** Python-style {@code dictionary.update(javaMap)}. */
    public void update(Map<? extends K, ? extends V> values) {
        Objects.requireNonNull(values, "values");

        updateEntries(values.entrySet());
    }

    /**
     * Updates from typed Java key/value entries in iteration order.
     *
     * <p>This provides a type-safe alternative to {@link #updateItems(Iterable)}.
     * Repeated keys retain their original position and take the last value.
     * Null keys and values are supported, but null entries are rejected.</p>
     */
    public void updateEntries(
        Iterable<? extends Map.Entry<? extends K, ? extends V>> entries
    ) {
        Objects.requireNonNull(entries, "entries");

        for (Map.Entry<? extends K, ? extends V> entry : entries) {
            Objects.requireNonNull(entry, "Dictionary update entry");
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Updates from an iterable of two-element Tuples.
     *
     * <p>This mirrors Python's ability to construct/update a dict from an
     * iterable of key/value pairs.</p>
     *
     * <p>Tuple elements have no generic type information, so the caller must
     * ensure that keys and values match this dictionary's types. Use
     * {@link #updateEntries(Iterable)} for compile-time type checking.</p>
     *
     * @throws IllegalArgumentException if an element is not a two-element Tuple
     */
    @SuppressWarnings("unchecked")
    public void updateItems(Iterable<Tuple> items) {
        Objects.requireNonNull(items, "items");

        for (Tuple item : items) {
            Objects.requireNonNull(item, "Dictionary update item");

            if (item.size() != 2) {
                throw new IllegalArgumentException(
                    "Dictionary update items must contain exactly two elements: " + item
                );
            }

            put((K) item.get(0), (V) item.get(1));
        }
    }

    /**
     * Returns a merged dictionary corresponding to Python's {@code self | other}.
     * Values from {@code other} win on duplicate keys.
     */
    public Dictionary<K, V> union(Dictionary<? extends K, ? extends V> other) {
        Objects.requireNonNull(other, "other");

        Dictionary<K, V> result = copy();
        result.update(other);
        return result;
    }

    /** Java-interop overload of {@link #union(Dictionary)}. */
    public Dictionary<K, V> union(Map<? extends K, ? extends V> other) {
        Objects.requireNonNull(other, "other");

        Dictionary<K, V> result = copy();
        result.update(other);
        return result;
    }


    // ============================================================
    // Size / capacity
    // ============================================================

    /** Equivalent to Python {@code len(dictionary)}. */
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
    // Live views / snapshots
    // ============================================================

    /**
     * Returns a live view of this dictionary's keys in insertion order.
     *
     * <p>The view is backed by this Dictionary: mappings added or removed
     * after the view is created are visible through subsequent operations on
     * the same view. Iterators remain fail-fast for structural changes.</p>
     */
    public KeyView keys() {
        return new KeyView();
    }

    /**
     * Returns a live view of this dictionary's values in insertion order.
     *
     * <p>The view is backed by this Dictionary and therefore reflects later
     * additions, removals and value replacements.</p>
     */
    public ValueView values() {
        return new ValueView();
    }

    /**
     * Returns a live view of key/value pairs in insertion order.
     *
     * <p>Each element produced by the view is a fresh two-element
     * {@link Tuple}. The view itself remains backed by this Dictionary.</p>
     */
    public ItemView items() {
        return new ItemView();
    }

    /** Returns the current keys as an independent custom List snapshot. */
    public List<K> keysSnapshot() {
        return keys().toDataList();
    }

    /** Returns the current values as an independent custom List snapshot. */
    public List<V> valuesSnapshot() {
        return values().toDataList();
    }

    /** Returns the current key/value pairs as an independent List snapshot. */
    public List<Tuple> itemsSnapshot() {
        return items().toDataList();
    }

    /** Live view corresponding to Python's {@code dict_keys}. */
    public final class KeyView implements Iterable<K> {

        public int size() {
            return Dictionary.this.size;
        }

        public boolean isEmpty() {
            return Dictionary.this.isEmpty();
        }

        public boolean contains(Object key) {
            return Dictionary.this.containsKey(key);
        }

        public List<K> toDataList() {
            List<K> result = new List<>(Dictionary.this.size);
            for (K key : this) {
                result.append(key);
            }
            return result;
        }

        public Stream<K> stream() {
            return StreamSupport.stream(
                this::spliterator,
                SPLITERATOR_CHARACTERISTICS,
                false
            );
        }

        public Stream<K> parallelStream() {
            return StreamSupport.stream(
                this::spliterator,
                SPLITERATOR_CHARACTERISTICS,
                true
            );
        }

        @Override
        public Iterator<K> iterator() {
            return keyIterator(false);
        }

        @Override
        public Spliterator<K> spliterator() {
            return Spliterators.spliterator(
                iterator(),
                Dictionary.this.size,
                SPLITERATOR_CHARACTERISTICS
            );
        }

        @Override
        public String toString() {
            return PythonRepr.sequence(this, "dict_keys([", "])", "dict_keys([...])", false);
        }
    }

    /** Live view corresponding to Python's {@code dict_values}. */
    public final class ValueView implements Iterable<V> {

        private static final int CHARACTERISTICS =
            Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;

        public int size() {
            return Dictionary.this.size;
        }

        public boolean isEmpty() {
            return Dictionary.this.isEmpty();
        }

        public boolean contains(Object value) {
            return Dictionary.this.containsValue(value);
        }

        public List<V> toDataList() {
            List<V> result = new List<>(Dictionary.this.size);
            for (V value : this) {
                result.append(value);
            }
            return result;
        }

        public Stream<V> stream() {
            return StreamSupport.stream(this::spliterator, CHARACTERISTICS, false);
        }

        public Stream<V> parallelStream() {
            return StreamSupport.stream(this::spliterator, CHARACTERISTICS, true);
        }

        @Override
        public Iterator<V> iterator() {
            return valueIterator();
        }

        @Override
        public Spliterator<V> spliterator() {
            return Spliterators.spliterator(
                iterator(),
                Dictionary.this.size,
                CHARACTERISTICS
            );
        }

        @Override
        public String toString() {
            return PythonRepr.sequence(this, "dict_values([", "])", "dict_values([...])", false);
        }
    }

    /** Live view corresponding to Python's {@code dict_items}. */
    public final class ItemView implements Iterable<Tuple> {

        public int size() {
            return Dictionary.this.size;
        }

        public boolean isEmpty() {
            return Dictionary.this.isEmpty();
        }

        /** Returns whether this live view currently contains a pair. */
        public boolean contains(Object item) {
            if (!(item instanceof Tuple tuple) || tuple.size() != 2) {
                return false;
            }

            Entry<K, V> entry = findEntry(tuple.get(0));
            return entry != null && Objects.equals(entry.value, tuple.get(1));
        }

        public List<Tuple> toDataList() {
            List<Tuple> result = new List<>(Dictionary.this.size);
            for (Tuple item : this) {
                result.append(item);
            }
            return result;
        }

        public Stream<Tuple> stream() {
            return StreamSupport.stream(
                this::spliterator,
                SPLITERATOR_CHARACTERISTICS,
                false
            );
        }

        public Stream<Tuple> parallelStream() {
            return StreamSupport.stream(
                this::spliterator,
                SPLITERATOR_CHARACTERISTICS,
                true
            );
        }

        @Override
        public Iterator<Tuple> iterator() {
            return itemIterator();
        }

        @Override
        public Spliterator<Tuple> spliterator() {
            return Spliterators.spliterator(
                iterator(),
                Dictionary.this.size,
                SPLITERATOR_CHARACTERISTICS
            );
        }

        @Override
        public String toString() {
            return PythonRepr.sequence(this, "dict_items([", "])", "dict_items([...])", false);
        }
    }


    // ============================================================
    // Copy / conversion
    // ============================================================

    /**
     * Returns a shallow independent copy preserving insertion order.
     *
     * <p>Existing spread hashes are reused; keys are not revalidated or
     * rehashed.</p>
     */
    public Dictionary<K, V> copy() {
        Dictionary<K, V> result = new Dictionary<>(size);
        result.copyEntriesFrom(this);
        return result;
    }

    /** Converts this dictionary to an unmodifiable LinkedHashMap snapshot. */
    public Map<K, V> toJavaMap() {
        int initialCapacity = Math.max(16, (int) Math.ceil(size / 0.75d) + 1);
        Map<K, V> result = new LinkedHashMap<>(initialCapacity);

        for (Entry<K, V> entry = head; entry != null; entry = entry.next) {
            result.put(entry.key, entry.value);
        }

        return java.util.Collections.unmodifiableMap(result);
    }


    // ============================================================
    // Functional traversal / streams
    // ============================================================

    /** Applies an action to each key/value pair in insertion order. */
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action, "action");

        int expectedModCount = modCount;
        for (Entry<K, V> entry = head; entry != null; entry = entry.next) {
            action.accept(entry.key, entry.value);

            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException(
                    "Dictionary was structurally modified during forEach()."
                );
            }
        }
    }

    /** Returns a late-binding stream over keys in insertion order. */
    public Stream<K> stream() {
        return StreamSupport.stream(
            this::spliterator,
            SPLITERATOR_CHARACTERISTICS,
            false
        );
    }

    /** Returns a late-binding parallel stream over keys. */
    public Stream<K> parallelStream() {
        return StreamSupport.stream(
            this::spliterator,
            SPLITERATOR_CHARACTERISTICS,
            true
        );
    }


    /** Returns a late-binding stream over values in insertion order. */
    public Stream<V> valueStream() {
        return values().stream();
    }

    /** Returns a late-binding stream over key/value Tuple pairs. */
    public Stream<Tuple> entryStream() {
        return items().stream();
    }


    // ============================================================
    // Iteration / Spliterator
    // ============================================================

    /**
     * Iterating a dictionary yields keys in insertion order, matching Python.
     */
    @Override
    public Iterator<K> iterator() {
        return keyIterator(false);
    }

    /**
     * Python 3.8+ equivalent of {@code reversed(dictionary)}.
     *
     * <p>The returned Iterable is live: its iterator starts from the current
     * tail when iteration begins. Each iterator is fail-fast for structural
     * changes after that iterator has been created.</p>
     */
    public Iterable<K> reversed() {
        return () -> keyIterator(true);
    }

    /** Returns an ordered, distinct and exactly-sized key spliterator. */
    @Override
    public Spliterator<K> spliterator() {
        return Spliterators.spliterator(
            iterator(),
            size,
            SPLITERATOR_CHARACTERISTICS
        );
    }


    // ============================================================
    // Equality / hashing
    // ============================================================

    /**
     * Structural dictionary equality. Insertion order does not affect equality.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Dictionary<?, ?> other)) {
            return false;
        }

        if (size != other.size) {
            return false;
        }

        for (Entry<K, V> entry = head; entry != null; entry = entry.next) {
            Entry<?, ?> otherEntry = other.findEntry(entry.key);
            if (otherEntry == null || !Objects.equals(entry.value, otherEntry.value)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Order-independent mapping hash compatible with equals().
     * Mutable dictionaries should not themselves be used as hash keys.
     */
    @Override
    public int hashCode() {
        int hash = 0;

        for (Entry<K, V> entry = head; entry != null; entry = entry.next) {
            hash += Objects.hashCode(entry.key) ^ Objects.hashCode(entry.value);
        }

        return hash;
    }


    // ============================================================
    // String representation
    // ============================================================

    /** Python-like representation: {@code {'name': 'Lorenzo', 'age': 21}}. */
    @Override
    public String toString() {
        return PythonRepr.container(this, "{...}", () -> {
            StringBuilder result = new StringBuilder("{");
            boolean first = true;

            for (Entry<K, V> entry = head; entry != null; entry = entry.next) {
                if (!first) {
                    result.append(", ");
                }

                result.append(PythonRepr.format(entry.key));
                result.append(": ");
                result.append(PythonRepr.format(entry.value));
                first = false;
            }
            return result.append('}').toString();
        });
    }


    // ============================================================
    // Private hash-table helpers
    // ============================================================

    private Entry<K, V> findEntry(Object key) {
        int index = findIndex(key, spreadHash(key));
        return index < 0 ? null : entryAt(index);
    }

    private int findIndex(Object key, int hash) {
        int mask = table.length - 1;
        int index = hash & mask;

        for (int probed = 0; probed < table.length; probed++) {
            Object slot = table[index];

            if (slot == EMPTY_SLOT) {
                return -1;
            }

            if (slot != DELETED_SLOT) {
                Entry<?, ?> entry = (Entry<?, ?>) slot;
                if (entry.hash == hash && Objects.equals(entry.key, key)) {
                    return index;
                }
            }

            index = (index + 1) & mask;
        }

        return -1;
    }

    private void insertNewEntry(Entry<K, V> entry) {
        int mask = table.length - 1;
        int index = entry.hash & mask;
        int firstDeleted = -1;

        while (true) {
            Object slot = table[index];

            if (slot == EMPTY_SLOT) {
                int insertionIndex = firstDeleted >= 0 ? firstDeleted : index;
                if (firstDeleted < 0) {
                    usedSlots++;
                }

                table[insertionIndex] = entry;
                size++;
                linkLast(entry);
                modCount++;
                return;
            }

            if (slot == DELETED_SLOT && firstDeleted < 0) {
                firstDeleted = index;
            }

            index = (index + 1) & mask;
        }
    }

    private V removeAtIndex(int index) {
        Entry<K, V> entry = entryAt(index);
        V value = entry.value;

        table[index] = DELETED_SLOT;
        size--;
        unlink(entry);
        modCount++;
        compactAfterRemoval();

        return value;
    }

    private void linkLast(Entry<K, V> entry) {
        entry.previous = tail;
        entry.next = null;

        if (tail == null) {
            head = entry;
        } else {
            tail.next = entry;
        }

        tail = entry;
    }

    private void unlink(Entry<K, V> entry) {
        Entry<K, V> previous = entry.previous;
        Entry<K, V> next = entry.next;

        if (previous == null) {
            head = next;
        } else {
            previous.next = next;
        }

        if (next == null) {
            tail = previous;
        } else {
            next.previous = previous;
        }

        entry.previous = null;
        entry.next = null;
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
            // Probing needs an empty slot to terminate. At maximum capacity,
            // reclaim tombstones before allowing the final free slot to vanish.
            if (usedSlots < table.length - 1) {
                return;
            }
            if (size < table.length - 1) {
                rehash(table.length);
                return;
            }
            throw new OutOfMemoryError("Dictionary has reached maximum capacity.");
        }

        rehash(table.length << 1);
    }

    private void compactAfterRemoval() {
        if (size == 0) {
            table = newTable(DEFAULT_CAPACITY);
            usedSlots = 0;
            head = null;
            tail = null;
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

    private void rehash(int requestedCapacity) {
        int newCapacity = Math.max(DEFAULT_CAPACITY, tableSizeFor(requestedCapacity));
        table = newTable(newCapacity);
        usedSlots = 0;

        for (Entry<K, V> entry = head; entry != null; entry = entry.next) {
            insertEntryDuringRehash(entry);
        }
    }

    private void insertEntryDuringRehash(Entry<K, V> entry) {
        int mask = table.length - 1;
        int index = entry.hash & mask;

        while (table[index] != EMPTY_SLOT) {
            index = (index + 1) & mask;
        }

        table[index] = entry;
        usedSlots++;
    }

    @SuppressWarnings("unchecked")
    private Entry<K, V> entryAt(int index) {
        return (Entry<K, V>) table[index];
    }

    /**
     * Copies mappings while reusing their stored spread hashes and known
     * uniqueness. Used only with fresh destination dictionaries.
     */
    private void copyEntriesFrom(Dictionary<? extends K, ? extends V> other) {
        for (Entry<? extends K, ? extends V> entry = other.head;
             entry != null;
             entry = entry.next) {
            insertKnownUniqueEntry(entry.key, entry.value, entry.hash);
        }
    }

    /**
     * Inserts a known-valid, known-unique mapping into a fresh table without
     * duplicate lookup, key validation or hash recomputation.
     */
    private void insertKnownUniqueEntry(K key, V value, int hash) {
        int mask = table.length - 1;
        int index = hash & mask;

        while (table[index] != EMPTY_SLOT) {
            index = (index + 1) & mask;
        }

        Entry<K, V> entry = new Entry<>(key, value, hash);
        table[index] = entry;
        usedSlots++;
        size++;
        linkLast(entry);
    }

    private Iterator<K> keyIterator(boolean reverse) {
        return new EntryIterator<>(reverse) {
            @Override
            K project(Entry<K, V> entry) {
                return entry.key;
            }
        };
    }

    private Iterator<V> valueIterator() {
        return new EntryIterator<>(false) {
            @Override
            V project(Entry<K, V> entry) {
                return entry.value;
            }
        };
    }

    private Iterator<Tuple> itemIterator() {
        return new EntryIterator<>(false) {
            @Override
            Tuple project(Entry<K, V> entry) {
                return Tuple.of(entry.key, entry.value);
            }
        };
    }

    private void validateKey(Object key) {
        if (!isStableCustomKey(key)) {
            throw new IllegalArgumentException(
                "Mutable DataStructures.List, LinkedList, Set and Dictionary instances cannot "
                    + "be dictionary keys because their hashCode can change."
            );
        }
    }

    private static boolean isStableCustomKey(Object key) {
        if (key instanceof List<?> || key instanceof LinkedList<?> || key instanceof Set<?>
            || key instanceof Dictionary<?, ?>) {
            return false;
        }

        if (key instanceof Tuple tuple) {
            for (Object element : tuple) {
                if (!isStableCustomKey(element)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static int spreadHash(Object key) {
        int hash = Objects.hashCode(key);
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


    /**
     * Shared fail-fast linked-list iterator used by key/value/item views and
     * reversed key iteration.
     */
    private abstract class EntryIterator<R> implements Iterator<R> {
        private Entry<K, V> next;
        private final boolean reverse;
        private final int expectedModCount;

        private EntryIterator(boolean reverse) {
            this.reverse = reverse;
            this.next = reverse ? tail : head;
            this.expectedModCount = modCount;
        }

        @Override
        public boolean hasNext() {
            checkForConcurrentModification();
            return next != null;
        }

        @Override
        public R next() {
            checkForConcurrentModification();

            if (next == null) {
                throw new NoSuchElementException();
            }

            Entry<K, V> current = next;
            next = reverse ? current.previous : current.next;
            return project(current);
        }

        abstract R project(Entry<K, V> entry);

        private void checkForConcurrentModification() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException(
                    "Dictionary was structurally modified while iterating."
                );
            }
        }
    }


    // ============================================================
    // Internal entry
    // ============================================================

    /** Internal table node. Not exposed as part of the public API. */
    private static final class Entry<K, V> {
        private final K key;
        private V value;
        private final int hash;
        private Entry<K, V> previous;
        private Entry<K, V> next;

        private Entry(K key, V value, int hash) {
            this.key = key;
            this.value = value;
            this.hash = hash;
        }
    }
}
