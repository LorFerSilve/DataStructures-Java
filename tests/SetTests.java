package DataStructures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.stream.Stream;

/** Dependency-free regression and differential tests for the custom hash set. */
public final class SetTests {
    private SetTests() { }

    public static void run() {
        basicContracts();
        aliasesAndIterators();
        hashFailureDoesNotLoseEntries();
        partialBulkRemovalInvalidatesIterators(false);
        partialBulkRemovalInvalidatesIterators(true);
        randomizedOperations();
    }

    private static void basicContracts() {
        Set<Integer> values = Set.of(null, 1, 1, 2, null);
        assertContents(values, new HashSet<>(Arrays.asList(null, 1, 2)));
        check(!values.add(null), "duplicate null");
        check(values.discard(null), "discard null");
        check(!values.discard(null), "discard absent null");
        expect(NoSuchElementException.class, () -> values.remove(null));
        expect(IllegalArgumentException.class, () -> new Set<>(-1));
        expect(IllegalArgumentException.class, () -> Set.of(new Set<>()));

        java.util.Set<Integer> snapshot = values.toJavaSet();
        Set<Integer> copy = values.copy();
        values.add(3);
        assertContents(copy, new HashSet<>(Arrays.asList(1, 2)));
        check(!snapshot.contains(3), "Java conversion is a snapshot");
        expect(UnsupportedOperationException.class, () -> snapshot.add(4));
        check(values.toDataList().size() == values.size(), "list conversion size");
        check(values.toArray().length == values.size(), "array conversion size");

        Set<Integer> reordered = Set.of(3, 2, 1);
        check(values.equals(reordered) && reordered.equals(values), "order-independent equality");
        check(values.hashCode() == reordered.hashCode(), "equal set hashes");
        check(!values.equals(snapshot), "custom equality excludes standard sets");
        while (!values.isEmpty()) {
            int previousSize = values.size();
            Integer removed = values.pop();
            check(!values.contains(removed) && values.size() == previousSize - 1, "pop");
        }
        expect(NoSuchElementException.class, values::pop);
    }

    private static void aliasesAndIterators() {
        Set<Integer> values = new Set<>();
        for (int i = 0; i < 10; i++) {
            values.add(i);
        }
        Iterator<Integer> iterator = values.iterator();
        int capacity = values.capacity();
        values.add(9); // At the load threshold: must not resize a duplicate.
        values.discard(-1);
        values.update(values);
        values.intersectionUpdate(values);
        values.update(() -> values.iterator());
        check(values.capacity() == capacity, "no-op mutations preserve capacity");
        check(collect(iterator).equals(values.toJavaSet()), "no-op mutations preserve iterator");

        iterator = values.iterator();
        values.add(10);
        Iterator<Integer> invalidated = iterator;
        expect(ConcurrentModificationException.class, invalidated::hasNext);
        expect(ConcurrentModificationException.class, invalidated::next);

        Stream<Integer> stream = values.stream();
        values.add(11);
        check(stream.count() == values.size(), "stream binds at terminal operation");
        check(values.parallelStream().mapToInt(Integer::intValue).sum() == 66,
            "parallel stream preserves elements");

        Set<Integer> alias = Set.of(1, 2, 3);
        Iterable<Integer> aliasView = () -> alias.iterator();
        alias.intersectionUpdate(aliasView);
        check(alias.size() == 3, "intersection with iterable alias");
        alias.differenceUpdate(aliasView);
        check(alias.isEmpty(), "difference with iterable alias");
        alias.update(Arrays.asList(1, 2, 3));
        alias.symmetricDifferenceUpdate(aliasView);
        check(alias.isEmpty(), "symmetric difference with iterable alias");
        alias.update(Arrays.asList(1, 2, 3));
        alias.differenceUpdate(alias);
        check(alias.isEmpty(), "difference with self");
        alias.update(Arrays.asList(1, 2, 3));
        alias.symmetricDifferenceUpdate(alias);
        check(alias.isEmpty(), "symmetric difference with self");

        Set<Integer> multiple = Set.of(1, 2, 3, 4);
        multiple.intersectionUpdate(Arrays.asList(2, 3, 4), Arrays.asList(3, 4, 5));
        assertContents(multiple, new HashSet<>(Arrays.asList(3, 4)));
        multiple.differenceUpdate(Arrays.asList(3), multiple);
        check(multiple.isEmpty(), "varargs difference with self");
        multiple.update(Arrays.asList(1, 2), Arrays.asList(2, 3));
        assertContents(multiple, new HashSet<>(Arrays.asList(1, 2, 3)));
        check(multiple.union().equals(multiple), "zero-argument union");
        check(multiple.intersection().equals(multiple), "zero-argument intersection");
        check(multiple.difference().equals(multiple), "zero-argument difference");
    }

