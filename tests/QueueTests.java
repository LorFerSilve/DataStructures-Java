package DataStructures;

import static DataStructures.TestSupport.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Spliterator;

public final class QueueTests {
    private QueueTests() { }

    public static void run() {
        constructionAndEmptyContracts();
        fifoOperationsAndNulls();
        wraparoundGrowthAndRemoval();
        copiesAndConversions();
        traversalContracts();
        equalityFailuresAndReentrantMutation();
        randomizedAgainstReference();
    }

    private static void constructionAndEmptyContracts() {
        Queue<Integer> queue = new Queue<>(0);
        equal(0, queue.capacity(), "zero initial capacity");
        equal(0, queue.size(), "empty size");
        check(queue.isEmpty(), "empty predicate");
        equal(null, queue.peek(), "empty peek");
        equal(null, queue.poll(), "empty poll");
        throwsType(NoSuchElementException.class, queue::element);
        throwsType(NoSuchElementException.class, queue::remove);
        throwsType(NoSuchElementException.class, queue.iterator()::next);
        throwsType(IllegalArgumentException.class, () -> new Queue<>(-1));
        throwsType(NullPointerException.class,
            () -> new Queue<Integer>((Iterable<Integer>) null));
        throwsType(NullPointerException.class,
            () -> new Queue<>(Arrays.asList(1, null, 2)));
        throwsType(NullPointerException.class, () -> Queue.<Integer>of((Integer[]) null));
        throwsType(NullPointerException.class, () -> Queue.of(1, null, 2));

        Iterator<Integer> iterator = queue.iterator();
        queue.clear();
        queue.trimToSize();
        equal(null, queue.poll(), "empty poll no-op");
        check(!queue.remove(null) && !queue.remove(8), "empty removals");
        check(!iterator.hasNext(), "empty no-ops preserve iterator");

        queue.add(1);
        equal(1, queue.remove(), "zero-capacity queue grows");
        queue.trimToSize();
        queue.offer(2);
        equal(2, queue.poll(), "trimmed-empty queue grows again");

        Queue<Integer> fromIterable = new Queue<>(java.util.List.of(3, 4, 5));
        equal(java.util.List.of(3, 4, 5), fromIterable.toJavaList(),
            "iterable constructor order");
        equal(3, fromIterable.element(), "iterable constructor front");
        equal(java.util.List.of(6, 7, 8), Queue.of(6, 7, 8).toJavaList(),
            "factory order");
    }

    private static void fifoOperationsAndNulls() {
        Queue<String> queue = new Queue<>(1);
        check(queue.add("a"), "add returns true");
        check(queue.offer("b"), "offer returns true");
        queue.add("c");
        equal("a", queue.element(), "element front");
        equal("a", queue.peek(), "peek front");
        equal(3, queue.size(), "peek preserves size");
        equal("a", queue.remove(), "remove first");
        equal("b", queue.poll(), "poll second");
        equal("c", queue.remove(), "remove third");
        check(queue.isEmpty(), "empty after FIFO removals");
        equal(null, queue.poll(), "empty poll");
        equal(null, queue.peek(), "empty peek");
        throwsType(NoSuchElementException.class, queue::remove);
        throwsType(NoSuchElementException.class, queue::element);
        throwsType(NullPointerException.class, () -> queue.add(null));
        throwsType(NullPointerException.class, () -> queue.offer(null));
        check(!queue.contains(null), "contains null false");
        check(!queue.remove(null), "remove null false");
    }

