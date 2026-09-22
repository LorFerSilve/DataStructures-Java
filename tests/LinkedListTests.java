package DataStructures;

import static DataStructures.TestSupport.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.Spliterator;

public final class LinkedListTests {
    private LinkedListTests() { }

    public static void run() {
        constructionAndEndOperations();
        indexingInsertionAndRemoval();
        searchReverseAndNulls();
        copiesEqualityAndConversions();
        traversalContracts();
        randomizedAgainstReference();
    }

    private static void constructionAndEndOperations() {
        LinkedList<Integer> empty = new LinkedList<>();
        equal(0, empty.size(), "new list size");
        check(empty.isEmpty(), "new list empty");
        throwsType(NoSuchElementException.class, empty::getFirst);
        throwsType(NoSuchElementException.class, empty::getLast);
        throwsType(NoSuchElementException.class, empty::removeFirst);
        throwsType(NoSuchElementException.class, empty::removeLast);
        throwsType(NoSuchElementException.class, empty::pop);
        throwsType(NoSuchElementException.class, empty.iterator()::next);
        throwsType(NoSuchElementException.class, empty.descendingIterator()::next);
        throwsType(NullPointerException.class,
            () -> new LinkedList<Integer>((Iterable<Integer>) null));
        throwsType(NullPointerException.class,
            () -> LinkedList.<Integer>of((Integer[]) null));

        LinkedList<Integer> list = new LinkedList<>(java.util.List.of(2, 3));
        list.prepend(1);
        list.append(4);
        list.addFirst(0);
        list.addLast(5);
        equal(java.util.List.of(0, 1, 2, 3, 4, 5), list.toJavaList(), "end insert order");
        equal(0, list.getFirst(), "front getter");
        equal(5, list.getLast(), "back getter");
        equal(0, list.removeFirst(), "front removal");
        equal(5, list.removeLast(), "back removal");
        equal(java.util.List.of(1, 2, 3, 4), list.toJavaList(), "end removals");

        LinkedList<Integer> self = LinkedList.of(1, 2, 3);
        self.extend(self);
        equal(java.util.List.of(1, 2, 3, 1, 2, 3), self.toJavaList(), "self extension snapshot");
    }

    private static void indexingInsertionAndRemoval() {
        LinkedList<String> list = LinkedList.of("b", "c", "d");
        list.insert(0, "a");
        list.insert(999, "f");
        list.insert(-1, "e");
        list.insert(-999, "start");
        equal(java.util.List.of("start", "a", "b", "c", "d", "e", "f"),
            list.toJavaList(), "Python-style insert normalization");

        equal("start", list.get(0), "get first index");
        equal("f", list.get(-1), "get negative index");
        equal("c", list.get(3), "get middle index");
        equal("c", list.set(3, "C"), "set returns previous");
        equal("C", list.get(3), "set replacement");
        throwsType(IndexOutOfBoundsException.class, () -> list.get(7));
        throwsType(IndexOutOfBoundsException.class, () -> list.get(-8));

        equal("start", list.removeAt(0), "remove first by index");
        equal("f", list.removeAt(-1), "remove last by negative index");
        equal("C", list.removeAt(2), "remove middle by index");
        equal(java.util.List.of("a", "b", "d", "e"), list.toJavaList(), "indexed removals");
        equal("e", list.pop(), "pop end");
        equal("b", list.pop(1), "pop indexed");
        equal(java.util.List.of("a", "d"), list.toJavaList(), "pop order");
    }

