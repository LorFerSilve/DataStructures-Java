package DataStructures;

import static DataStructures.TestSupport.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Spliterator;
import java.util.TreeSet;

public final class AVLTreeTests {
    private AVLTreeTests() { }

    public static void run() {
        constructionAndSetSemantics();
        insertionRotations();
        deletionAndRebalancing();
        navigationAndShape();
        traversalsAndCopy();
        comparatorContracts();
        iterationAndStreams();
        randomizedAgainstTreeSet();
    }

    private static void constructionAndSetSemantics() {
        AVLTree<Integer> tree = new AVLTree<>();
        equal(0, tree.size(), "empty size");
        check(tree.isEmpty(), "empty predicate");
        equal(0, tree.height(), "empty height");
        equal(null, tree.comparator(), "natural comparator marker");
        throwsType(NoSuchElementException.class, tree::minimum);
        throwsType(NoSuchElementException.class, tree::maximum);
        equal(null, tree.pollMinimum(), "empty minimum poll");
        equal(null, tree.pollMaximum(), "empty maximum poll");
        throwsType(NullPointerException.class, () -> tree.add(null));
        throwsType(NullPointerException.class, () -> tree.contains(null));
        throwsType(NullPointerException.class, () -> tree.remove(null));
        throwsType(NullPointerException.class,
            () -> new AVLTree<Integer>((Iterable<Integer>) null));

        check(tree.add(4), "first insertion");
        check(tree.add(2), "left insertion");
        check(tree.add(6), "right insertion");
        check(!tree.add(4), "duplicate insertion ignored");
        equal(3, tree.size(), "unique size");
        equal(2, tree.height(), "three-node height");
        check(tree.contains(2), "contains value");
        check(!tree.contains(5), "missing value");
        equal(2, tree.minimum(), "minimum");
        equal(6, tree.maximum(), "maximum");
        equal(0, tree.depth(4), "root depth");
        equal(1, tree.depth(2), "child depth");
        equal(-1, tree.depth(5), "missing depth");

        AVLTree<Integer> factory = AVLTree.of(3, 1, 4, 1, 5);
        equal(java.util.List.of(1, 3, 4, 5), factory.toJavaList(),
            "factory deduplicates");

        AVLTree<Integer> iterable =
            new AVLTree<>(java.util.List.of(5, 2, 8, 2));
        equal(java.util.List.of(2, 5, 8), iterable.toJavaList(),
            "iterable constructor");
    }

    private static void insertionRotations() {
        AVLTree<Integer> ll = AVLTree.of(30, 20, 10);
        equal(List.of(20, 10, 30), ll.levelOrder(), "LL rotation");

        AVLTree<Integer> rr = AVLTree.of(10, 20, 30);
        equal(List.of(20, 10, 30), rr.levelOrder(), "RR rotation");

        AVLTree<Integer> lr = AVLTree.of(30, 10, 20);
        equal(List.of(20, 10, 30), lr.levelOrder(), "LR rotation");

        AVLTree<Integer> rl = AVLTree.of(10, 30, 20);
        equal(List.of(20, 10, 30), rl.levelOrder(), "RL rotation");

        AVLTree<Integer> ascending = new AVLTree<>();
        for (int i = 1; i <= 1_000; i++) {
            ascending.add(i);
        }

        equal(1_000, ascending.size(), "ascending insertion size");
        check(ascending.height() <= 11,
            "AVL keeps 1000 ascending inserts logarithmic");
        equal(1, ascending.minimum(), "ascending minimum");
        equal(1_000, ascending.maximum(), "ascending maximum");
    }

    private static void deletionAndRebalancing() {
        AVLTree<Integer> leaf = AVLTree.of(4, 2, 6, 1, 3, 5, 7);
        check(leaf.remove(1), "remove leaf");
        equal(java.util.List.of(2, 3, 4, 5, 6, 7), leaf.toJavaList(),
            "leaf removal");

        AVLTree<Integer> oneChild = AVLTree.of(4, 2, 6, 1);
        check(oneChild.remove(2), "remove one-child node");
        equal(java.util.List.of(1, 4, 6), oneChild.toJavaList(),
            "one-child removal");

        AVLTree<Integer> twoChildren =
            AVLTree.of(8, 4, 12, 2, 6, 10, 14, 5, 7, 9, 11);
        check(twoChildren.remove(4), "remove two-child non-root");
        check(twoChildren.remove(8), "remove two-child root");
        equal(java.util.List.of(2, 5, 6, 7, 9, 10, 11, 12, 14),
            twoChildren.toJavaList(), "two-child removals");
        check(!twoChildren.remove(999), "missing removal");

        AVLTree<Integer> leftHeavy =
            AVLTree.of(9, 5, 10, 0, 6, 11, -1, 1, 2);
        check(leftHeavy.remove(10), "deletion causing right rotation");
        equal(1, leftHeavy.levelOrder().get(0), "deletion rebalanced root");
        check(leftHeavy.height() <= 4, "deletion height remains balanced");

        AVLTree<Integer> extremes =
            AVLTree.of(4, 2, 6, 1, 3, 5, 7);
        equal(1, extremes.pollMinimum(), "poll minimum");
        equal(7, extremes.pollMaximum(), "poll maximum");
        equal(java.util.List.of(2, 3, 4, 5, 6), extremes.toJavaList(),
            "extreme polls preserve order");

        AVLTree<Integer> drain = new AVLTree<>();
        for (int i = 0; i < 200; i++) {
            drain.add(i);
        }
        for (int i = 0; i < 200; i++) {
            check(drain.remove(i), "sequential deletion");
            if (!drain.isEmpty()) {
                check(drain.height() <= avlHeightUpperBound(drain.size()),
                    "height bound while draining");
            }
        }
        check(drain.isEmpty(), "drained tree empty");
    }

