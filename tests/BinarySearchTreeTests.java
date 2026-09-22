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

public final class BinarySearchTreeTests {
    private BinarySearchTreeTests() { }

    public static void run() {
        constructionAndBasicSetSemantics();
        deletionCases();
        navigationAndShape();
        traversalsAndCopy();
        comparatorContracts();
        iterationAndStreams();
        randomizedAgainstTreeSet();
    }

    private static void constructionAndBasicSetSemantics() {
        BinarySearchTree<Integer> tree = new BinarySearchTree<>();
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
            () -> new BinarySearchTree<Integer>((Iterable<Integer>) null));

        check(tree.add(4), "first insertion");
        check(tree.add(2), "left insertion");
        check(tree.add(6), "right insertion");
        check(!tree.add(4), "duplicate insertion ignored");
        equal(3, tree.size(), "unique size");
        check(tree.contains(2), "contains stored value");
        check(!tree.contains(5), "absent value");
        equal(4, tree.minimum() + tree.maximum() - 4, "extremes callable");
        equal(2, tree.minimum(), "minimum");
        equal(6, tree.maximum(), "maximum");
        equal(0, tree.depth(4), "root depth");
        equal(1, tree.depth(2), "child depth");
        equal(-1, tree.depth(5), "absent depth");

        BinarySearchTree<Integer> factory = BinarySearchTree.of(3, 1, 4, 1, 5);
        equal(java.util.List.of(1, 3, 4, 5), factory.toJavaList(), "factory deduplicates");
        BinarySearchTree<Integer> iterable =
            new BinarySearchTree<>(java.util.List.of(5, 2, 8, 2));
        equal(java.util.List.of(2, 5, 8), iterable.toJavaList(), "iterable constructor");
    }

    private static void deletionCases() {
        BinarySearchTree<Integer> leaf = BinarySearchTree.of(4, 2, 6, 1, 3, 5, 7);
        check(leaf.remove(1), "remove leaf");
        equal(java.util.List.of(2, 3, 4, 5, 6, 7), leaf.toJavaList(), "leaf removal order");

        BinarySearchTree<Integer> oneChild = BinarySearchTree.of(4, 2, 6, 1);
        check(oneChild.remove(2), "remove node with one child");
        equal(java.util.List.of(1, 4, 6), oneChild.toJavaList(), "one-child removal");

        BinarySearchTree<Integer> twoChildren =
            BinarySearchTree.of(8, 4, 12, 2, 6, 10, 14, 5, 7, 9, 11);
        check(twoChildren.remove(4), "remove two-child non-root");
        equal(java.util.List.of(2, 5, 6, 7, 8, 9, 10, 11, 12, 14),
            twoChildren.toJavaList(), "two-child non-root order");
        check(twoChildren.remove(8), "remove two-child root");
        equal(java.util.List.of(2, 5, 6, 7, 9, 10, 11, 12, 14),
            twoChildren.toJavaList(), "two-child root order");
        check(!twoChildren.remove(999), "missing removal");

        BinarySearchTree<Integer> extremes = BinarySearchTree.of(4, 2, 6, 1, 3, 5, 7);
        equal(1, extremes.pollMinimum(), "poll minimum");
        equal(7, extremes.pollMaximum(), "poll maximum");
        equal(java.util.List.of(2, 3, 4, 5, 6), extremes.toJavaList(),
            "extreme removals preserve tree");
    }

    private static void navigationAndShape() {
        BinarySearchTree<Integer> tree =
            BinarySearchTree.of(8, 4, 12, 2, 6, 10, 14, 1, 3, 5, 7);

        equal(4, tree.height(), "balanced-ish height");
        equal(3, tree.depth(1), "deep node depth");

        equal(null, tree.lower(1), "lower below minimum");
        equal(3, tree.lower(4), "lower exact");
        equal(4, tree.floor(4), "floor exact");
        equal(3, tree.floor(3), "floor exact second");
        equal(5, tree.ceiling(5), "ceiling exact");
        equal(6, tree.ceiling(6), "ceiling exact second");
        equal(5, tree.higher(4), "higher exact");
        equal(null, tree.higher(14), "higher above maximum");
        equal(7, tree.floor(7), "floor stored");
        equal(8, tree.ceiling(8), "ceiling stored");

        equal(6, tree.floor(6), "floor comparator path");
        equal(7, tree.ceiling(7), "ceiling comparator path");

        BinarySearchTree<Integer> chain = new BinarySearchTree<>();
        for (int i = 0; i < 8; i++) {
            chain.add(i);
        }
        equal(8, chain.height(), "degenerate height");
        equal(7, chain.depth(7), "degenerate depth");
    }

    private static void traversalsAndCopy() {
        BinarySearchTree<Integer> tree =
            BinarySearchTree.of(8, 4, 12, 2, 6, 10, 14, 1, 3, 5, 7);

        equal(List.of(1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14),
            tree.inOrder(), "in-order traversal");
        equal(List.of(8, 4, 2, 1, 3, 6, 5, 7, 12, 10, 14),
            tree.preOrder(), "pre-order traversal");
        equal(List.of(1, 3, 2, 5, 7, 6, 4, 10, 14, 12, 8),
            tree.postOrder(), "post-order traversal");
        equal(List.of(8, 4, 12, 2, 6, 10, 14, 1, 3, 5, 7),
            tree.levelOrder(), "level-order traversal");

        BinarySearchTree<Integer> copy = tree.copy();
        check(copy != tree, "copy identity");
        equal(tree.inOrder(), copy.inOrder(), "copy content");
        equal(tree.preOrder(), copy.preOrder(), "copy preserves shape");
        equal(tree.height(), copy.height(), "copy preserves height");
        tree.remove(8);
        check(copy.contains(8), "copy independent from source");
        copy.add(13);
        check(!tree.contains(13), "source independent from copy");

        Object[] array = copy.toArray();
        equal(copy.size(), array.length, "array size");
        equal(copy.inOrder(), copy.toDataList(), "custom list conversion");
        java.util.List<Integer> javaList = copy.toJavaList();
        throwsType(UnsupportedOperationException.class, () -> javaList.add(99));
    }

    private static void comparatorContracts() {
        Comparator<Integer> reverse = Comparator.reverseOrder();
        BinarySearchTree<Integer> tree =
            new BinarySearchTree<>(java.util.List.of(1, 4, 2, 3), reverse);

        check(tree.comparator() == reverse, "comparator retained");
        equal(java.util.List.of(4, 3, 2, 1), tree.toJavaList(), "reverse comparator order");
        equal(4, tree.minimum(), "minimum follows comparator");
        equal(1, tree.maximum(), "maximum follows comparator");

        BinarySearchTree<String> caseInsensitive =
            new BinarySearchTree<>(String.CASE_INSENSITIVE_ORDER);
        check(caseInsensitive.add("Alpha"), "custom comparator first");
        check(!caseInsensitive.add("alpha"), "comparator equality deduplicates");
        check(caseInsensitive.contains("ALPHA"), "contains uses comparator");
        equal("Alpha", caseInsensitive.minimum(), "stored representative retained");

        BinarySearchTree<Object> nonComparable = new BinarySearchTree<>();
        nonComparable.add(new Object());
        throwsType(ClassCastException.class, () -> nonComparable.add(new Object()));

        final class TreeHolder {
            private BinarySearchTree<Integer> tree;
        }
        TreeHolder holder = new TreeHolder();
        Comparator<Integer> mutating = (left, right) -> {
            holder.tree.clear();
            return Integer.compare(left, right);
        };
        holder.tree = new BinarySearchTree<>(mutating);
        holder.tree.add(10);
        throwsType(ConcurrentModificationException.class, () -> holder.tree.add(5));
        check(holder.tree.isEmpty(), "comparator mutation retained but detected");
    }

    private static void iterationAndStreams() {
        BinarySearchTree<Integer> tree =
            BinarySearchTree.of(8, 4, 12, 2, 6, 10, 14);

        equal(java.util.List.of(2, 4, 6, 8, 10, 12, 14),
            collect(tree.iterator()), "ascending iterator");
        equal(java.util.List.of(14, 12, 10, 8, 6, 4, 2),
            collect(tree.descendingIterator()), "descending iterator");
        equal(java.util.List.of(14, 12, 10, 8, 6, 4, 2),
            collect(tree.reversed().iterator()), "reversed iterable");

        Iterator<Integer> iterator = tree.iterator();
        equal(2, iterator.next(), "iterator first");
        tree.add(5);
        throwsType(ConcurrentModificationException.class, iterator::hasNext);
        throwsType(ConcurrentModificationException.class, iterator::next);

        Iterator<Integer> cannotRemove = tree.iterator();
        cannotRemove.next();
        throwsType(UnsupportedOperationException.class, cannotRemove::remove);

        var stream = tree.stream();
        tree.add(13);
        equal(tree.toJavaList(), stream.toList(), "stream late binding");
        equal((long) tree.size(), tree.parallelStream().count(), "parallel stream count");

        Spliterator<Integer> split = tree.spliterator();
        int characteristics = Spliterator.ORDERED | Spliterator.DISTINCT
            | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL;
        check(split.hasCharacteristics(characteristics), "spliterator characteristics");
        equal((long) tree.size(), split.getExactSizeIfKnown(), "spliterator exact size");

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
        BinarySearchTree<Integer> actual = new BinarySearchTree<>();
        TreeSet<Integer> expected = new TreeSet<>();
        Random random = new Random(0xB57B57L);

        for (int step = 0; step < 20_000; step++) {
            int value = random.nextInt(401) - 200;

            switch (random.nextInt(14)) {
                case 0, 1, 2 -> equal(expected.add(value), actual.add(value), "random add");
                case 3, 4 -> equal(expected.remove(value), actual.remove(value), "random remove");
                case 5 -> equal(expected.contains(value), actual.contains(value), "random contains");
                case 6 -> equal(expected.lower(value), actual.lower(value), "random lower");
                case 7 -> equal(expected.floor(value), actual.floor(value), "random floor");
                case 8 -> equal(expected.ceiling(value), actual.ceiling(value), "random ceiling");
                case 9 -> equal(expected.higher(value), actual.higher(value), "random higher");
                case 10 -> {
                    Integer reference = expected.pollFirst();
                    equal(reference, actual.pollMinimum(), "random poll minimum");
                }
                case 11 -> {
                    Integer reference = expected.pollLast();
                    equal(reference, actual.pollMaximum(), "random poll maximum");
                }
                case 12 -> {
                    if (random.nextInt(20) == 0) {
                        expected.clear();
                        actual.clear();
                    }
                }
                case 13 -> equal(new ArrayList<>(expected), actual.stream().toList(),
                    "random stream");
                default -> throw new AssertionError("Unknown operation");
            }

            assertContents(actual, expected);
        }
    }

    private static void assertContents(
        BinarySearchTree<Integer> actual,
        TreeSet<Integer> expected
    ) {
        equal(expected.size(), actual.size(), "size");
        equal(expected.isEmpty(), actual.isEmpty(), "emptiness");
        equal(new ArrayList<>(expected), actual.toJavaList(), "sorted snapshot");
        equal(new ArrayList<>(expected), collect(actual.iterator()), "sorted iterator");

        ArrayList<Integer> reversed = new ArrayList<>(expected);
        Collections.reverse(reversed);
        equal(reversed, collect(actual.descendingIterator()), "descending snapshot");

        if (expected.isEmpty()) {
            equal(0, actual.height(), "empty height");
        } else {
            equal(expected.first(), actual.minimum(), "minimum");
            equal(expected.last(), actual.maximum(), "maximum");
            check(actual.height() >= 1 && actual.height() <= actual.size(), "height bounds");
        }
    }

    private static <T> java.util.List<T> collect(Iterator<T> iterator) {
        ArrayList<T> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }
}