    private static void searchReverseAndNulls() {
        LinkedList<String> list = LinkedList.of(null, "x", "y", "x", null);
        check(list.contains(null), "contains null");
        check(list.contains("y"), "contains value");
        check(!list.contains("missing"), "missing value");
        equal(0, list.indexOf(null), "first null");
        equal(4, list.lastIndexOf(null), "last null");
        equal(1, list.indexOf("x"), "first duplicate");
        equal(3, list.lastIndexOf("x"), "last duplicate");
        equal(2, list.count("x"), "duplicate count");
        equal(2, list.count(null), "null count");

        list.remove("x");
        equal(Arrays.asList(null, "y", "x", null), list.toJavaList(), "remove first match");
        throwsType(NoSuchElementException.class, () -> list.remove("missing"));

        Iterator<String> stale = list.iterator();
        list.reverse();
        equal(java.util.Arrays.asList(null, "x", "y", null), list.toJavaList(), "reverse");
        throwsType(ConcurrentModificationException.class, stale::hasNext);

        LinkedList<Integer> singleton = LinkedList.of(1);
        Iterator<Integer> stable = singleton.iterator();
        singleton.reverse();
        equal(1, stable.next(), "reverse singleton is no-op");
    }

    private static void copiesEqualityAndConversions() {
        LinkedList<Object> original = new LinkedList<>();
        Object shared = new Object();
        original.append("first");
        original.append(shared);
        original.append(null);

        LinkedList<Object> copy = original.copy();
        check(copy != original, "copy independent identity");
        check(copy.equals(original) && original.equals(copy), "structural equality");
        equal(original.hashCode(), copy.hashCode(), "equal lists hash equally");
        check(!original.equals(List.of("first", shared, null)), "type-specific equality");

        copy.removeLast();
        equal(3, original.size(), "copy mutation does not affect original");
        equal(2, copy.size(), "copy mutation size");

        Object[] array = original.toArray();
        equal(3, array.length, "array length");
        check(array[1] == shared, "array shallow");
        array[0] = "changed";
        equal("first", original.getFirst(), "array independent");

        java.util.List<Object> javaList = original.toJavaList();
        equal(Arrays.asList("first", shared, null), javaList, "Java snapshot order");
        throwsType(UnsupportedOperationException.class, () -> javaList.add("x"));
        equal(List.of("first", shared, null), original.toDataList(), "custom list snapshot");

        original.clear();
        check(original.isEmpty(), "clear empties list");
        equal(3, javaList.size(), "snapshots survive clear");
        original.clear();
    }

    private static void traversalContracts() {
        LinkedList<Integer> list = LinkedList.of(1, 2, 3, 4, 5);
        equal(java.util.List.of(1, 2, 3, 4, 5), collect(list.iterator()),
            "forward iteration");
        equal(java.util.List.of(5, 4, 3, 2, 1), collect(list.descendingIterator()),
            "descending iteration");
        equal(java.util.List.of(5, 4, 3, 2, 1), collect(list.reversed().iterator()),
            "reversed iterable");

        Iterator<Integer> iterator = list.iterator();
        equal(1, iterator.next(), "iterator first");
        list.set(1, 20);
        equal(20, iterator.next(), "nonstructural set remains visible");
        list.append(6);
        throwsType(ConcurrentModificationException.class, iterator::hasNext);

        Iterator<Integer> cannotRemove = list.iterator();
        cannotRemove.next();
        throwsType(UnsupportedOperationException.class, cannotRemove::remove);

        var stream = list.stream();
        list.append(7);
        equal(list.toJavaList(), stream.toList(), "stream late binding");
        equal((long) list.size(), list.parallelStream().count(), "parallel stream count");

        Spliterator<Integer> split = list.spliterator();
        int characteristics = Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        check(split.hasCharacteristics(characteristics), "spliterator characteristics");
        equal((long) list.size(), split.getExactSizeIfKnown(), "spliterator exact size");
        Spliterator<Integer> prefix = split.trySplit();
        check(prefix != null, "spliterator splits");

        ArrayList<Integer> combined = new ArrayList<>();
        prefix.forEachRemaining(combined::add);
        split.forEachRemaining(combined::add);
        equal(list.toJavaList(), combined, "split preserves order");

        Spliterator<Integer> bound = list.spliterator();
        bound.estimateSize();
        list.removeFirst();
        throwsType(ConcurrentModificationException.class, bound::estimateSize);
        throwsType(ConcurrentModificationException.class, bound::trySplit);

        Spliterator<Integer> callback = list.spliterator();
        throwsType(ConcurrentModificationException.class,
            () -> callback.tryAdvance(value -> list.clear()));
    }

