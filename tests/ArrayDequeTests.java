package DataStructures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Spliterator;
import java.util.stream.Stream;

import static DataStructures.TestSupport.check;
import static DataStructures.TestSupport.equal;
import static DataStructures.TestSupport.throwsType;

/** Dependency-free contract and differential tests for the circular deque. */
public final class ArrayDequeTests {
    private ArrayDequeTests() { }

    public static void run() {
        constructionAndEmptyContracts();
        aliasesAndNulls();
        wraparoundGrowthAndRemoval();
        copiesAndConversions();
        iteratorsAndStreams();
        equalityFailuresAndReentrantMutation();
        randomizedOperations();
    }

    private static void constructionAndEmptyContracts() {
        ArrayDeque<Integer> values = new ArrayDeque<>(0);
        equal(0, values.capacity(), "zero initial capacity");
        equal(0, values.size(), "empty size");
        check(values.isEmpty(), "empty predicate");
        equal(null, values.peekFirst(), "empty peekFirst");
        equal(null, values.peekLast(), "empty peekLast");
        equal(null, values.peek(), "empty peek");
        equal(null, values.pollFirst(), "empty pollFirst");
        equal(null, values.pollLast(), "empty pollLast");
        equal(null, values.poll(), "empty poll");
        throwsType(NoSuchElementException.class, values::removeFirst);
        throwsType(NoSuchElementException.class, values::removeLast);
        throwsType(NoSuchElementException.class, values::remove);
        throwsType(NoSuchElementException.class, values::pop);
        throwsType(NoSuchElementException.class, values::getFirst);
        throwsType(NoSuchElementException.class, values::getLast);
        throwsType(NoSuchElementException.class, values::element);
        throwsType(NoSuchElementException.class, values.iterator()::next);
        throwsType(NoSuchElementException.class, values.descendingIterator()::next);
        throwsType(IllegalArgumentException.class, () -> new ArrayDeque<>(-1));
        throwsType(NullPointerException.class,
            () -> new ArrayDeque<Integer>((Iterable<Integer>) null));
        throwsType(NullPointerException.class,
            () -> new ArrayDeque<>(Arrays.asList(1, null, 2)));
        throwsType(NullPointerException.class, () -> ArrayDeque.<Integer>of((Integer[]) null));
        throwsType(NullPointerException.class, () -> ArrayDeque.of(1, null, 2));

        Iterator<Integer> iterator = values.iterator();
        values.clear();
        values.trimToSize();
        values.pollFirst();
        check(!values.remove(null) && !values.remove(8), "empty occurrence removal");
        check(!iterator.hasNext(), "empty no-ops preserve iterator");
        values.addFirst(1);
        equal(1, values.removeLast(), "zero capacity grows at first end");
        values.trimToSize();
        values.addLast(2);
        equal(2, values.removeFirst(), "zero capacity grows at last end");
        equal(java.util.List.of(3, 4), new ArrayDeque<>(java.util.List.of(3, 4)).toJavaList(),
            "iterable construction order");
        equal("ArrayDeque([])", ArrayDeque.of().toString(), "empty representation");
    }

    private static void aliasesAndNulls() {
        ArrayDeque<Integer> values = new ArrayDeque<>(1);
        check(values.offerFirst(2), "offerFirst returns true");
        check(values.offerLast(3), "offerLast returns true");
        check(values.offer(4), "queue offer returns true");
        check(values.add(5), "queue add returns true");
        values.push(1);
        equal(java.util.List.of(1, 2, 3, 4, 5), values.toJavaList(), "queue and stack insertions");
        equal(1, values.element(), "queue element");
        equal(1, values.peek(), "queue peek");
        equal(1, values.pop(), "stack pop");
        equal(2, values.remove(), "queue remove");
        equal(3, values.poll(), "queue poll");
        equal(5, values.removeLast(), "last removal");
        equal(4, values.getLast(), "remaining tail");
        equal(4, values.getFirst(), "remaining head");

        Iterator<Integer> iterator = values.iterator();
        int capacity = values.capacity();
        throwsType(NullPointerException.class, () -> values.addFirst(null));
        throwsType(NullPointerException.class, () -> values.addLast(null));
        throwsType(NullPointerException.class, () -> values.offerFirst(null));
        throwsType(NullPointerException.class, () -> values.offerLast(null));
        throwsType(NullPointerException.class, () -> values.offer(null));
        throwsType(NullPointerException.class, () -> values.add(null));
        throwsType(NullPointerException.class, () -> values.push(null));
        check(!values.contains(null), "null is absent");
        check(!values.removeFirstOccurrence(null), "removeFirstOccurrence null");
        check(!values.removeLastOccurrence(null), "removeLastOccurrence null");
        check(!values.remove(null), "remove null");
        equal(capacity, values.capacity(), "rejected values preserve capacity");
        equal(4, iterator.next(), "rejected values preserve iterator");
        check(!iterator.hasNext(), "rejected values preserve contents");
        values.clear();
        equal(capacity, values.capacity(), "clear retains capacity");
    }

