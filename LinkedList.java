package DataStructures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A mutable doubly linked list with Python-style indexing.
 *
 * <p>Adding or removing at either end is O(1). Indexed access is O(min(i, n-i))
 * because traversal starts from the nearer end. Null elements are allowed.
 * Copies and conversions are shallow and preserve front-to-back order.</p>
 *
 * <p>This class is not thread-safe. Iterators and spliterators are fail-fast on
 * a best-effort basis and do not support removal. Equality and hashing are
 * structural and order-sensitive, matching the custom {@link List} semantics.</p>
 *
 * @param <T> element type
 */
public final class LinkedList<T> implements Iterable<T> {
    private Node<T> head;
    private Node<T> tail;
    private int size;
    private int modCount;

    public LinkedList() { }

    /** Copies values in iteration order. */
    public LinkedList(Iterable<? extends T> values) {
        Objects.requireNonNull(values, "values");
        for (T value : values) {
            append(value);
        }
        modCount = 0;
    }

    @SafeVarargs
    public static <T> LinkedList<T> of(T... values) {
        Objects.requireNonNull(values, "values");
        LinkedList<T> result = new LinkedList<>();
        for (T value : values) {
            result.append(value);
        }
        result.modCount = 0;
        return result;
    }

    // ============================================================
    // Insertion
    // ============================================================

    /** Python-style append to the end. */
    public void append(T value) {
        addLast(value);
    }

    public void prepend(T value) {
        addFirst(value);
    }

    public void addFirst(T value) {
        Node<T> oldHead = head;
        Node<T> node = new Node<>(value, null, oldHead);
        head = node;
        if (oldHead == null) {
            tail = node;
        } else {
            oldHead.prev = node;
        }
        size++;
        modCount++;
    }

    public void addLast(T value) {
        Node<T> oldTail = tail;
        Node<T> node = new Node<>(value, oldTail, null);
        tail = node;
        if (oldTail == null) {
            head = node;
        } else {
            oldTail.next = node;
        }
        size++;
        modCount++;
    }

    /**
     * Appends every value from the iterable.
     *
     * <p>Extending a list with itself uses a shallow snapshot, so traversal does
     * not continue into elements appended by the operation itself.</p>
     */
    public void extend(Iterable<? extends T> values) {
        Objects.requireNonNull(values, "values");
        Iterable<? extends T> source = values == this ? copy() : values;
        for (T value : source) {
            append(value);
        }
    }

    /**
     * Python-style insertion. Out-of-range indices are clamped; negative
     * indices are interpreted relative to the end.
     */
    public void insert(int index, T value) {
        int normalized = normalizeInsertIndex(index);
        if (normalized == size) {
            addLast(value);
            return;
        }
        linkBefore(value, nodeAt(normalized));
    }

    // ============================================================
    // Access
    // ============================================================

    public T get(int index) {
        return nodeAt(normalizeIndex(index)).item;
    }

    /** Replaces one element without structurally modifying the list. */
    public T set(int index, T value) {
        Node<T> node = nodeAt(normalizeIndex(index));
        T previous = node.item;
        node.item = value;
        return previous;
    }

    public T getFirst() {
        requireNonEmpty();
        return head.item;
    }

    public T getLast() {
        requireNonEmpty();
        return tail.item;
    }

    // ============================================================
    // Removal
    // ============================================================

    /** Removes the first equal value, throwing when it is absent. */
    public void remove(T value) {
        Node<T> node = findFirstNode(value);
        if (node == null) {
            throw new NoSuchElementException("Value not found in linked list: " + value);
        }
        unlink(node);
    }

    public T removeAt(int index) {
        return unlink(nodeAt(normalizeIndex(index)));
    }

    public T removeFirst() {
        requireNonEmpty();
        return unlink(head);
    }

    public T removeLast() {
        requireNonEmpty();
        return unlink(tail);
    }

    /** Python-style pop from the end. */
    public T pop() {
        return pop(-1);
    }

    public T pop(int index) {
        if (size == 0) {
            throw new NoSuchElementException("Cannot pop from an empty linked list.");
        }
        return removeAt(index);
    }