    private static void randomizedAgainstReference() {
        LinkedList<Integer> actual = new LinkedList<>();
        java.util.LinkedList<Integer> expected = new java.util.LinkedList<>();
        Random random = new Random(0x1A2B3C4DL);

        for (int step = 0; step < 20_000; step++) {
            Integer value = random.nextInt(7) == 0 ? null : random.nextInt(101) - 50;
            int operation = random.nextInt(14);

            switch (operation) {
                case 0 -> {
                    actual.addFirst(value);
                    expected.addFirst(value);
                }
                case 1, 2 -> {
                    actual.addLast(value);
                    expected.addLast(value);
                }
                case 3 -> {
                    int raw = random.nextInt(expected.size() + 11) - 5;
                    int index = normalizeInsertIndex(raw, expected.size());
                    actual.insert(raw, value);
                    expected.add(index, value);
                }
                case 4 -> {
                    if (!expected.isEmpty()) {
                        int index = random.nextInt(expected.size());
                        int customIndex = random.nextBoolean() ? index : index - expected.size();
                        equal(expected.get(index), actual.get(customIndex), "random get");
                    }
                }
                case 5 -> {
                    if (!expected.isEmpty()) {
                        int index = random.nextInt(expected.size());
                        int customIndex = random.nextBoolean() ? index : index - expected.size();
                        equal(expected.set(index, value), actual.set(customIndex, value), "random set");
                    }
                }
                case 6 -> {
                    if (!expected.isEmpty()) {
                        equal(expected.removeFirst(), actual.removeFirst(), "random removeFirst");
                    }
                }
                case 7 -> {
                    if (!expected.isEmpty()) {
                        equal(expected.removeLast(), actual.removeLast(), "random removeLast");
                    }
                }
                case 8 -> {
                    if (!expected.isEmpty()) {
                        int index = random.nextInt(expected.size());
                        int customIndex = random.nextBoolean() ? index : index - expected.size();
                        equal(expected.remove(index), actual.removeAt(customIndex), "random removeAt");
                    }
                }
                case 9 -> {
                    boolean present = expected.remove(value);
                    if (present) {
                        actual.remove(value);
                    } else {
                        throwsType(NoSuchElementException.class, () -> actual.remove(value));
                    }
                }
                case 10 -> {
                    Collections.reverse(expected);
                    actual.reverse();
                }
                case 11 -> {
                    if (random.nextInt(12) == 0) {
                        expected.clear();
                        actual.clear();
                    }
                }
                case 12 -> {
                    equal(expected.indexOf(value), actual.indexOf(value), "random indexOf");
                    equal(expected.lastIndexOf(value), actual.lastIndexOf(value), "random lastIndexOf");
                }
                case 13 -> equal(new ArrayList<>(expected), actual.stream().toList(),
                    "random stream");
                default -> throw new AssertionError("Unknown operation");
            }

            assertContents(actual, expected);
        }
    }

    private static void assertContents(LinkedList<Integer> actual,
                                       java.util.LinkedList<Integer> expected) {
        equal(expected.size(), actual.size(), "size");
        equal(expected.isEmpty(), actual.isEmpty(), "emptiness");
        equal(new ArrayList<>(expected), actual.toJavaList(), "snapshot");
        equal(new ArrayList<>(expected), collect(actual.iterator()), "forward iterator");
        ArrayList<Integer> reversed = new ArrayList<>(expected);
        Collections.reverse(reversed);
        equal(reversed, collect(actual.descendingIterator()), "reverse iterator");
        equal(Arrays.asList(expected.toArray()), Arrays.asList(actual.toArray()), "array");
        if (!expected.isEmpty()) {
            equal(expected.getFirst(), actual.getFirst(), "first");
            equal(expected.getLast(), actual.getLast(), "last");
        }
    }

    private static int normalizeInsertIndex(int index, int size) {
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

    private static <T> java.util.List<T> collect(Iterator<T> iterator) {
        ArrayList<T> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }
}
