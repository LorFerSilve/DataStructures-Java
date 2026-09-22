package DataStructures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * A pointer-based binary search tree with set semantics.
 *
 * <p>Values are unique according to the configured ordering: adding a value
 * that compares equal to an existing value returns false and leaves the tree
 * unchanged. Null values are rejected. Natural ordering is used when no
 * comparator is supplied.</p>
 *
 * <p>Search, insertion and removal are O(h), where h is the tree height.
 * This class deliberately does not self-balance, so h can range from O(log n)
 * for a well-shaped tree to O(n) for a degenerate tree. In-order traversal is
 * sorted according to the tree comparator.</p>
 *
 * <p>The node representation is private. This class is not thread-safe.
 * Iterators and spliterators are fail-fast on a best-effort basis and do not
 * support removal. Equality and hashing use object identity.</p>
 *
 * @param <T> element type
 */
public final class BinarySearchTree<T> implements Iterable<T> {
    private Node<T> root;
    private int size;
    private int modCount;
    private final Comparator<? super T> comparator;

    public BinarySearchTree() {
        this(null);
    }

    /** A null comparator selects natural ordering. */
    public BinarySearchTree(Comparator<? super T> comparator) {
        this.comparator = comparator;
    }

    public BinarySearchTree(Iterable<? extends T> values) {
        this(values, null);
    }

    public BinarySearchTree(
        Iterable<? extends T> values,
        Comparator<? super T> comparator
    ) {
        this(comparator);
        Objects.requireNonNull(values, "values");
        for (T value : values) {
            add(value);
        }
        modCount = 0;
    }

    @SafeVarargs
    public static <T> BinarySearchTree<T> of(T... values) {
        Objects.requireNonNull(values, "values");
        BinarySearchTree<T> result = new BinarySearchTree<>();
        for (T value : values) {
            result.add(value);
        }
        result.modCount = 0;
        return result;
    }

    // ============================================================
    // Core mutation / lookup
    // ============================================================

    /**
     * Adds a value if no comparator-equal value is already stored.
     *
     * @return true when the tree changed
     */
    public boolean add(T value) {
        requireElement(value);

        if (root == null) {
            root = new Node<>(value, null);
            size = 1;
            modCount++;
            return true;
        }

        int expectedModCount = modCount;
        Node<T> parent = null;
        Node<T> current = root;
        int comparison = 0;

        while (current != null) {
            parent = current;
            comparison = compare(value, current.item, expectedModCount);
            if (comparison == 0) {
                return false;
            }
            current = comparison < 0 ? current.left : current.right;
        }

        Node<T> node = new Node<>(value, parent);
        if (comparison < 0) {
            parent.left = node;
        } else {
            parent.right = node;
        }

        size++;
        modCount++;
        return true;
    }

    public boolean contains(T value) {
        requireElement(value);
        return findNode(value, modCount) != null;
    }

    /**
     * Removes the comparator-equal stored value.
     *
     * @return true when a value was removed
     */
    public boolean remove(T value) {
        requireElement(value);
        Node<T> node = findNode(value, modCount);
        if (node == null) {
            return false;
        }

        deleteNode(node);
        size--;
        modCount++;
        return true;
    }

    public void clear() {
        if (root != null) {
            root = null;
            size = 0;
            modCount++;
        }
    }

    // ============================================================
    // Size / shape / ordering
    // ============================================================

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the number of node levels in the longest root-to-leaf path.
     * An empty tree has height 0 and a single-node tree has height 1.
     */
    public int height() {
        if (root == null) {
            return 0;
        }

        Queue<NodeDepth<T>> queue = new Queue<>();
        queue.add(new NodeDepth<>(root, 1));
        int maximum = 0;

        while (!queue.isEmpty()) {
            NodeDepth<T> current = queue.remove();
            maximum = Math.max(maximum, current.depth);
            if (current.node.left != null) {
                queue.add(new NodeDepth<>(current.node.left, current.depth + 1));
            }
            if (current.node.right != null) {
                queue.add(new NodeDepth<>(current.node.right, current.depth + 1));
            }
        }

        return maximum;
    }

    /** Returns zero for the root and -1 when the value is absent. */
    public int depth(T value) {
        requireElement(value);
        int expectedModCount = modCount;
        Node<T> current = root;
        int depth = 0;

        while (current != null) {
            int comparison = compare(value, current.item, expectedModCount);
            if (comparison == 0) {
                return depth;
            }
            current = comparison < 0 ? current.left : current.right;
            depth++;
        }

        return -1;
    }