    private static void wraparoundGrowthAndRemoval() {
        ArrayDeque<Integer> values = new ArrayDeque<>(7);
        java.util.ArrayDeque<Integer> reference = new java.util.ArrayDeque<>();
        for (int round = 0; round < 50; round++) {
            for (int i = 0; i < 7; i++) {
                values.addLast(i);
                reference.addLast(i);
            }
            for (int i = 0; i < 5; i++) {
                equal(reference.pollFirst(), values.pollFirst(), "wrap head advancement");
            }
            for (int i = 7; i < 12; i++) {
                values.addLast(i);
                reference.addLast(i);
            }
            assertContents(values, reference);
            // Grow a completely full, wrapped buffer from either end.
            if (round % 2 == 0) {
                values.addFirst(100);
                reference.addFirst(100);
            } else {
                values.addLast(100);
                reference.addLast(100);
            }
            assertContents(values, reference);
            equal(reference.removeFirstOccurrence(6), values.removeFirstOccurrence(6),
                "remove near front of wrapped buffer");
            equal(reference.removeLastOccurrence(10), values.removeLastOccurrence(10),
                "remove near back of wrapped buffer");
            assertContents(values, reference);
            values.trimToSize();
            equal(values.size(), values.capacity(), "trim wrapped buffer");
            assertContents(values, reference);
            values.clear();
            reference.clear();
            values.trimToSize();
        }

        // Every position, including the shortest-side shifting boundary.
        for (int length = 1; length <= 18; length++) {
            for (int rotation = 0; rotation < length; rotation++) {
                for (int removed = 0; removed < length; removed++) {
                    ArrayDeque<Integer> ring = new ArrayDeque<>(length);
                    java.util.ArrayDeque<Integer> expected = new java.util.ArrayDeque<>();
                    for (int i = 0; i < length; i++) {
                        ring.addLast(i);
                        expected.addLast(i);
                    }
                    for (int i = 0; i < rotation; i++) {
                        ring.addLast(ring.removeFirst());
                        expected.addLast(expected.removeFirst());
                    }
                    equal(expected.remove(removed), ring.remove(removed), "remove rotated item");
                    assertContents(ring, expected);
                }
            }
        }

        values = ArrayDeque.of(1, 2, 1, 3, 1);
        check(values.removeFirstOccurrence(1), "remove first duplicate");
        equal(java.util.List.of(2, 1, 3, 1), values.toJavaList(), "first occurrence order");
        check(values.removeLastOccurrence(1), "remove last duplicate");
        equal(java.util.List.of(2, 1, 3), values.toJavaList(), "last occurrence order");
    }

    private static void copiesAndConversions() {
        ArrayDeque<Object> values = new ArrayDeque<>(4);
        Object shared = new Object();
        values.addLast("discard");
        values.addLast(shared);
        values.addLast("tail");
        values.removeFirst();
        values.addLast("last");
        ArrayDeque<Object> copy = values.copy();
        java.util.List<Object> snapshot = values.toJavaList();
        List<Object> dataList = values.toDataList();
        Object[] array = values.toArray();
        check(copy.getFirst() == shared && snapshot.get(0) == shared, "copies are shallow");
        equal(values.size(), copy.capacity(), "copy capacity equals size");
        check(!copy.equals(values) && values.equals(values), "identity equality");
        int originalHash = values.hashCode();
        values.clear();
        equal(originalHash, values.hashCode(), "identity hash survives mutation");
        equal(3, copy.size(), "copy independent from source");
        equal(3, snapshot.size(), "Java snapshot independent from source");
        equal(3, dataList.size(), "custom list independent from source");
        throwsType(UnsupportedOperationException.class, () -> snapshot.add("x"));
        throwsType(UnsupportedOperationException.class, () -> snapshot.set(0, "x"));
        array[0] = "changed";
        check(copy.getFirst() == shared, "array mutation is isolated");
        copy.addFirst("copy");
        check(values.isEmpty(), "copy mutation is isolated");
        equal(0, values.copy().capacity(), "empty copy compact capacity");
    }

