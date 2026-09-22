package DataStructures;

import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;

/** Dependency-free regression tests for list slicing and sorting. */
public final class ListTests {
    private ListTests() { }

    public static void run() {
        testOpenSlices();
        testSliceBoundaries();
        testSliceAssignment();
        testSorting();
        testSortingFailures();
    }

    private static void testOpenSlices() {
        List<Integer> values = List.of(0, 1, 2, 3, 4);
        equalArray(new Object[] {2, 3, 4}, values.sliceFrom(2));
        equalArray(new Object[] {3, 1}, values.sliceFrom(-2, -2));
        equalArray(new Object[] {0, 1, 2}, values.sliceTo(-2));
        equalArray(new Object[] {4, 2}, values.sliceTo(0, -2));
        equalArray(new Object[] {4, 3, 2, 1, 0}, values.sliceAll(-1));
        equalArray(new Object[] {0, 2, 4}, values.sliceAll(2));
        equalArray(new Object[] {4}, values.sliceAll(Integer.MIN_VALUE));
        equalArray(new Object[] {1}, values.slice(1, 5, Integer.MAX_VALUE));
        equalArray(new Object[0], values.slice(4, -1, -1));
        equalArray(new Object[] {4, 3, 2, 1, 0}, values.sliceFrom(4, -1));
        equalArray(new Object[0], new List<Integer>().sliceAll(-1));

        List<Integer> copy = values.sliceAll();
        copy.set(0, 99);
        equal(0, values.get(0), "sliceAll must copy the list structure");
        List<Integer> steppedCopy = values.sliceAll(1);
        steppedCopy.clear();
        equal(5, values.size(), "sliceAll(1) must copy the list structure");

        expect(IllegalArgumentException.class, () -> values.slice(0, 5, 0));
        expect(IllegalArgumentException.class, () -> values.sliceFrom(0, 0));
        expect(IllegalArgumentException.class, () -> values.sliceTo(0, 0));
        expect(IllegalArgumentException.class, () -> values.sliceAll(0));
        expect(IllegalArgumentException.class, () -> values.removeSlice(0, 5, 0));
        expect(IllegalArgumentException.class,
            () -> values.setSlice(0, 5, 0, List.of()));
    }

    private static void testSliceBoundaries() {
        int[] bounds = {Integer.MIN_VALUE, -12, -5, -1, 0, 1, 4, 9,
            Integer.MAX_VALUE};
        int[] steps = {Integer.MIN_VALUE, -10, -3, -1, 1, 2, 10,
            Integer.MAX_VALUE};

        for (int length = 0; length <= 8; length++) {
            List<Integer> original = new List<>();
            for (int i = 0; i < length; i++) {
                original.append(i);
            }
            for (int start : bounds) {
                for (int end : bounds) {
                    for (int step : steps) {
                        java.util.List<Integer> indices = selectedIndices(
                            length, start, end, step);
                        equalArray(indices.toArray(), original.slice(start, end, step));

                        List<Integer> removed = original.copy();
                        removed.removeSlice(start, end, step);
                        java.util.List<Integer> remaining = new java.util.ArrayList<>();
                        for (int i = 0; i < length; i++) {
                            if (!indices.contains(i)) {
                                remaining.add(i);
                            }
                        }
                        equalArray(remaining.toArray(), removed);

                        List<Integer> assigned = original.copy();
                        List<Integer> replacement = new List<>();
                        Object[] expected = original.toArray();
                        for (int i = 0; i < indices.size(); i++) {
                            replacement.append(100 + i);
                            expected[indices.get(i)] = 100 + i;
                        }
                        assigned.setSlice(start, end, step, replacement);
                        equalArray(expected, assigned);
                    }
                }
            }
        }
    }

    // Reference selection examines every valid index independently, rather
    // than duplicating the implementation's stepped traversal/count formula.
    private static java.util.List<Integer> selectedIndices(
        int length, int start, int end, int step
    ) {
        long from = start < 0 ? (long) length + start : start;
        long to = end < 0 ? (long) length + end : end;
        long lower = step > 0 ? 0 : -1;
        long upper = step > 0 ? length : length - 1L;
        from = Math.max(lower, Math.min(from, upper));
        to = Math.max(lower, Math.min(to, upper));
        java.util.List<Integer> indices = new java.util.ArrayList<>();
        for (int i = 0; i < length; i++) {
            if (step > 0 ? i >= from && i < to && (i - from) % step == 0
                : i <= from && i > to && (from - i) % -(long) step == 0) {
                indices.add(i);
            }
        }
        if (step < 0) {
            java.util.Collections.reverse(indices);
        }
        return indices;
    }