    public void clear() {
        if (size == 0) {
            return;
        }

        Node<T> node = head;
        while (node != null) {
            Node<T> next = node.next;
            node.item = null;
            node.prev = null;
            node.next = null;
            node = next;
        }

        head = null;
        tail = null;
        size = 0;
        modCount++;
    }

    // ============================================================
    // Size / search
    // ============================================================

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(T value) {
        return indexOf(value) >= 0;
    }

    public int indexOf(T value) {
        int expectedModCount = modCount;
        int index = 0;
        for (Node<T> node = head; node != null; node = node.next, index++) {
            boolean equal = Objects.equals(node.item, value);
            checkForModification(expectedModCount);
            if (equal) {
                return index;
            }
        }
        return -1;
    }

    public int lastIndexOf(T value) {
        int expectedModCount = modCount;
        int index = size - 1;
        for (Node<T> node = tail; node != null; node = node.prev, index--) {
            boolean equal = Objects.equals(node.item, value);
            checkForModification(expectedModCount);
            if (equal) {
                return index;
            }
        }
        return -1;
    }

    public int count(T value) {
        int expectedModCount = modCount;
        int count = 0;
        for (Node<T> node = head; node != null; node = node.next) {
            if (Objects.equals(node.item, value)) {
                count++;
            }
            checkForModification(expectedModCount);
        }
        return count;
    }

    // ============================================================
    // Reordering / copying / conversions
    // ============================================================

    /** Reverses the links in place in O(n). */
    public void reverse() {
        if (size < 2) {
            return;
        }

        Node<T> node = head;
        while (node != null) {
            Node<T> next = node.next;
            node.next = node.prev;
            node.prev = next;
            node = next;
        }

        Node<T> oldHead = head;
        head = tail;
        tail = oldHead;
        modCount++;
    }

    public LinkedList<T> copy() {
        return new LinkedList<>(this);
    }

    public Object[] toArray() {
        Object[] result = new Object[size];
        int index = 0;
        for (Node<T> node = head; node != null; node = node.next) {
            result[index++] = node.item;
        }
        return result;
    }

    public List<T> toDataList() {
        return new List<>(this);
    }

    public java.util.List<T> toJavaList() {
        ArrayList<T> result = new ArrayList<>(size);
        for (T value : this) {
            result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    // ============================================================
    // Iteration / streams
    // ============================================================

    @Override
    public Iterator<T> iterator() {
        return new LinkedIterator(false);
    }

    public Iterator<T> descendingIterator() {
        return new LinkedIterator(true);
    }

    public Iterable<T> reversed() {
        return this::descendingIterator;
    }

    @Override
    public Spliterator<T> spliterator() {
        return new LinkedSpliterator();
    }

    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    public Stream<T> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }

    private final class LinkedIterator implements Iterator<T> {
        private Node<T> next;
        private final boolean descending;
        private final int expectedModCount;

        private LinkedIterator(boolean descending) {
            this.descending = descending;
            this.next = descending ? tail : head;
            this.expectedModCount = modCount;
        }

        @Override
        public boolean hasNext() {
            checkForModification(expectedModCount);
            return next != null;
        }

        @Override
        public T next() {
            checkForModification(expectedModCount);
            if (next == null) {
                throw new NoSuchElementException();
            }
            Node<T> current = next;
            next = descending ? current.prev : current.next;
            return current.item;
        }
    }

    private final class LinkedSpliterator implements Spliterator<T> {
        private Node<T> current;
        private int estimate = -1;
        private int expectedModCount;

        private int bind() {
            if (estimate < 0) {
                current = head;
                estimate = size;
                expectedModCount = modCount;
            }
            return estimate;
        }

        @Override
        public Spliterator<T> trySplit() {
            int remaining = bind();
            checkForModification(expectedModCount);
            int batchSize = remaining >>> 1;
            if (batchSize == 0 || current == null) {
                return null;
            }

            ArrayList<T> prefix = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                prefix.add(current.item);
                current = current.next;
            }
            estimate -= batchSize;
            checkForModification(expectedModCount);
            return Spliterators.spliterator(
                prefix,
                Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED
            );
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action, "action");
            bind();
            checkForModification(expectedModCount);
            if (estimate == 0 || current == null) {
                return false;
            }

            T value = current.item;
            current = current.next;
            estimate--;
            action.accept(value);
            checkForModification(expectedModCount);
            return true;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            Objects.requireNonNull(action, "action");
            bind();
            checkForModification(expectedModCount);
            while (estimate > 0 && current != null) {
                T value = current.item;
                current = current.next;
                estimate--;
                action.accept(value);
                checkForModification(expectedModCount);
            }
        }