    private static void iteratorsAndStreams() {
        ArrayDeque<Integer> values = new ArrayDeque<>(12);
        for (int i = 0; i < 10; i++) {
            values.addLast(i);
        }
        for (int i = 0; i < 4; i++) {
            values.removeFirst();
        }
        for (int i = 10; i < 14; i++) {
            values.addLast(i);
        }
        Iterator<Integer> iterator = values.iterator();
        Iterator<Integer> descending = values.descendingIterator();
        equal(4, iterator.next(), "first iterator item");
        equal(13, descending.next(), "first descending item");
        values.trimToSize();
        check(!values.remove(-1), "absent removal is no-op");
        equal(java.util.List.of(5, 6, 7, 8, 9, 10, 11, 12, 13), collect(iterator),
            "forward iterator survives trim");
        equal(java.util.List.of(12, 11, 10, 9, 8, 7, 6, 5, 4), collect(descending),
            "descending iterator survives trim");
        throwsType(NoSuchElementException.class, iterator::next);
        Iterator<Integer> cannotRemove = values.iterator();
        cannotRemove.next();
        throwsType(UnsupportedOperationException.class, cannotRemove::remove);
        Iterator<Integer> cannotRemoveDescending = values.descendingIterator();
        cannotRemoveDescending.next();
        throwsType(UnsupportedOperationException.class, cannotRemoveDescending::remove);

        Iterator<Integer> invalidated = values.iterator();
        Iterator<Integer> invalidatedDescending = values.descendingIterator();
        values.removeLast();
        throwsType(ConcurrentModificationException.class, invalidated::hasNext);
        throwsType(ConcurrentModificationException.class, invalidated::next);
        throwsType(ConcurrentModificationException.class, invalidatedDescending::hasNext);
        throwsType(ConcurrentModificationException.class, invalidatedDescending::next);

        Stream<Integer> stream = values.stream();
        values.addFirst(3);
        equal(values.toJavaList(), stream.toList(), "stream binds at terminal operation");
        equal(values.toJavaList(), values.parallelStream().map(value -> value).toList(),
            "parallel stream preserves order");
        equal((long) values.size(), values.stream().count(), "stream exact size");

        Spliterator<Integer> split = values.spliterator();
        int characteristics = Spliterator.ORDERED | Spliterator.SIZED
            | Spliterator.SUBSIZED | Spliterator.NONNULL;
        check(split.hasCharacteristics(characteristics), "spliterator characteristics");
        equal((long) values.size(), split.getExactSizeIfKnown(), "spliterator exact size");
        Spliterator<Integer> prefix = split.trySplit();
        check(prefix != null, "nontrivial spliterator splits");
        ArrayList<Integer> combined = new ArrayList<>();
        values.trimToSize();
        prefix.forEachRemaining(combined::add);
        split.forEachRemaining(combined::add);
        equal(values.toJavaList(), combined, "split traversal order across trim");
        equal(0L, split.estimateSize(), "exhausted split size");
        check(!split.tryAdvance(combined::add), "exhausted split advance");
        throwsType(NullPointerException.class, () -> split.tryAdvance(null));
        throwsType(NullPointerException.class, () -> split.forEachRemaining(null));

        Spliterator<Integer> bound = values.spliterator();
        bound.estimateSize();
        values.addLast(100);
        throwsType(ConcurrentModificationException.class, () -> bound.tryAdvance(value -> { }));
        throwsType(ConcurrentModificationException.class, bound::trySplit);
        throwsType(ConcurrentModificationException.class, bound::estimateSize);
        Spliterator<Integer> callback = values.spliterator();
        throwsType(ConcurrentModificationException.class,
            () -> callback.tryAdvance(value -> values.removeFirst()));
        Spliterator<Integer> remaining = values.spliterator();
        throwsType(ConcurrentModificationException.class,
            () -> remaining.forEachRemaining(value -> values.clear()));
    }