    private static void wraparoundGrowthAndRemoval() {
        Queue<Integer> queue = new Queue<>(4);
        java.util.ArrayDeque<Integer> reference = new java.util.ArrayDeque<>();

        for (int i = 0; i < 4; i++) {
            queue.add(i);
            reference.add(i);
        }
        equal(reference.poll(), queue.poll(), "wrap remove 0");
        equal(reference.poll(), queue.poll(), "wrap remove 1");
        queue.add(4);
        reference.add(4);
        queue.add(5);
        reference.add(5);
        assertContents(queue, reference);

        queue.add(6);
        reference.add(6);
        check(queue.capacity() >= 5, "growth from wrapped full buffer");
        assertContents(queue, reference);

        check(queue.remove(4), "remove interior wrapped value");
        check(reference.remove(4), "reference interior removal");
        assertContents(queue, reference);

        queue.trimToSize();
        equal(queue.size(), queue.capacity(), "trim wrapped queue");
        assertContents(queue, reference);

        queue.clear();
        reference.clear();
        queue.trimToSize();
        equal(0, queue.capacity(), "trim empty queue");
        queue.add(42);
        reference.add(42);
        assertContents(queue, reference);

        Queue<Integer> duplicates = Queue.of(1, 2, 1, 3, 1);
        check(duplicates.remove(1), "remove first duplicate");
        equal(java.util.List.of(2, 1, 3, 1), duplicates.toJavaList(),
            "first duplicate removed");
    }

    private static void copiesAndConversions() {
        Queue<Object> queue = new Queue<>(4);
        Object shared = new Object();
        queue.add("discard");
        queue.add(shared);
        queue.add("tail");
        queue.poll();
        queue.add("last");

        Queue<Object> copy = queue.copy();
        java.util.List<Object> snapshot = queue.toJavaList();
        List<Object> dataList = queue.toDataList();
        Object[] array = queue.toArray();

        check(copy.peek() == shared && snapshot.get(0) == shared, "copies are shallow");
        equal(queue.size(), copy.capacity(), "copy capacity equals size");
        check(!copy.equals(queue) && queue.equals(queue), "identity equality");
        int originalHash = queue.hashCode();
        queue.clear();
        equal(originalHash, queue.hashCode(), "identity hash survives mutation");
        equal(3, copy.size(), "copy independent from source");
        equal(3, snapshot.size(), "Java snapshot independent from source");
        equal(3, dataList.size(), "custom list independent from source");
        throwsType(UnsupportedOperationException.class, () -> snapshot.add("x"));
        throwsType(UnsupportedOperationException.class, () -> snapshot.set(0, "x"));
        array[0] = "changed";
        check(copy.peek() == shared, "array mutation isolated");
        copy.add("copy");
        check(queue.isEmpty(), "copy mutation isolated");
        equal(0, queue.copy().capacity(), "empty copy compact capacity");
    }

    private static void traversalContracts() {
        Queue<Integer> queue = new Queue<>(8);
        for (int i = 0; i < 6; i++) {
            queue.add(i);
        }
        queue.poll();
        queue.poll();
        queue.add(6);
        queue.add(7);

        Iterator<Integer> iterator = queue.iterator();
        equal(2, iterator.next(), "iterator starts at front");
        queue.trimToSize();
        equal(java.util.List.of(3, 4, 5, 6, 7), collect(iterator),
            "iterator survives capacity-only trim");
        throwsType(NoSuchElementException.class, iterator::next);

        Iterator<Integer> cannotRemove = queue.iterator();
        cannotRemove.next();
        throwsType(UnsupportedOperationException.class, cannotRemove::remove);

        Iterator<Integer> invalidated = queue.iterator();
        queue.add(8);
        throwsType(ConcurrentModificationException.class, invalidated::hasNext);
        throwsType(ConcurrentModificationException.class, invalidated::next);

        var stream = queue.stream();
        queue.add(9);
        equal(queue.toJavaList(), stream.toList(), "stream late binding");
        equal((long) queue.size(), queue.parallelStream().count(), "parallel stream count");

        Spliterator<Integer> split = queue.spliterator();
        int characteristics = Spliterator.ORDERED | Spliterator.SIZED
            | Spliterator.SUBSIZED | Spliterator.NONNULL;
        check(split.hasCharacteristics(characteristics), "spliterator characteristics");
        equal((long) queue.size(), split.getExactSizeIfKnown(), "spliterator exact size");
        Spliterator<Integer> prefix = split.trySplit();
        check(prefix != null, "nontrivial spliterator splits");
        ArrayList<Integer> combined = new ArrayList<>();
        queue.trimToSize();
        prefix.forEachRemaining(combined::add);
        split.forEachRemaining(combined::add);
        equal(queue.toJavaList(), combined, "split traversal order");
        equal(0L, split.estimateSize(), "exhausted split size");
        check(!split.tryAdvance(combined::add), "exhausted split");
        throwsType(NullPointerException.class, () -> split.tryAdvance(null));
        throwsType(NullPointerException.class, () -> split.forEachRemaining(null));

        Spliterator<Integer> bound = queue.spliterator();
        bound.estimateSize();
        queue.poll();
        throwsType(ConcurrentModificationException.class, bound::estimateSize);
        throwsType(ConcurrentModificationException.class, bound::trySplit);

        Spliterator<Integer> callback = queue.spliterator();
        throwsType(ConcurrentModificationException.class,
            () -> callback.tryAdvance(value -> queue.clear()));
    }