    private static void testSliceAssignment() {
        List<Integer> values = List.of(0, 1, 2, 3, 4);
        values.setSlice(4, 0, -2, List.of(40, 20));
        equalArray(new Object[] {0, 1, 20, 3, 40}, values);
        Object[] before = values.toArray();
        expect(IllegalArgumentException.class,
            () -> values.setSlice(0, 5, 2, List.of(8)));
        equalArray(before, values);
        expect(IllegalArgumentException.class,
            () -> values.setSlice(4, 0, 2, List.of(8)));
        equalArray(before, values);

        List<Integer> self = List.of(1, 2, 3);
        self.setSlice(2, -4, -1, self);
        equalArray(new Object[] {3, 2, 1}, self);
        self.setSlice(1, 2, self);
        equalArray(new Object[] {3, 3, 2, 1, 1}, self);
        self.setSlice(4, 1, List.of(7, 8));
        equalArray(new Object[] {3, 3, 2, 1, 7, 8, 1}, self);
        self.setSlice(1, 6, List.of(9));
        equalArray(new Object[] {3, 9, 1}, self);

        Iterable<Integer> failingValues = () -> new Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() { return true; }

            @Override
            public Integer next() {
                if (next++ == 0) {
                    return 99;
                }
                throw new IllegalStateException("Source failed");
            }
        };
        expect(IllegalStateException.class,
            () -> values.setSlice(0, 5, 2, failingValues));
        equalArray(before, values);
        expect(IllegalStateException.class,
            () -> values.setSlice(0, 5, failingValues));
        equalArray(before, values);

        Iterator<Integer> iterator = values.iterator();
        values.removeSlice(4, 1, 2);
        equal(0, iterator.next(), "Empty slice removal must not invalidate iterators");
        values.setSlice(0, 1, 2, List.of(99));
        expect(ConcurrentModificationException.class, iterator::next);
        iterator = values.iterator();
        values.removeSlice(4, 0, -2);
        expect(ConcurrentModificationException.class, iterator::hasNext);
        equalArray(new Object[] {99, 1, 3}, values);
    }

    private static void testSorting() {
        List<String> values = List.of("aa", "b", "cc", "d", "eee");
        java.util.List<String> calls = new java.util.ArrayList<>();
        Function<String, Integer> key = value -> {
            calls.add(value);
            return value.length();
        };
        values.sort(key, true);
        equal(java.util.List.of("aa", "b", "cc", "d", "eee"), calls,
            "Keys must be evaluated once per element in original order");
        equalArray(new Object[] {"eee", "aa", "cc", "b", "d"}, values);
        values.sort(String::length);
        equalArray(new Object[] {"b", "d", "aa", "cc", "eee"}, values);

        calls.clear();
        List.of("only").sort(key);
        equal(java.util.List.of("only"), calls, "A singleton's key is evaluated");
        calls.clear();
        new List<String>().sort(key);
        equal(java.util.List.of(), calls, "An empty list has no keys");

        List<Integer> source = List.of(3, 1, 2);
        equalArray(new Object[] {1, 2, 3}, source.sorted());
        equalArray(new Object[] {3, 1, 2}, source);
        Comparator<Integer> extremeComparator = (left, right) ->
            left < right ? Integer.MIN_VALUE : (left.equals(right) ? 0 : 1);
        source.sort(extremeComparator, true);
        equalArray(new Object[] {3, 2, 1}, source);

        Iterator<Integer> iterator = source.iterator();
        source.sort();
        expect(ConcurrentModificationException.class, iterator::next);
    }

    private static void testSortingFailures() {
        List<Integer> values = List.of(6, 4, 2, 5, 3, 1);
        Object[] before = values.toArray();
        int[] calls = {0};
        Iterator<Integer> iterator = values.iterator();
        Comparator<Integer> failingComparator = (left, right) -> {
            if (++calls[0] == 4) {
                throw new IllegalStateException("Comparison failed");
            }
            return Integer.compare(left, right);
        };
        expect(IllegalStateException.class, () -> values.sort(failingComparator));
        equalArray(before, values);
        equal(6, iterator.next(), "Failed sorting must not invalidate iterators");

        Function<Integer, Integer> failingKey = value -> {
            if (value == 2) {
                throw new IllegalStateException("Key failed");
            }
            return -value;
        };
        expect(IllegalStateException.class, () -> values.sort(failingKey));
        equalArray(before, values);

        List<Integer> modified = List.of(3, 2, 1);
        Comparator<Integer> modifyingComparator = (left, right) -> {
            if (modified.size() == 3) {
                modified.append(4);
            }
            return Integer.compare(left, right);
        };
        expect(ConcurrentModificationException.class,
            () -> modified.sort(modifyingComparator));
        equalArray(new Object[] {3, 2, 1, 4}, modified);

        List<Integer> modifiedByKey = List.of(2, 1);
        Function<Integer, Integer> modifyingKey = value -> {
            modifiedByKey.clear();
            return value;
        };
        expect(ConcurrentModificationException.class,
            () -> modifiedByKey.sort(modifyingKey));
        equal(0, modifiedByKey.size(), "Do not overwrite callback mutations");
    }

    private static void equalArray(Object[] expected, List<?> actual) {
        if (!Arrays.equals(expected, actual.toArray())) {
            throw new AssertionError("Expected " + Arrays.toString(expected)
                + " but got " + Arrays.toString(actual.toArray()));
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return;
            }
            throw new AssertionError("Expected " + type.getName()
                + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