    private static void navigationAndShape() {
        AVLTree<Integer> tree =
            AVLTree.of(8, 4, 12, 2, 6, 10, 14, 1, 3, 5, 7);

        check(tree.height() <= avlHeightUpperBound(tree.size()),
            "height within AVL bound");

        equal(null, tree.lower(1), "lower below minimum");
        equal(3, tree.lower(4), "lower exact");
        equal(4, tree.floor(4), "floor exact");
        equal(5, tree.ceiling(5), "ceiling exact");
        equal(5, tree.higher(4), "higher exact");
        equal(null, tree.higher(14), "higher above maximum");

        equal(6, tree.floor(6), "floor stored");
        equal(7, tree.ceiling(7), "ceiling stored");

        for (int value : tree) {
            check(tree.depth(value) >= 0, "stored value has depth");
            check(tree.depth(value) < tree.height(), "depth below height");
        }
    }

    private static void traversalsAndCopy() {
        AVLTree<Integer> tree = AVLTree.of(30, 20, 40, 10, 25, 35, 50);

        equal(List.of(10, 20, 25, 30, 35, 40, 50),
            tree.inOrder(), "in-order traversal");
        equal(List.of(30, 20, 10, 25, 40, 35, 50),
            tree.preOrder(), "pre-order traversal");
        equal(List.of(10, 25, 20, 35, 50, 40, 30),
            tree.postOrder(), "post-order traversal");
        equal(List.of(30, 20, 40, 10, 25, 35, 50),
            tree.levelOrder(), "level-order traversal");

        AVLTree<Integer> copy = tree.copy();
        check(copy != tree, "copy identity");
        equal(tree.inOrder(), copy.inOrder(), "copy contents");
        equal(tree.preOrder(), copy.preOrder(), "copy shape");
        equal(tree.height(), copy.height(), "copy height metadata");

        tree.remove(30);
        check(copy.contains(30), "copy independent");
        copy.add(45);
        check(!tree.contains(45), "source independent");

        Object[] array = copy.toArray();
        equal(copy.size(), array.length, "array size");
        equal(copy.inOrder(), copy.toDataList(), "custom-list conversion");

        java.util.List<Integer> javaList = copy.toJavaList();
        throwsType(UnsupportedOperationException.class, () -> javaList.add(99));
    }

    private static void comparatorContracts() {
        Comparator<Integer> reverse = Comparator.reverseOrder();
        AVLTree<Integer> tree =
            new AVLTree<>(java.util.List.of(1, 4, 2, 3), reverse);

        check(tree.comparator() == reverse, "comparator retained");
        equal(java.util.List.of(4, 3, 2, 1), tree.toJavaList(),
            "reverse comparator order");
        equal(4, tree.minimum(), "minimum follows comparator");
        equal(1, tree.maximum(), "maximum follows comparator");

        AVLTree<String> caseInsensitive =
            new AVLTree<>(String.CASE_INSENSITIVE_ORDER);
        check(caseInsensitive.add("Alpha"), "custom comparator insertion");
        check(!caseInsensitive.add("alpha"),
            "comparator equality deduplicates");
        check(caseInsensitive.contains("ALPHA"),
            "contains follows comparator equality");

        AVLTree<Object> nonComparable = new AVLTree<>();
        nonComparable.add(new Object());
        throwsType(ClassCastException.class,
            () -> nonComparable.add(new Object()));

        final class TreeHolder {
            private AVLTree<Integer> tree;
        }

        TreeHolder holder = new TreeHolder();
        Comparator<Integer> mutating = (left, right) -> {
            holder.tree.clear();
            return Integer.compare(left, right);
        };

        holder.tree = new AVLTree<>(mutating);
        holder.tree.add(10);
        throwsType(ConcurrentModificationException.class,
            () -> holder.tree.add(5));
        check(holder.tree.isEmpty(),
            "comparator mutation retained but detected");
    }