    private static void equalityFailuresAndReentrantMutation() {
        ArrayDeque<Integer> values = ArrayDeque.of(1, 2, 3);
        Object failing = new Object() {
            @Override
            public boolean equals(Object other) {
                throw new IllegalStateException("Deliberate equality failure");
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };
        Iterator<Integer> iterator = values.iterator();
        throwsType(IllegalStateException.class, () -> values.contains(failing));
        throwsType(IllegalStateException.class, () -> values.removeFirstOccurrence(failing));
        throwsType(IllegalStateException.class, () -> values.removeLastOccurrence(failing));
        equal(java.util.List.of(1, 2, 3), collect(iterator), "throwing equals preserves iterator");
        equal(java.util.List.of(1, 2, 3), values.toJavaList(), "throwing equals preserves contents");

        Object mutating = new Object() {
            @Override
            public boolean equals(Object other) {
                values.clear();
                values.addLast(42);
                return true;
            }

            @Override
            public int hashCode() {
                return 2;
            }
        };
        throwsType(ConcurrentModificationException.class,
            () -> values.removeFirstOccurrence(mutating));
        equal(java.util.List.of(42), values.toJavaList(), "reentrant equality cannot remove new item");
    }

    private static void randomizedOperations() {
        Random random = new Random(0xDEA0E123L);
        ArrayDeque<Integer> values = new ArrayDeque<>(0);
        java.util.ArrayDeque<Integer> reference = new java.util.ArrayDeque<>();
        for (int step = 0; step < 20_000; step++) {
            int value = random.nextInt(31) - 15;
            switch (random.nextInt(21)) {
                case 0 -> { values.addFirst(value); reference.addFirst(value); }
                case 1 -> { values.addLast(value); reference.addLast(value); }
                case 2 -> equal(reference.offerFirst(value), values.offerFirst(value), "offerFirst");
                case 3 -> equal(reference.offerLast(value), values.offerLast(value), "offerLast");
                case 4 -> equal(reference.pollFirst(), values.pollFirst(), "pollFirst");
                case 5 -> equal(reference.pollLast(), values.pollLast(), "pollLast");
                case 6 -> equal(reference.removeFirstOccurrence(value),
                    values.removeFirstOccurrence(value), "remove first occurrence");
                case 7 -> equal(reference.removeLastOccurrence(value),
                    values.removeLastOccurrence(value), "remove last occurrence");
                case 8 -> equal(reference.contains(value), values.contains(value), "contains");
                case 9 -> {
                    values.trimToSize();
                    equal(values.size(), values.capacity(), "random trim");
                }
                case 10 -> {
                    if (random.nextInt(8) == 0) {
                        values.clear();
                        reference.clear();
                    }
                }
                case 11 -> assertContents(values.copy(), reference);
                case 12 -> { values.push(value); reference.push(value); }
                case 13 -> equal(reference.offer(value), values.offer(value), "queue offer");
                case 14 -> equal(reference.poll(), values.poll(), "queue poll");
                case 15 -> equal(reference.remove(value), values.remove(value), "remove alias");
                case 16 -> equal(reference.add(value), values.add(value), "queue add");
                case 17 -> {
                    if (!reference.isEmpty()) {
                        equal(reference.pop(), values.pop(), "stack pop");
                    }
                }
                case 18 -> {
                    if (!reference.isEmpty()) {
                        equal(reference.removeLast(), values.removeLast(), "removeLast");
                    }
                }
                case 19 -> {
                    if (!reference.isEmpty()) {
                        equal(reference.remove(), values.remove(), "queue remove");
                    }
                }
                case 20 -> equal(new ArrayList<>(reference), values.stream().toList(), "random stream");
                default -> throw new AssertionError("Unknown operation");
            }
            assertContents(values, reference);
        }
    }

    private static void assertContents(ArrayDeque<Integer> values,
                                       java.util.ArrayDeque<Integer> reference) {
        java.util.List<Integer> expected = new ArrayList<>(reference);
        equal(reference.size(), values.size(), "deque size");
        equal(reference.isEmpty(), values.isEmpty(), "deque emptiness");
        check(values.capacity() >= values.size(), "capacity covers size");
        equal(expected, values.toJavaList(), "first-to-last snapshot");
        equal(expected, collect(values.iterator()), "first-to-last iteration");
        equal(expected, Arrays.asList(values.toArray()), "array conversion order");
        ArrayList<Integer> reversed = new ArrayList<>(expected);
        Collections.reverse(reversed);
        equal(reversed, collect(values.descendingIterator()), "last-to-first iteration");
        equal(reference.peekFirst(), values.peekFirst(), "front value");
        equal(reference.peekLast(), values.peekLast(), "back value");
        if (!reference.isEmpty()) {
            equal(reference.getFirst(), values.getFirst(), "front getter");
            equal(reference.getLast(), values.getLast(), "back getter");
            equal(reference.element(), values.element(), "queue getter");
        }
    }

    private static <T> java.util.List<T> collect(Iterator<T> iterator) {
        ArrayList<T> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }
}