    private static void hashFailureDoesNotLoseEntries() {
        Set<ThrowingKey> values = new Set<>();
        ArrayList<ThrowingKey> originals = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ThrowingKey value = new ThrowingKey(i);
            originals.add(value);
            values.add(value);
        }
        Iterator<ThrowingKey> iterator = values.iterator();
        int originalCapacity = values.capacity();
        originals.get(1).fail = true;
        try {
            expect(IllegalStateException.class, () -> values.add(new ThrowingKey(100)));
        } finally {
            originals.get(1).fail = false;
        }
        assertContents(values, new HashSet<>(originals));
        check(values.capacity() == originalCapacity, "failed resize keeps the original table");
        check(collect(iterator).equals(new HashSet<>(originals)), "failed resize preserves iterator");
        values.add(new ThrowingKey(100));
        check(values.size() == 11 && values.contains(new ThrowingKey(100)), "retry after failed resize");
    }

    private static void partialBulkRemovalInvalidatesIterators(boolean intersection) {
        ThrowingKey first = new ThrowingKey(0);
        ThrowingKey second = new ThrowingKey(1);
        Set<ThrowingKey> values = Set.of(first, second);
        Set<ThrowingKey> membership = intersection ? Set.of(second) : Set.of(first, second);
        Iterator<ThrowingKey> iterator = values.iterator();
        second.fail = true;
        try {
            expect(IllegalStateException.class, () -> {
                if (intersection) {
                    values.intersectionUpdate(membership);
                } else {
                    values.differenceUpdate(membership);
                }
            });
        } finally {
            second.fail = false;
        }
        check(values.size() == 1 && !values.contains(first) && values.contains(second),
            "completed bulk removals are retained after hash failure");
        expect(ConcurrentModificationException.class, iterator::hasNext);
        expect(ConcurrentModificationException.class, iterator::next);
        values.add(first);
        check(values.size() == 2, "set remains usable after partial bulk removal");
    }

    private static void randomizedOperations() {
        for (int seed = 0; seed < 16; seed++) {
            Random random = new Random(seed);
            Set<CollisionKey> actual = new Set<>();
            HashSet<CollisionKey> expected = new HashSet<>();
            for (int step = 0; step < 1200; step++) {
                CollisionKey value = randomKey(random);
                switch (random.nextInt(12)) {
                    case 0, 1, 2 -> check(actual.add(value) == expected.add(value), "random add");
                    case 3, 4 -> check(actual.discard(value) == expected.remove(value), "random discard");
                    case 5 -> {
                        if (expected.remove(value)) {
                            actual.remove(value);
                        } else {
                            expect(NoSuchElementException.class, () -> actual.remove(value));
                        }
                    }
                    case 6 -> {
                        if (!expected.isEmpty()) {
                            check(expected.remove(actual.pop()), "random pop returns an existing element");
                        } else {
                            expect(NoSuchElementException.class, actual::pop);
                        }
                    }
                    case 7 -> {
                        ArrayList<CollisionKey> other = randomValues(random);
                        actual.update(other);
                        expected.addAll(other);
                    }
                    case 8 -> {
                        ArrayList<CollisionKey> other = randomValues(random);
                        actual.differenceUpdate(other);
                        expected.removeAll(other);
                    }
                    case 9 -> {
                        ArrayList<CollisionKey> other = randomValues(random);
                        actual.intersectionUpdate(other);
                        expected.retainAll(other);
                    }
                    case 10 -> {
                        ArrayList<CollisionKey> other = randomValues(random);
                        actual.symmetricDifferenceUpdate(other);
                        for (CollisionKey element : new HashSet<>(other)) {
                            if (!expected.remove(element)) {
                                expected.add(element);
                            }
                        }
                    }
                    case 11 -> {
                        actual.clear();
                        expected.clear();
                    }
                    default -> throw new AssertionError("unreachable operation");
                }
                assertContents(actual, expected);
                check(actual.contains(value) == expected.contains(value), "random membership");
                if (step % 25 == 0) {
                    verifyAlgebra(actual, expected, randomValues(random));
                }
            }
        }

        // Sustained growth followed by deletion exercises shrinking and tombstones.
        Set<CollisionKey> actual = new Set<>();
        HashSet<CollisionKey> expected = new HashSet<>();
        for (int i = 0; i < 600; i++) {
            CollisionKey value = new CollisionKey(i);
            actual.add(value);
            expected.add(value);
        }
        int grownCapacity = actual.capacity();
        for (int i = 0; i < 580; i++) {
            CollisionKey value = new CollisionKey(i);
            actual.remove(value);
            expected.remove(value);
        }
        assertContents(actual, expected);
        check(actual.capacity() < grownCapacity, "sparse tables shrink");
    }

    private static void verifyAlgebra(Set<CollisionKey> actual, HashSet<CollisionKey> expected,
                                      ArrayList<CollisionKey> otherValues) {
        HashSet<CollisionKey> other = new HashSet<>(otherValues);
        HashSet<CollisionKey> union = new HashSet<>(expected);
        union.addAll(other);
        assertContents(actual.union(otherValues), union);
        HashSet<CollisionKey> intersection = new HashSet<>(expected);
        intersection.retainAll(other);
        assertContents(actual.intersection(otherValues), intersection);
        HashSet<CollisionKey> difference = new HashSet<>(expected);
        difference.removeAll(other);
        assertContents(actual.difference(otherValues), difference);
        HashSet<CollisionKey> symmetric = new HashSet<>(union);
        symmetric.removeAll(intersection);
        assertContents(actual.symmetricDifference(otherValues), symmetric);
        check(actual.isSubsetOf(otherValues) == other.containsAll(expected), "subset relation");
        check(actual.isSupersetOf(otherValues) == expected.containsAll(other), "superset relation");
        check(actual.isProperSubsetOf(otherValues)
            == (other.size() > expected.size() && other.containsAll(expected)), "proper subset relation");
        check(actual.isProperSupersetOf(otherValues)
            == (other.size() < expected.size() && expected.containsAll(other)), "proper superset relation");
        check(actual.isDisjoint(otherValues) == intersection.isEmpty(), "disjoint relation");
        check(actual.isDisjoint(new Set<>(otherValues)) == intersection.isEmpty(), "custom disjoint relation");
        assertContents(actual, expected);
    }

    private static CollisionKey randomKey(Random random) {
        return random.nextInt(16) == 0 ? null : new CollisionKey(random.nextInt(100));
    }

    private static ArrayList<CollisionKey> randomValues(Random random) {
        ArrayList<CollisionKey> result = new ArrayList<>();
        int count = random.nextInt(45);
        for (int i = 0; i < count; i++) {
            result.add(randomKey(random));
        }
        return result;
    }

    private static <T> void assertContents(Set<T> actual, java.util.Set<T> expected) {
        check(actual.size() == expected.size(), "set sizes differ");
        check(actual.toJavaSet().equals(expected), "set contents differ");
        check(collect(actual.iterator()).equals(expected), "iterator contents differ");
        for (T value : expected) {
            check(actual.contains(value), "stored element cannot be found");
        }
    }

    private static <T> HashSet<T> collect(Iterator<T> iterator) {
        HashSet<T> result = new HashSet<>();
        while (iterator.hasNext()) {
            check(result.add(iterator.next()), "iterator returned a duplicate");
        }
        expect(NoSuchElementException.class, iterator::next);
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return;
            }
            throw new AssertionError("Expected " + type.getSimpleName() + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private record CollisionKey(int value) {
        @Override
        public int hashCode() {
            return switch (value & 3) {
                case 0 -> 0;
                case 1 -> -1;
                case 2 -> Integer.MIN_VALUE;
                default -> Integer.MAX_VALUE;
            };
        }
    }

    private static final class ThrowingKey {
        private final int value;
        private boolean fail;

        private ThrowingKey(int value) {
            this.value = value;
        }

        @Override
        public int hashCode() {
            if (fail) {
                throw new IllegalStateException("test hash failure");
            }
            return value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ThrowingKey key && value == key.value;
        }
    }
}