    /** Returns the comparator, or null when natural ordering is used. */
    public Comparator<? super T> comparator() {
        return comparator;
    }

    // ============================================================
    // Extremes / navigation
    // ============================================================

    public T minimum() {
        requireNonEmpty();
        return minimumNode(root).item;
    }

    public T maximum() {
        requireNonEmpty();
        return maximumNode(root).item;
    }

    /** Removes and returns the minimum value, or null when empty. */
    public T pollMinimum() {
        if (root == null) {
            return null;
        }
        Node<T> node = minimumNode(root);
        T value = node.item;
        deleteNode(node);
        size--;
        modCount++;
        return value;
    }

    /** Removes and returns the maximum value, or null when empty. */
    public T pollMaximum() {
        if (root == null) {
            return null;
        }
        Node<T> node = maximumNode(root);
        T value = node.item;
        deleteNode(node);
        size--;
        modCount++;
        return value;
    }

    /** Greatest stored value strictly less than value, or null when absent. */
    public T lower(T value) {
        return bound(value, BoundMode.LOWER);
    }

    /** Greatest stored value less than or equal to value, or null when absent. */
    public T floor(T value) {
        return bound(value, BoundMode.FLOOR);
    }

    /** Smallest stored value greater than or equal to value, or null when absent. */
    public T ceiling(T value) {
        return bound(value, BoundMode.CEILING);
    }

    /** Smallest stored value strictly greater than value, or null when absent. */
    public T higher(T value) {
        return bound(value, BoundMode.HIGHER);
    }

    // ============================================================
    // Traversals / conversion
    // ============================================================

    /** Returns values in comparator-sorted order. */
    public List<T> inOrder() {
        return new List<>(this);
    }