        @Override
        public long estimateSize() {
            return bind();
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
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
        if (!(obj instanceof LinkedList<?> other) || size != other.size) {
            return false;
        }

        Iterator<T> left = iterator();
        Iterator<?> right = other.iterator();
        while (left.hasNext()) {
            if (!Objects.equals(left.next(), right.next())) {
                return false;
            }
        }
        return !right.hasNext();
    }

    @Override
    public int hashCode() {
        int expectedModCount = modCount;
        int hash = 1;
        for (Node<T> node = head; node != null; node = node.next) {
            hash = 31 * hash + Objects.hashCode(node.item);
            checkForModification(expectedModCount);
        }
        return hash;
    }

    @Override
    public String toString() {
        return PythonRepr.sequence(
            this,
            "LinkedList([",
            "])",
            "LinkedList([...])",
            false
        );
    }

    // ============================================================
    // Link helpers / index normalization
    // ============================================================

    private void requireNonEmpty() {
        if (size == 0) {
            throw new NoSuchElementException("Linked list is empty.");
        }
    }

    private void checkForModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException(
                "Linked list was modified during traversal."
            );
        }
    }

    private void linkBefore(T value, Node<T> successor) {
        Node<T> predecessor = successor.prev;
        Node<T> node = new Node<>(value, predecessor, successor);
        successor.prev = node;
        if (predecessor == null) {
            head = node;
        } else {
            predecessor.next = node;
        }
        size++;
        modCount++;
    }

    private T unlink(Node<T> node) {
        Node<T> previous = node.prev;
        Node<T> next = node.next;

        if (previous == null) {
            head = next;
        } else {
            previous.next = next;
        }

        if (next == null) {
            tail = previous;
        } else {
            next.prev = previous;
        }

        T value = node.item;
        node.item = null;
        node.prev = null;
        node.next = null;
        size--;
        modCount++;
        return value;
    }

    private Node<T> findFirstNode(T value) {
        int expectedModCount = modCount;
        for (Node<T> node = head; node != null; node = node.next) {
            boolean equal = Objects.equals(node.item, value);
            checkForModification(expectedModCount);
            if (equal) {
                return node;
            }
        }
        return null;
    }

    private Node<T> nodeAt(int index) {
        if (index < (size >>> 1)) {
            Node<T> node = head;
            for (int i = 0; i < index; i++) {
                node = node.next;
            }
            return node;
        }

        Node<T> node = tail;
        for (int i = size - 1; i > index; i--) {
            node = node.prev;
        }
        return node;
    }

    private int normalizeIndex(int index) {
        long normalized = index;
        if (normalized < 0) {
            normalized += size;
        }
        if (normalized < 0 || normalized >= size) {
            throw new IndexOutOfBoundsException(
                "Index " + index + " out of bounds for linked list of size " + size
            );
        }
        return (int) normalized;
    }

    private int normalizeInsertIndex(int index) {
        long normalized = index;
        if (normalized < 0) {
            normalized += size;
            if (normalized < 0) {
                return 0;
            }
        }
        if (normalized > size) {
            return size;
        }
        return (int) normalized;
    }

    private static final class Node<E> {
        private E item;
        private Node<E> prev;
        private Node<E> next;

        private Node(E item, Node<E> prev, Node<E> next) {
            this.item = item;
            this.prev = prev;
            this.next = next;
        }
    }
}
