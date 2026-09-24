package DataStructures;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Disjoint-set / Union-Find structure with path compression and union by size.
 *
 * <p>Each stored value starts in its own component. {@link #union(Object, Object)}
 * merges two components and {@link #find(Object)} returns the current
 * representative of an element. Values must keep stable equals/hashCode state
 * while stored. Null values are rejected.</p>
 *
 * <p>Union uses component size to keep trees shallow. Find performs full path
 * compression, so a sequence of operations has near-constant amortized cost
 * O(alpha(n)), where alpha is the inverse Ackermann function.</p>
 *
 * <p>Iteration follows insertion order and is fail-fast for membership changes.
 * Union/find operations do not invalidate iterators because they do not change
 * the set of stored elements. Equality and hashing use object identity.</p>
 *
 * @param <T> element type
 */
public final class DisjointSet<T> implements Iterable<T> {
    private static final int SPLITERATOR_CHARACTERISTICS =
        Spliterator.ORDERED
            | Spliterator.DISTINCT
            | Spliterator.SIZED
            | Spliterator.SUBSIZED
            | Spliterator.NONNULL;

    private final Dictionary<T, Node<T>> nodes = new Dictionary<>();
    private int componentCount;
    private int modCount;

    /** Creates an empty disjoint-set structure. */
    public DisjointSet() { }

    /** Adds every value from the iterable in encounter order. */
    public DisjointSet(Iterable<? extends T> values) {
        Objects.requireNonNull(values, "values");
        for (T value : values) {
            add(value);
        }
        modCount = 0;
    }

    @SafeVarargs
    public static <T> DisjointSet<T> of(T... values) {
        Objects.requireNonNull(values, "values");
        DisjointSet<T> result = new DisjointSet<>();
        for (T value : values) {
            result.add(value);
        }
        result.modCount = 0;
        return result;
    }

    /**
     * Adds a new singleton component.
     *
     * @return false when an equal value is already stored
     */
    public boolean add(T value) {
        requireValue(value);

        if (nodes.containsKey(value)) {
            return false;
        }

        Node<T> node = new Node<>(value);
        nodes.put(value, node);
        componentCount++;
        modCount++;
        return true;
    }

    public boolean contains(Object value) {
        Objects.requireNonNull(value, "value");
        return nodes.containsKey(value);
    }

    public int size() {
        return nodes.size();
    }

    public int componentCount() {
        return componentCount;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * Returns the representative value of the component containing value.
     *
     * <p>The lookup performs full path compression.</p>
     *
     * @throws NoSuchElementException when value is not present
     */
    public T find(T value) {
        return findRoot(requireStoredNode(value)).value;
    }

    /**
     * Merges the two components using union by size.
     *
     * <p>If both components have the same size, the representative of
     * {@code first} remains the representative.</p>
     *
     * @return true when two previously distinct components were merged
     * @throws NoSuchElementException when either value is not present
     */
    public boolean union(T first, T second) {
        Node<T> firstRoot = findRoot(requireStoredNode(first));
        Node<T> secondRoot = findRoot(requireStoredNode(second));

        if (firstRoot == secondRoot) {
            return false;
        }

        if (firstRoot.size < secondRoot.size) {
            Node<T> temporary = firstRoot;
            firstRoot = secondRoot;
            secondRoot = temporary;
        }

        secondRoot.parent = firstRoot;
        firstRoot.size += secondRoot.size;
        componentCount--;
        return true;
    }

    /**
     * Returns whether two stored values belong to the same component.
     *
     * @throws NoSuchElementException when either value is not present
     */
    public boolean connected(T first, T second) {
        return findRoot(requireStoredNode(first))
            == findRoot(requireStoredNode(second));
    }

    /**
     * Returns the number of elements in the component containing value.
     *
     * @throws NoSuchElementException when value is not present
     */
    public int componentSize(T value) {
        return findRoot(requireStoredNode(value)).size;
    }

    /** Returns a shallow snapshot of all stored elements in insertion order. */
    public List<T> elements() {
        return nodes.keysSnapshot();
    }

    /**
     * Returns one representative per component.
     *
     * <p>Component order follows the first inserted element encountered for
     * each component. The returned value itself is the actual current root.</p>
     */
    public List<T> representatives() {
        List<T> result = new List<>(componentCount);
        Dictionary<Node<T>, Boolean> seen = new Dictionary<>(componentCount);

        for (T value : nodes) {
            Node<T> root = findRoot(nodes.get(value));
            if (!seen.containsKey(root)) {
                seen.put(root, Boolean.TRUE);
                result.append(root.value);
            }
        }

        return result;
    }

    /**
     * Returns component snapshots in deterministic insertion order.
     *
     * <p>Components are ordered by the first stored element belonging to each
     * component. Elements inside a component retain global insertion order.</p>
     */
    public List<List<T>> components() {
        Dictionary<Node<T>, List<T>> grouped = new Dictionary<>(componentCount);

        for (T value : nodes) {
            Node<T> root = findRoot(nodes.get(value));
            List<T> component = grouped.get(root, null);
            if (component == null) {
                component = new List<>();
                grouped.put(root, component);
            }
            component.append(value);
        }

        return grouped.valuesSnapshot();
    }

    /** Removes all elements and components. */
    public void clear() {
        if (nodes.isEmpty()) {
            return;
        }

        nodes.clear();
        componentCount = 0;
        modCount++;
    }

    /**
     * Returns an independent shallow copy preserving insertion order,
     * partitioning and representative values.
     */
    public DisjointSet<T> copy() {
        DisjointSet<T> result = new DisjointSet<>();

        for (T value : nodes) {
            result.add(value);
        }

        for (T value : result.nodes) {
            result.nodes.get(value).size = 0;
        }

        for (T value : nodes) {
            Node<T> sourceNode = nodes.get(value);
            Node<T> sourceRoot = findRoot(sourceNode);
            Node<T> targetNode = result.nodes.get(value);
            Node<T> targetRoot = result.nodes.get(sourceRoot.value);

            targetNode.parent = targetRoot;
            targetRoot.size++;
        }

        result.componentCount = componentCount;
        result.modCount = 0;
        return result;
    }

    @Override
    public Iterator<T> iterator() {
        Iterator<T> delegate = nodes.iterator();
        int expectedModCount = modCount;

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                checkForModification(expectedModCount);
                return delegate.hasNext();
            }

            @Override
            public T next() {
                checkForModification(expectedModCount);
                return delegate.next();
            }
        };
    }

    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(
            iterator(),
            size(),
            SPLITERATOR_CHARACTERISTICS
        );
    }

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
    public String toString() {
        return PythonRepr.container(
            this,
            "DisjointSet(...)",
            () -> "DisjointSet(" + PythonRepr.format(components()) + ")"
        );
    }

    private Node<T> requireStoredNode(T value) {
        requireValue(value);
        Node<T> node = nodes.get(value, null);
        if (node == null) {
            throw new NoSuchElementException(
                "Element not found in disjoint set: " + PythonRepr.format(value)
            );
        }
        return node;
    }

    private void requireValue(T value) {
        Objects.requireNonNull(value, "value");
    }

    private Node<T> findRoot(Node<T> node) {
        Node<T> root = node;
        while (root.parent != root) {
            root = root.parent;
        }

        Node<T> current = node;
        while (current.parent != current) {
            Node<T> next = current.parent;
            current.parent = root;
            current = next;
        }

        return root;
    }

    private void checkForModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    private static final class Node<T> {
        private final T value;
        private Node<T> parent;
        private int size = 1;

        private Node(T value) {
            this.value = value;
            parent = this;
        }
    }
}