    private static void iterationAndStreams() {
        AVLTree<Integer> tree = AVLTree.of(8, 4, 12, 2, 6, 10, 14);

        equal(java.util.List.of(2, 4, 6, 8, 10, 12, 14),
            collect(tree.iterator()), "ascending iterator");
        equal(java.util.List.of(14, 12, 10, 8, 6, 4, 2),
            collect(tree.descendingIterator()), "descending iterator");
        equal(java.util.List.of(14, 12, 10, 8, 6, 4, 2),
            collect(tree.reversed().iterator()), "reversed iterable");

        Iterator<Integer> stale = tree.iterator();
        equal(2, stale.next(), "iterator first");
        tree.add(5);
        throwsType(ConcurrentModificationException.class, stale::hasNext);
        throwsType(ConcurrentModificationException.class, stale::next);

        Iterator<Integer> cannotRemove = tree.iterator();
        cannotRemove.next();
        throwsType(UnsupportedOperationException.class, cannotRemove::remove);

        var stream = tree.stream();
        tree.add(13);
        equal(tree.toJavaList(), stream.toList(), "stream late binding");
        equal((long) tree.size(), tree.parallelStream().count(),
            "parallel stream count");

        Spliterator<Integer> split = tree.spliterator();
        int characteristics = Spliterator.ORDERED | Spliterator.DISTINCT
            | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL;
        check(split.hasCharacteristics(characteristics),
            "spliterator characteristics");
        equal((long) tree.size(), split.getExactSizeIfKnown(),
            "spliterator exact size");

        Spliterator<Integer> prefix = split.trySplit();
        check(prefix != null, "spliterator splits");

        ArrayList<Integer> combined = new ArrayList<>();
        prefix.forEachRemaining(combined::add);
        split.forEachRemaining(combined::add);
        equal(tree.toJavaList(), combined, "split order");

        Spliterator<Integer> bound = tree.spliterator();
        bound.estimateSize();
        tree.remove(8);
        throwsType(ConcurrentModificationException.class, bound::estimateSize);
        throwsType(ConcurrentModificationException.class, bound::trySplit);

        Spliterator<Integer> callback = tree.spliterator();
        throwsType(ConcurrentModificationException.class,
            () -> callback.tryAdvance(value -> tree.clear()));
    }

    private static void randomizedAgainstTreeSet() {
        AVLTree<Integer> actual = new AVLTree<>();
        TreeSet<Integer> expected = new TreeSet<>();
        Random random = new Random(0xA71A71L);

        for (int step = 0; step < 30_000; step++) {
            int value = random.nextInt(801) - 400;

            switch (random.nextInt(14)) {
                case 0, 1, 2 ->
                    equal(expected.add(value), actual.add(value), "random add");
                case 3, 4 ->
                    equal(expected.remove(value), actual.remove(value), "random remove");
                case 5 ->
                    equal(expected.contains(value), actual.contains(value),
                        "random contains");
                case 6 ->
                    equal(expected.lower(value), actual.lower(value), "random lower");
                case 7 ->
                    equal(expected.floor(value), actual.floor(value), "random floor");
                case 8 ->
                    equal(expected.ceiling(value), actual.ceiling(value),
                        "random ceiling");
                case 9 ->
                    equal(expected.higher(value), actual.higher(value),
                        "random higher");
                case 10 ->
                    equal(expected.pollFirst(), actual.pollMinimum(),
                        "random poll minimum");
                case 11 ->
                    equal(expected.pollLast(), actual.pollMaximum(),
                        "random poll maximum");
                case 12 -> {
                    if (random.nextInt(25) == 0) {
                        expected.clear();
                        actual.clear();
                    }
                }
                case 13 ->
                    equal(new ArrayList<>(expected), actual.stream().toList(),
                        "random stream");
                default -> throw new AssertionError("Unknown operation");
            }

            assertContents(actual, expected);
        }
    }

    private static void assertContents(
        AVLTree<Integer> actual,
        TreeSet<Integer> expected
    ) {
        equal(expected.size(), actual.size(), "size");
        equal(expected.isEmpty(), actual.isEmpty(), "emptiness");
        equal(new ArrayList<>(expected), actual.toJavaList(), "sorted snapshot");
        equal(new ArrayList<>(expected), collect(actual.iterator()),
            "sorted iterator");

        ArrayList<Integer> reversed = new ArrayList<>(expected);
        Collections.reverse(reversed);
        equal(reversed, collect(actual.descendingIterator()),
            "descending snapshot");

        if (expected.isEmpty()) {
            equal(0, actual.height(), "empty height");
        } else {
            equal(expected.first(), actual.minimum(), "minimum");
            equal(expected.last(), actual.maximum(), "maximum");
            check(actual.height() <= avlHeightUpperBound(actual.size()),
                "AVL logarithmic height bound");
        }
    }

    /**
     * Safe integral upper bound derived from the AVL minimum-node recurrence.
     * It is deliberately a little looser than the exact theoretical bound.
     */
    private static int avlHeightUpperBound(int size) {
        if (size == 0) {
            return 0;
        }
        return (int) Math.ceil(1.45 * (Math.log(size + 2) / Math.log(2))) + 1;
    }

    private static <T> java.util.List<T> collect(Iterator<T> iterator) {
        ArrayList<T> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }
}