    /** Returns root-left-right traversal. */
    public List<T> preOrder() {
        List<T> result = new List<>(size);
        if (root == null) {
            return result;
        }

        Stack<Node<T>> stack = new Stack<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node<T> node = stack.pop();
            result.append(node.item);
            if (node.right != null) {
                stack.push(node.right);
            }
            if (node.left != null) {
                stack.push(node.left);
            }
        }
        return result;
    }

    /** Returns left-right-root traversal. */
    public List<T> postOrder() {
        List<T> result = new List<>(size);
        if (root == null) {
            return result;
        }

        Stack<Node<T>> pending = new Stack<>();
        Stack<Node<T>> reverse = new Stack<>();
        pending.push(root);

        while (!pending.isEmpty()) {
            Node<T> node = pending.pop();
            reverse.push(node);
            if (node.left != null) {
                pending.push(node.left);
            }
            if (node.right != null) {
                pending.push(node.right);
            }
        }

        while (!reverse.isEmpty()) {
            result.append(reverse.pop().item);
        }
        return result;
    }

    /** Returns breadth-first traversal from the root. */
    public List<T> levelOrder() {
        List<T> result = new List<>(size);
        if (root == null) {
            return result;
        }

        Queue<Node<T>> queue = new Queue<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node<T> node = queue.remove();
            result.append(node.item);
            if (node.left != null) {
                queue.add(node.left);
            }
            if (node.right != null) {
                queue.add(node.right);
            }
        }
        return result;
    }

    /** Returns an independent shallow copy preserving exact tree shape. */
    public BinarySearchTree<T> copy() {
        BinarySearchTree<T> result = new BinarySearchTree<>(comparator);
        if (root == null) {
            return result;
        }

        result.root = new Node<>(root.item, null);
        result.size = size;

        Stack<CopyFrame<T>> stack = new Stack<>();
        stack.push(new CopyFrame<>(root, result.root));

        while (!stack.isEmpty()) {
            CopyFrame<T> frame = stack.pop();

            if (frame.source.right != null) {
                frame.destination.right =
                    new Node<>(frame.source.right.item, frame.destination);
                stack.push(new CopyFrame<>(
                    frame.source.right,
                    frame.destination.right
                ));
            }

            if (frame.source.left != null) {
                frame.destination.left =
                    new Node<>(frame.source.left.item, frame.destination);
                stack.push(new CopyFrame<>(
                    frame.source.left,
                    frame.destination.left
                ));
            }
        }

        return result;
    }

    public Object[] toArray() {
        return inOrder().toArray();
    }

    public List<T> toDataList() {
        return inOrder();
    }

    /** Returns an unmodifiable shallow snapshot in sorted order. */
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

    /** Iterates in ascending comparator order. */
    @Override
    public Iterator<T> iterator() {
        return new TreeIterator(false);
    }

    /** Iterates in descending comparator order. */
    public Iterator<T> descendingIterator() {
        return new TreeIterator(true);
    }

    public Iterable<T> reversed() {
        return this::descendingIterator;
    }

    @Override
    public Spliterator<T> spliterator() {
        return new TreeSpliterator();
    }

    /** Late-binding stream in ascending comparator order. */
    public Stream<T> stream() {
        return StreamSupport.stream(
            this::spliterator,
            treeCharacteristics(),
            false
        );
    }

    /** Late-binding parallel stream in ascending comparator order. */
    public Stream<T> parallelStream() {
        return StreamSupport.stream(
            this::spliterator,
            treeCharacteristics(),
            true
        );
    }

    @Override
    public String toString() {
        return PythonRepr.sequence(
            this,
            "BinarySearchTree([",
            "])",
            "BinarySearchTree([...])",
            false
        );
    }

    // ============================================================
    // Iterator / spliterator implementations
    // ============================================================

    private final class TreeIterator implements Iterator<T> {
        private Object[] stack = new Object[16];
        private int stackSize;
        private final boolean descending;
        private final int expectedModCount;

        private TreeIterator(boolean descending) {
            this.descending = descending;
            this.expectedModCount = modCount;
            pushPath(root);
        }

        @Override
        public boolean hasNext() {
            checkForModification(expectedModCount);
            return stackSize != 0;
        }

        @Override
        public T next() {
            checkForModification(expectedModCount);
            if (stackSize == 0) {
                throw new NoSuchElementException();
            }

            Node<T> node = popNode();
            pushPath(descending ? node.left : node.right);
            return node.item;
        }

        private void pushPath(Node<T> node) {
            Node<T> current = node;
            while (current != null) {
                ensureStackCapacity();
                stack[stackSize++] = current;
                current = descending ? current.right : current.left;
            }
        }

        private void ensureStackCapacity() {
            if (stackSize == stack.length) {
                Object[] replacement = new Object[stack.length << 1];
                System.arraycopy(stack, 0, replacement, 0, stack.length);
                stack = replacement;
            }
        }

        @SuppressWarnings("unchecked")
        private Node<T> popNode() {
            int index = --stackSize;
            Node<T> node = (Node<T>) stack[index];
            stack[index] = null;
            return node;
        }
    }

    private final class TreeSpliterator implements Spliterator<T> {
        private Iterator<T> iterator;
        private int remaining = -1;
        private int expectedModCount;

        private int bind() {
            if (remaining < 0) {
                iterator = BinarySearchTree.this.iterator();
                remaining = size;
                expectedModCount = modCount;
            }
            return remaining;
        }

        @Override
        public Spliterator<T> trySplit() {
            int currentRemaining = bind();
            checkForModification(expectedModCount);
            int batchSize = currentRemaining >>> 1;
            if (batchSize == 0) {
                return null;
            }

            ArrayList<T> prefix = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                prefix.add(iterator.next());
            }
            remaining -= batchSize;
            checkForModification(expectedModCount);

            return Spliterators.spliterator(
                prefix.iterator(),
                prefix.size(),
                treeCharacteristics()
            );
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action, "action");
            bind();
            checkForModification(expectedModCount);
            if (remaining == 0) {
                return false;
            }

            T value = iterator.next();
            remaining--;
            action.accept(value);
            checkForModification(expectedModCount);
            return true;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            Objects.requireNonNull(action, "action");
            bind();
            checkForModification(expectedModCount);

            while (remaining > 0) {
                T value = iterator.next();
                remaining--;
                action.accept(value);
                checkForModification(expectedModCount);
            }
        }

        @Override
        public long estimateSize() {
            int currentRemaining = bind();
            checkForModification(expectedModCount);
            return currentRemaining;
        }

        @Override
        public int characteristics() {
            return treeCharacteristics();
        }
    }

    // ============================================================
    // Private tree helpers
    // ============================================================

    private int treeCharacteristics() {
        return Spliterator.ORDERED
            | Spliterator.DISTINCT
            | Spliterator.SIZED
            | Spliterator.SUBSIZED
            | Spliterator.NONNULL;
    }

    private void requireNonEmpty() {
        if (root == null) {
            throw new NoSuchElementException("Binary search tree is empty.");
        }
    }

    private void requireElement(T value) {
        Objects.requireNonNull(value, "value");
    }

    private void checkForModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException(
                "Binary search tree was modified during traversal."
            );
        }
    }

    @SuppressWarnings("unchecked")
    private int compare(T left, T right, int expectedModCount) {
        int result;
        if (comparator != null) {
            result = comparator.compare(left, right);
        } else {
            result = ((Comparable<? super T>) left).compareTo(right);
        }
        checkForModification(expectedModCount);
        return result;
    }

    private Node<T> findNode(T value, int expectedModCount) {
        Node<T> current = root;
        while (current != null) {
            int comparison = compare(value, current.item, expectedModCount);
            if (comparison == 0) {
                return current;
            }
            current = comparison < 0 ? current.left : current.right;
        }
        return null;
    }

    private T bound(T value, BoundMode mode) {
        requireElement(value);
        int expectedModCount = modCount;
        Node<T> current = root;
        Node<T> candidate = null;

        while (current != null) {
            int comparison = compare(value, current.item, expectedModCount);

            if (comparison == 0) {
                return switch (mode) {
                    case FLOOR, CEILING -> current.item;
                    case LOWER -> {
                        Node<T> lower = predecessor(current);
                        yield lower == null ? null : lower.item;
                    }
                    case HIGHER -> {
                        Node<T> higher = successor(current);
                        yield higher == null ? null : higher.item;
                    }
                };
            }

            if (comparison < 0) {
                if (mode == BoundMode.CEILING || mode == BoundMode.HIGHER) {
                    candidate = current;
                }
                current = current.left;
            } else {
                if (mode == BoundMode.FLOOR || mode == BoundMode.LOWER) {
                    candidate = current;
                }
                current = current.right;
            }
        }

        return candidate == null ? null : candidate.item;
    }

    private void deleteNode(Node<T> node) {
        if (node.left == null) {
            transplant(node, node.right);
        } else if (node.right == null) {
            transplant(node, node.left);
        } else {
            Node<T> successor = minimumNode(node.right);

            if (successor.parent != node) {
                transplant(successor, successor.right);
                successor.right = node.right;
                successor.right.parent = successor;
            }

            transplant(node, successor);
            successor.left = node.left;
            successor.left.parent = successor;
        }

        node.parent = null;
        node.left = null;
        node.right = null;
    }

    private void transplant(Node<T> replaced, Node<T> replacement) {
        if (replaced.parent == null) {
            root = replacement;
        } else if (replaced == replaced.parent.left) {
            replaced.parent.left = replacement;
        } else {
            replaced.parent.right = replacement;
        }

        if (replacement != null) {
            replacement.parent = replaced.parent;
        }
    }

    private Node<T> minimumNode(Node<T> start) {
        Node<T> current = start;
        while (current.left != null) {
            current = current.left;
        }
        return current;
    }

    private Node<T> maximumNode(Node<T> start) {
        Node<T> current = start;
        while (current.right != null) {
            current = current.right;
        }
        return current;
    }

    private Node<T> predecessor(Node<T> node) {
        if (node.left != null) {
            return maximumNode(node.left);
        }

        Node<T> current = node;
        Node<T> parent = current.parent;
        while (parent != null && current == parent.left) {
            current = parent;
            parent = parent.parent;
        }
        return parent;
    }

    private Node<T> successor(Node<T> node) {
        if (node.right != null) {
            return minimumNode(node.right);
        }

        Node<T> current = node;
        Node<T> parent = current.parent;
        while (parent != null && current == parent.right) {
            current = parent;
            parent = parent.parent;
        }
        return parent;
    }

    private enum BoundMode {
        LOWER,
        FLOOR,
        CEILING,
        HIGHER
    }

    private static final class Node<E> {
        private final E item;
        private Node<E> parent;
        private Node<E> left;
        private Node<E> right;

        private Node(E item, Node<E> parent) {
            this.item = item;
            this.parent = parent;
        }
    }

    private static final class NodeDepth<E> {
        private final Node<E> node;
        private final int depth;

        private NodeDepth(Node<E> node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }

    private static final class CopyFrame<E> {
        private final Node<E> source;
        private final Node<E> destination;

        private CopyFrame(Node<E> source, Node<E> destination) {
            this.source = source;
            this.destination = destination;
        }
    }
}
