package DataStructures;

import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Dependency-free heap tests, including 36,000 differential operations. */
public final class BinaryHeapTests {
    private BinaryHeapTests() { }

    public static void run() {
        testBasics();
        testOrderingAndConversions();
        testRemovalDirections();
        testDuplicatePriorities();
        testIteratorsAndStreams();
        testComparisonFailures();
        testCallbackModification();
        testComplexity();
        randomized(null, 74291L);
        randomized(Comparator.reverseOrder(), 12512L);
        randomized(Comparator.<Integer>comparingInt(Math::abs)
            .thenComparingInt(Integer::intValue), 89031L);
    }

    private static void testBasics() {
        BinaryHeap<Integer> heap = new BinaryHeap<>(0);
        equal(0, heap.capacity(), "zero initial capacity");
        equal(null, heap.peek(), "empty peek");
        equal(null, heap.poll(), "empty poll");
        check(heap.isEmpty(), "empty heap");
        equal(null, heap.comparator(), "natural comparator");
        expect(NoSuchElementException.class, heap::remove);
        expect(NoSuchElementException.class, heap::element);
        expect(IllegalArgumentException.class, () -> new BinaryHeap<>(-1));
        expect(NullPointerException.class, () -> heap.offer(null));
        expect(NullPointerException.class, () -> heap.add(null));
        expect(NullPointerException.class,
            () -> new BinaryHeap<Integer>((Iterable<Integer>) null));
        expect(NullPointerException.class, () -> BinaryHeap.of((Integer[]) null));
        expect(NullPointerException.class, () -> BinaryHeap.of(1, null, 2));
        expect(ClassCastException.class, () -> new BinaryHeap<>().offer(new Object()));
        BinaryHeap<Object> heterogeneous = new BinaryHeap<>();
        heterogeneous.add(1);
        expect(ClassCastException.class, () -> heterogeneous.add("incompatible"));
        equal(1, heterogeneous.remove(), "incompatible offer keeps contents");
        check(!heap.contains(null) && !heap.remove(null), "null search is absent");
        check(heap.offer(4) && heap.add(2) && heap.add(4), "insertion succeeds");
        equal(3, heap.size(), "size after insertion");
        equal(2, heap.element(), "minimum head");
        equal(2, heap.poll(), "minimum removed");
        check(heap.contains(4) && heap.remove(Integer.valueOf(4)), "remove equal value");
        equal(1, heap.size(), "remove one duplicate");
        equal(4, heap.remove(), "remaining duplicate");
        check(!heap.remove(Integer.valueOf(99)), "absent removal");
        heap.trimToSize();
        equal(0, heap.capacity(), "empty trim");
        heap.offer(7);
        heap.offer(5);
        heap.trimToSize();
        equal(heap.size(), heap.capacity(), "nonempty trim");
        int capacity = heap.capacity();
        heap.clear();
        equal(capacity, heap.capacity(), "clear retains capacity");
        heap.offer(8);
        equal(8, heap.remove(), "reuse after clear");
    }

    private static void testOrderingAndConversions() {
        Comparator<Integer> reverse = Comparator.reverseOrder();
        BinaryHeap<Integer> heap = new BinaryHeap<>(List.of(2, 5, 1, 3, 5), reverse);
        equal(reverse, heap.comparator(), "comparator retained");
        equalArray(new Object[] {5, 5, 3, 2, 1}, heap.sorted().toArray());
        equal(5, heap.size(), "sorted preserves size");
        BinaryHeap<Integer> copy = heap.copy();
        equal(reverse, copy.comparator(), "copy retains comparator");
        equalArray(heap.toArray(), copy.toArray());
        check(!heap.equals(copy), "heap equality is identity");
        copy.offer(100);
        equal(5, heap.peek(), "copy is independent");
        Object[] array = heap.toArray();
        array[0] = 999;
        equal(5, heap.peek(), "array is independent");
        List<Integer> dataList = heap.toDataList();
        equalArray(heap.toArray(), dataList.toArray());
        dataList.clear();
        equal(5, heap.size(), "data list is independent");
        java.util.List<Integer> snapshot = heap.toJavaList();
        expect(UnsupportedOperationException.class, () -> snapshot.add(12));
        heap.clear();
        equal(5, snapshot.size(), "Java list is a snapshot");
        equalArray(new Object[] {1, 2, 3}, BinaryHeap.of(3, 1, 2).sorted().toArray());
        equalArray(new Object[0], BinaryHeap.of().toArray());
        BinaryHeap<Integer> explicitNatural = new BinaryHeap<>(List.of(3, 1, 2), null);
        equal(1, explicitNatural.peek(), "null comparator selects natural order");
        BinaryHeap<String> byLength = new BinaryHeap<>(Comparator.comparingInt(String::length));
        byLength.add("lengthy");
        byLength.add("x");
        byLength.add("abc");
        equal("x", byLength.remove(), "custom comparator");
        equal("BinaryHeap(['a', 'b'])", BinaryHeap.of("b", "a").toString(), "representation");
        BinaryHeap<Number> broadComparator = new BinaryHeap<>(
            Comparator.comparingDouble(Number::doubleValue));
        broadComparator.add(2.5);
        broadComparator.add(1);
        equal(1, broadComparator.remove(), "comparator supports non-Comparable values");
        Iterator<Integer> onePass = java.util.List.of(9, 2, 7, 1).iterator();
        BinaryHeap<Integer> fromIterable = new BinaryHeap<>(() -> onePass);
        equalArray(new Object[] {1, 2, 7, 9}, fromIterable.sorted().toArray());
    }

