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
 * A self-balancing AVL search tree with ordered-set semantics.
 *
 * <p>Values are unique according to the configured ordering. Natural ordering
 * is used when no comparator is supplied; null values are rejected. Each node
 * stores its subtree height, and insertions/removals repair AVL balance with
 * LL, RR, LR and RL rotations.</p>
 *
 * <p>Search, insertion, removal and ordered navigation are O(log n). In-order
 * traversal is sorted according to the configured comparator. The node
 * representation is private and the class uses object identity for equals and
 * hashCode.</p>
 *
 * <p>This class is not thread-safe. Iterators and spliterators are fail-fast on
 * a best-effort basis and do not support removal.</p>
 *
 * @param <T> element type
 */
public final class AVLTree<T> implements Iterable<T> {
    private Node<T> root;
    private int size;
    private int modCount;
    private final Comparator<? super T> comparator;

    public AVLTree() {
        this.comparator = null;
    }

    /** A null comparator selects natural ordering. */
    public AVLTree(Comparator<? super T> comparator) {
        this.comparator = comparator;
    }

    public AVLTree(Iterable<? extends T> values) {
        this(values, null);
    }

    public AVLTree(
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
    public static <T> AVLTree<T> of(T... values) {
        Objects.requireNonNull(values, "values");
        AVLTree<T> result = new AVLTree<>();
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
     * Adds one comparator-distinct value.
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

        Node<T> inserted = new Node<>(value, parent);
        if (comparison < 0) {
            parent.left = inserted;
        } else {
            parent.right = inserted;
        }

        size++;
        modCount++;
        rebalance(parent);
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

        Node<T> rebalanceStart = deleteNode(node);
        size--;
        modCount++;
        rebalance(rebalanceStart);
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

    /** Empty tree = 0; a single root = 1. */
    public int height() {
        return heightOf(root);
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
    // Extremes / ordered navigation
    // ============================================================

    public T minimum() {
        requireNonEmpty();
        return minimumNode(root).item;
    }

    public T maximum() {
        requireNonEmpty();
        return maximumNode(root).item;
    }

    public T pollMinimum() {
        if (root == null) {
            return null;
        }

        Node<T> node = minimumNode(root);
        T value = node.item;
        Node<T> rebalanceStart = deleteNode(node);
        size--;
        modCount++;
        rebalance(rebalanceStart);
        return value;
    }

    public T pollMaximum() {
        if (root == null) {
            return null;
        }

        Node<T> node = maximumNode(root);
        T value = node.item;
        Node<T> rebalanceStart = deleteNode(node);
        size--;
        modCount++;
        rebalance(rebalanceStart);
        return value;
    }

    public T lower(T value) {
        return bound(value, BoundMode.LOWER);
    }

    public T floor(T value) {
        return bound(value, BoundMode.FLOOR);
    }

    public T ceiling(T value) {
        return bound(value, BoundMode.CEILING);
    }

    public T higher(T value) {
        return bound(value, BoundMode.HIGHER);
    }

    // ============================================================
    // Traversals / conversion
    // ============================================================

    public List<T> inOrder() {
        return new List<>(this);
    }

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

    /** Returns a shallow independent copy preserving exact AVL shape. */
    public AVLTree<T> copy() {
        AVLTree<T> result = new AVLTree<>(comparator);
        if (root == null) {
            return result;
        }

        result.root = new Node<>(root.item, null);
        result.root.height = root.height;
        result.size = size;

        Stack<CopyFrame<T>> stack = new Stack<>();
        stack.push(new CopyFrame<>(root, result.root));

        while (!stack.isEmpty()) {
            CopyFrame<T> frame = stack.pop();

            if (frame.source.right != null) {
                frame.destination.right =
                    new Node<>(frame.source.right.item, frame.destination);
                frame.destination.right.height = frame.source.right.height;
                stack.push(new CopyFrame<>(
                    frame.source.right,
                    frame.destination.right
                ));
            }

            if (frame.source.left != null) {
                frame.destination.left =
                    new Node<>(frame.source.left.item, frame.destination);
                frame.destination.left.height = frame.source.left.height;
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
        return new TreeIterator(false);
    }

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

    public Stream<T> stream() {
        return StreamSupport.stream(
            this::spliterator,
            treeCharacteristics(),
            false
        );
    }

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
            "AVLTree([",
            "])",
            "AVLTree([...])",
            false
        );
    }

    // ============================================================
    // Iterators
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
                iterator = AVLTree.this.iterator();
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
    // AVL balancing
    // ============================================================

    private void rebalance(Node<T> start) {
        Node<T> current = start;

        while (current != null) {
            updateHeight(current);
            int balance = balanceFactor(current);

            if (balance > 1) {
                if (balanceFactor(current.left) < 0) {
                    rotateLeft(current.left);
                }
                current = rotateRight(current);
            } else if (balance < -1) {
                if (balanceFactor(current.right) > 0) {
                    rotateRight(current.right);
                }
                current = rotateLeft(current);
            }

            current = current.parent;
        }
    }

    private Node<T> rotateLeft(Node<T> pivot) {
        Node<T> newRoot = pivot.right;
        if (newRoot == null) {
            throw new IllegalStateException("Cannot left-rotate without a right child.");
        }

        Node<T> parent = pivot.parent;
        Node<T> transferred = newRoot.left;

        replaceParentLink(parent, pivot, newRoot);
        newRoot.left = pivot;
        pivot.parent = newRoot;

        pivot.right = transferred;
        if (transferred != null) {
            transferred.parent = pivot;
        }

        updateHeight(pivot);
        updateHeight(newRoot);
        return newRoot;
    }

    private Node<T> rotateRight(Node<T> pivot) {
        Node<T> newRoot = pivot.left;
        if (newRoot == null) {
            throw new IllegalStateException("Cannot right-rotate without a left child.");
        }

        Node<T> parent = pivot.parent;
        Node<T> transferred = newRoot.right;

        replaceParentLink(parent, pivot, newRoot);
        newRoot.right = pivot;
        pivot.parent = newRoot;

        pivot.left = transferred;
        if (transferred != null) {
            transferred.parent = pivot;
        }

        updateHeight(pivot);
        updateHeight(newRoot);
        return newRoot;
    }

    private void replaceParentLink(
        Node<T> parent,
        Node<T> oldChild,
        Node<T> newChild
    ) {
        newChild.parent = parent;

        if (parent == null) {
            root = newChild;
        } else if (parent.left == oldChild) {
            parent.left = newChild;
        } else if (parent.right == oldChild) {
            parent.right = newChild;
        } else {
            throw new IllegalStateException("AVL parent/child links are inconsistent.");
        }
    }

    private int balanceFactor(Node<T> node) {
        return node == null ? 0 : heightOf(node.left) - heightOf(node.right);
    }

    private void updateHeight(Node<T> node) {
        node.height = 1 + Math.max(heightOf(node.left), heightOf(node.right));
    }

    private int heightOf(Node<T> node) {
        return node == null ? 0 : node.height;
    }

    // ============================================================
    // Private search / deletion helpers
    // ============================================================

    private void requireNonEmpty() {
        if (root == null) {
            throw new NoSuchElementException("AVL tree is empty.");
        }
    }

    private void requireElement(T value) {
        Objects.requireNonNull(value, "value");
    }

    private void checkForModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException(
                "AVL tree was modified during traversal."
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

    /**
     * Deletes a node and returns the first node whose height may need repair.
     */
    private Node<T> deleteNode(Node<T> target) {
        Node<T> physical = target;

        if (target.left != null && target.right != null) {
            Node<T> successor = minimumNode(target.right);
            target.item = successor.item;
            physical = successor;
        }

        Node<T> replacement =
            physical.left != null ? physical.left : physical.right;
        Node<T> parent = physical.parent;

        if (parent == null) {
            root = replacement;
        } else if (parent.left == physical) {
            parent.left = replacement;
        } else if (parent.right == physical) {
            parent.right = replacement;
        } else {
            throw new IllegalStateException("AVL parent/child links are inconsistent.");
        }

        if (replacement != null) {
            replacement.parent = parent;
        }

        physical.parent = null;
        physical.left = null;
        physical.right = null;

        return parent != null ? parent : replacement;
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

    private int treeCharacteristics() {
        return Spliterator.ORDERED
            | Spliterator.DISTINCT
            | Spliterator.SIZED
            | Spliterator.SUBSIZED
            | Spliterator.NONNULL;
    }

    private enum BoundMode {
        LOWER,
        FLOOR,
        CEILING,
        HIGHER
    }

    private static final class Node<E> {
        private E item;
        private Node<E> parent;
        private Node<E> left;
        private Node<E> right;
        private int height = 1;

        private Node(E item, Node<E> parent) {
            this.item = item;
            this.parent = parent;
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