    private static void equalityFailuresAndReentrantMutation() {
        Queue<Integer> queue = Queue.of(1, 2, 3);
        Object failing = new Object() {
            @Override
            public boolean equals(Object other) {
                throw new IllegalStateException("Deliberate equality failure");
            }
        };

        Iterator<Integer> iterator = queue.iterator();
        throwsType(IllegalStateException.class, () -> queue.contains(failing));
        throwsType(IllegalStateException.class, () -> queue.remove(failing));
        equal(java.util.List.of(1, 2, 3), collect(iterator),
            "throwing equals preserves iterator");
        equal(java.util.List.of(1, 2, 3), queue.toJavaList(),
            "throwing equals preserves queue");

        Object mutating = new Object() {
            @Override
            public boolean equals(Object other) {
                queue.clear();
                queue.add(42);
                return true;
            }
        };

        throwsType(ConcurrentModificationException.class, () -> queue.remove(mutating));
        equal(java.util.List.of(42), queue.toJavaList(),
            "reentrant equality cannot remove replacement item");
    }

    private static void randomizedAgainstReference() {
        Queue<Integer> queue = new Queue<>(0);
        java.util.ArrayDeque<Integer> reference = new java.util.ArrayDeque<>();
        Random random = new Random(0x0F1F0F1FL);

        for (int step = 0; step < 20_000; step++) {
            int value = random.nextInt(41) - 20;
            switch (random.nextInt(12)) {
                case 0, 1, 2 -> {
                    equal(reference.offer(value), queue.offer(value), "random offer");
                }
                case 3 -> equal(reference.poll(), queue.poll(), "random poll");
                case 4 -> equal(reference.peek(), queue.peek(), "random peek");
                case 5 -> equal(reference.contains(value), queue.contains(value), "random contains");
                case 6 -> equal(reference.remove(value), queue.remove(value), "random remove value");
                case 7 -> {
                    queue.trimToSize();
                    equal(queue.size(), queue.capacity(), "random trim");
                }
                case 8 -> {
                    if (random.nextInt(8) == 0) {
                        queue.clear();
                        reference.clear();
                    }
                }
                case 9 -> assertContents(queue.copy(), reference);
                case 10 -> equal(new ArrayList<>(reference), queue.stream().toList(),
                    "random stream");
                case 11 -> {
                    if (!reference.isEmpty()) {
                        equal(reference.remove(), queue.remove(), "random throwing remove");
                    } else {
                        throwsType(NoSuchElementException.class, queue::remove);
                    }
                }
                default -> throw new AssertionError("Unknown operation");
            }
            assertContents(queue, reference);
        }
    }

    private static void assertContents(Queue<Integer> queue,
                                       java.util.ArrayDeque<Integer> reference) {
        java.util.List<Integer> expected = new ArrayList<>(reference);
        equal(reference.size(), queue.size(), "queue size");
        equal(reference.isEmpty(), queue.isEmpty(), "queue emptiness");
        check(queue.capacity() >= queue.size(), "capacity covers size");
        equal(expected, queue.toJavaList(), "front-to-back snapshot");
        equal(expected, collect(queue.iterator()), "front-to-back iteration");
        equal(expected, Arrays.asList(queue.toArray()), "array conversion order");
        equal(reference.peek(), queue.peek(), "front value");
        if (!reference.isEmpty()) {
            equal(reference.element(), queue.element(), "throwing front getter");
        }
    }

    private static <T> java.util.List<T> collect(Iterator<T> iterator) {
        ArrayList<T> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }
}