    private static void testRemovalDirections() {
        // Last value 3 replaces 11 below parent 10 and must sift upward.
        BinaryHeap<Integer> upward = new BinaryHeap<>(List.of(1, 10, 2, 11, 12, 3));
        check(upward.remove(Integer.valueOf(11)), "upward removal");
        equalArray(new Object[] {1, 2, 3, 10, 12}, drain(upward));
        // Last value 15 replaces 2 and must sift downward through its children.
        BinaryHeap<Integer> downward = new BinaryHeap<>(
            List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
        check(downward.remove(Integer.valueOf(2)), "downward removal");
        equalArray(new Object[] {1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            drain(downward));
        BinaryHeap<Integer> last = BinaryHeap.of(1, 2, 3);
        check(last.remove(Integer.valueOf(3)), "last slot removal");
        equalArray(new Object[] {1, 2}, drain(last));
    }

    private record Job(int priority, int id) { }

    private static void testDuplicatePriorities() {
        Comparator<Job> ordering = Comparator.comparingInt(Job::priority);
        BinaryHeap<Job> heap = new BinaryHeap<>(ordering);
        java.util.Set<Job> expected = new java.util.HashSet<>();
        for (int index = 0; index < 100; index++) {
            Job job = new Job(index % 3, index);
            heap.add(job);
            expected.add(job);
        }
        check(!heap.contains(new Job(0, 1000)), "contains uses equality, not priority");
        check(!heap.remove(new Job(0, 1000)), "remove uses equality, not priority");
        Job target = new Job(1, 40);
        check(heap.remove(target), "remove equal record");
        expected.remove(target);
        int priority = -1;
        while (!heap.isEmpty()) {
            Job job = heap.remove();
            check(job.priority() >= priority, "ties preserve priority ordering");
            priority = job.priority();
            check(expected.remove(job), "each distinct job appears exactly once");
        }
        check(expected.isEmpty(), "all equal-priority jobs retained");
    }

    private static void testIteratorsAndStreams() {
        BinaryHeap<Integer> heap = BinaryHeap.of(5, 2, 1, 4, 3);
        Iterator<Integer> iterator = heap.iterator();
        expect(UnsupportedOperationException.class, iterator::remove);
        int visited = 0;
        while (iterator.hasNext()) {
            check(heap.contains(iterator.next()), "iterator contents");
            visited++;
        }
        equal(heap.size(), visited, "iterator count");
        expect(NoSuchElementException.class, iterator::next);

        Iterator<Integer> unchanged = heap.iterator();
        heap.remove(Integer.valueOf(999));
        heap.remove(null);
        heap.trimToSize();
        heap.peek();
        heap.sorted();
        check(unchanged.hasNext(), "nonstructural operations preserve iterator");
        heap.offer(0);
        expect(ConcurrentModificationException.class, unchanged::hasNext);
        expect(ConcurrentModificationException.class, unchanged::next);
        Iterator<Integer> removed = heap.iterator();
        heap.poll();
        expect(ConcurrentModificationException.class, removed::next);
        Iterator<Integer> cleared = heap.iterator();
        heap.clear();
        expect(ConcurrentModificationException.class, cleared::hasNext);
        Iterator<Integer> empty = heap.iterator();
        heap.clear();
        heap.poll();
        check(!empty.hasNext(), "empty no-op mutations preserve iterator");

        heap.add(5);
        Stream<Integer> stream = heap.stream();
        heap.add(1);
        equal(6, stream.mapToInt(Integer::intValue).sum(), "stream is late-binding");
        heap.add(3);
        equal(9, heap.parallelStream().mapToInt(Integer::intValue).sum(), "parallel stream");
        check(heap.parallelStream().isParallel(), "parallel flag");
        equal(3L, heap.stream().count(), "stream size");
        Spliterator<Integer> spliterator = heap.spliterator();
        check(spliterator.hasCharacteristics(Spliterator.SIZED | Spliterator.SUBSIZED
            | Spliterator.NONNULL), "spliterator guarantees");
        check(!spliterator.hasCharacteristics(Spliterator.ORDERED), "no encounter order");
        check(!spliterator.hasCharacteristics(Spliterator.SORTED), "no sorted guarantee");
        equal(3L, spliterator.estimateSize(), "spliterator initial size");
        Spliterator<Integer> split = spliterator.trySplit();
        check(split != null, "spliterator splits");
        equal(3L, split.estimateSize() + spliterator.estimateSize(), "split sizes");
        java.util.List<Integer> output = new java.util.ArrayList<>();
        split.forEachRemaining(output::add);
        spliterator.forEachRemaining(output::add);
        output.sort(null);
        equal(java.util.List.of(1, 3, 5), output, "split partition");
        Spliterator<Integer> modified = heap.spliterator();
        modified.estimateSize();
        heap.add(9);
        expect(ConcurrentModificationException.class, () -> modified.tryAdvance(value -> { }));
        Spliterator<Integer> callback = heap.spliterator();
        expect(ConcurrentModificationException.class, () -> callback.tryAdvance(value -> heap.clear()));
        expect(NullPointerException.class, () -> heap.spliterator().tryAdvance(null));
        expect(NullPointerException.class, () -> heap.spliterator().forEachRemaining(null));
    }

    private static final class BombComparator implements Comparator<Integer> {
        private int remaining = -1;

        @Override
        public int compare(Integer left, Integer right) {
            if (remaining == 0) {
                throw new IllegalStateException("intentional comparison failure");
            }
            if (remaining > 0) {
                remaining--;
            }
            return Integer.compare(left, right);
        }
    }

    private static void testComparisonFailures() {
        checkFailureAtomicity(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
            heap -> heap.add(0));
        checkFailureAtomicity(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
            BinaryHeap::poll);
        checkFailureAtomicity(List.of(1, 10, 2, 11, 12, 3),
            heap -> heap.remove(Integer.valueOf(11)));
        checkFailureAtomicity(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
            heap -> heap.remove(Integer.valueOf(2)));
        BombComparator comparator = new BombComparator();
        comparator.remaining = 0;
        expect(IllegalStateException.class,
            () -> new BinaryHeap<>(List.of(3, 2, 1), comparator));
    }

    private static void checkFailureAtomicity(
        List<Integer> input, Consumer<BinaryHeap<Integer>> operation
    ) {
        int failures = 0;
        for (int allowedComparisons = 0; allowedComparisons < 32; allowedComparisons++) {
            BombComparator comparator = new BombComparator();
            BinaryHeap<Integer> heap = new BinaryHeap<>(input, comparator);
            heap.trimToSize();
            Object[] before = heap.toArray();
            int capacity = heap.capacity();
            Iterator<Integer> iterator = heap.iterator();
            comparator.remaining = allowedComparisons;
            try {
                operation.accept(heap);
                check(failures > 0, "operation must compare");
                return;
            } catch (IllegalStateException expected) {
                comparator.remaining = -1;
                failures++;
                equalArray(before, heap.toArray());
                equal(capacity, heap.capacity(), "failure preserves capacity");
                check(iterator.hasNext(), "failure preserves iterator validity");
                assertHeap(heap, null);
            }
        }
        throw new AssertionError("operation never succeeded after comparison failures");
    }

    private static final class CallbackComparator implements Comparator<Integer> {
        private Runnable callback;

        @Override
        public int compare(Integer left, Integer right) {
            Runnable action = callback;
            callback = null;
            if (action != null) {
                action.run();
            }
            return Integer.compare(left, right);
        }
    }

    private static void testCallbackModification() {
        CallbackComparator comparator = new CallbackComparator();
        BinaryHeap<Integer> heap = new BinaryHeap<>(List.of(1, 2, 3, 4, 5, 6), comparator);
        comparator.callback = heap::clear;
        expect(ConcurrentModificationException.class, () -> heap.offer(0));
        check(heap.isEmpty(), "clear from comparator is retained");
        for (int value = 1; value <= 6; value++) {
            heap.add(value);
        }
        comparator.callback = () -> heap.add(7);
        expect(ConcurrentModificationException.class, heap::poll);
        equalArray(new Object[] {1, 2, 3, 4, 5, 6, 7}, heap.sorted().toArray());
        comparator.callback = () -> heap.remove(Integer.valueOf(7));
        expect(ConcurrentModificationException.class, () -> heap.remove(Integer.valueOf(2)));
        equalArray(new Object[] {1, 2, 3, 4, 5, 6}, heap.sorted().toArray());
        comparator.callback = heap::trimToSize;
        heap.offer(0);
        equal(0, heap.poll(), "capacity change callback remains safe");
        Object mutatingNeedle = new Object() {
            @Override
            public boolean equals(Object other) {
                heap.clear();
                return true;
            }

            @Override
            public int hashCode() {
                return 0;
            }
        };
        expect(ConcurrentModificationException.class, () -> heap.remove(mutatingNeedle));
        check(heap.isEmpty(), "equals callback mutation retained");
    }

    private static void testComplexity() {
        int[] comparisons = {0};
        Comparator<Integer> counting = (left, right) -> {
            comparisons[0]++;
            return Integer.compare(left, right);
        };
        int count = 16_383;
        java.util.List<Integer> input = new java.util.ArrayList<>(count);
        for (int value = count; value > 0; value--) {
            input.add(value);
        }
        BinaryHeap<Integer> heap = new BinaryHeap<>(input, counting);
        check(comparisons[0] < 3 * count, "bottom-up heapify uses linear comparisons");
        comparisons[0] = 0;
        heap.offer(0);
        check(comparisons[0] <= 15, "insertion uses logarithmic comparisons");
        comparisons[0] = 0;
        equal(0, heap.poll(), "large heap minimum");
        check(comparisons[0] <= 30, "head removal uses logarithmic comparisons");
        equal(count, heap.size(), "large heap size preserved");
    }

    private static void randomized(Comparator<Integer> comparator, long seed) {
        Random random = new Random(seed);
        BinaryHeap<Integer> actual = new BinaryHeap<>(0, comparator);
        PriorityQueue<Integer> expected = new PriorityQueue<>(11, comparator);
        for (int step = 0; step < 12_000; step++) {
            int value = random.nextInt(101) - 50;
            int operation = random.nextInt(12);
            if (actual.size() > 256) {
                operation = 4;
            }
            switch (operation) {
                case 0, 1, 2, 3 -> equal(expected.offer(value), actual.offer(value), "random offer");
                case 4, 5 -> equal(expected.poll(), actual.poll(), "random poll");
                case 6, 7 -> equal(expected.remove(value), actual.remove(value), "random value removal");
                case 8 -> equal(expected.contains(value), actual.contains(value), "random contains");
                case 9 -> actual.trimToSize();
                case 10 -> {
                    if (random.nextInt(40) == 0) {
                        actual.clear();
                        expected.clear();
                    }
                }
                case 11 -> {
                    PriorityQueue<Integer> reference = new PriorityQueue<>(expected);
                    java.util.List<Integer> ordered = new java.util.ArrayList<>();
                    while (!reference.isEmpty()) {
                        ordered.add(reference.remove());
                    }
                    equalArray(ordered.toArray(), actual.sorted().toArray());
                }
                default -> throw new AssertionError("unknown random operation");
            }
            equal(expected.size(), actual.size(), "random size");
            equal(expected.isEmpty(), actual.isEmpty(), "random empty");
            equal(expected.peek(), actual.peek(), "random head");
            assertHeap(actual, comparator);
            if (step % 97 == 0) {
                Integer[] expectedValues = expected.toArray(new Integer[0]);
                Arrays.sort(expectedValues);
                Object[] actualValues = actual.toArray();
                Arrays.sort(actualValues);
                equalArray(expectedValues, actualValues);
            }
        }
        while (!expected.isEmpty()) {
            equal(expected.remove(), actual.remove(), "final randomized drain");
        }
        check(actual.isEmpty(), "random drain exhausted");
    }

    private static void assertHeap(BinaryHeap<Integer> heap, Comparator<Integer> comparator) {
        Object[] values = heap.toArray();
        for (int index = 1; index < values.length; index++) {
            Integer parent = (Integer) values[(index - 1) >>> 1];
            Integer child = (Integer) values[index];
            int comparison = comparator == null ? parent.compareTo(child) : comparator.compare(parent, child);
            check(comparison <= 0, "heap invariant");
        }
        check(heap.capacity() >= heap.size(), "capacity accommodates elements");
    }

    private static Object[] drain(BinaryHeap<Integer> heap) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        while (!heap.isEmpty()) {
            result.add(heap.remove());
        }
        return result.toArray();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void equalArray(Object[] expected, Object[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError("Expected " + Arrays.toString(expected)
                + ", got " + Arrays.toString(actual));
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                return;
            }
            throw new AssertionError("Expected " + type.getSimpleName() + ", got " + error, error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
