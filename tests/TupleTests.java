package DataStructures;

import java.util.NoSuchElementException;
import java.util.Spliterator;

import static DataStructures.TestSupport.*;

public final class TupleTests {
    private TupleTests() { }

    public static void run() {
        Object[] input = {"a", null, 3};
        Tuple tuple = Tuple.of(input);
        input[0] = "changed";
        equal("a", tuple.first(), "constructor copies input");
        Object[] output = tuple.toArray();
        output[0] = "changed again";
        equal("a", tuple.first(), "array conversion copies data");
        equal(3, tuple.get(-1, Integer.class), "typed negative getter");
        equal(null, tuple.get(1, String.class), "typed null getter");
        throwsType(ClassCastException.class, () -> tuple.get(0, Integer.class));
        throwsType(IndexOutOfBoundsException.class, () -> tuple.get(Integer.MIN_VALUE));
        throwsType(NoSuchElementException.class, Tuple.EMPTY::first);
        throwsType(NoSuchElementException.class, Tuple.EMPTY::last);
        throwsType(NoSuchElementException.class, () -> tuple.index("missing"));
        throwsType(UnsupportedOperationException.class, () -> tuple.toJavaList().add(4));
        check(Tuple.from(tuple) == tuple, "immutable conversion reuses tuple");
        equal(tuple, Tuple.from(tuple.toJavaList()), "Java conversion round trip");
        equal(tuple.hashCode(), Tuple.from(tuple.toJavaList()).hashCode(), "equal tuple hash");
        check(tuple.spliterator().hasCharacteristics(Spliterator.IMMUTABLE), "immutable traversal");
        equal(tuple.toJavaList(), tuple.parallelStream().toList(), "parallel order");
        equal(Tuple.EMPTY, tuple.repeat(0), "zero repeat");
        equal(Tuple.of(1, 2, 1, 2), Tuple.of(1, 2).repeat(2), "repeat");
        throwsType(OutOfMemoryError.class, () -> tuple.repeat(Integer.MAX_VALUE));
        equal(Tuple.of(1, 2, 3), Tuple.of(1).concat(Tuple.of(2, 3)), "concat");
        equal(List.of(1, 2, 3), Tuple.of(3, 1, 2).sorted(), "sorted returns custom list");

        int[] bounds = {Integer.MIN_VALUE, -20, -8, -2, -1, 0, 1, 2, 7, 20, Integer.MAX_VALUE};
        int[] steps = {Integer.MIN_VALUE, -20, -3, -1, 1, 2, 20, Integer.MAX_VALUE};
        for (int size = 0; size <= 8; size++) {
            Object[] values = new Object[size];
            for (int i = 0; i < size; i++) {
                values[i] = i;
            }
            Tuple source = Tuple.of(values);
            for (int step : steps) {
                equal(expectedSlice(size, null, null, step), source.sliceAll(step).toJavaList(), "sliceAll");
                for (int start : bounds) {
                    equal(expectedSlice(size, start, null, step), source.sliceFrom(start, step).toJavaList(), "sliceFrom");
                    equal(expectedSlice(size, null, start, step), source.sliceTo(start, step).toJavaList(), "sliceTo");
                    for (int end : bounds) {
                        equal(expectedSlice(size, start, end, step), source.slice(start, end, step).toJavaList(), "explicit slice");
                    }
                }
            }
            throwsType(IllegalArgumentException.class, () -> source.sliceAll(0));
        }
    }

    // Independent oracle: select valid positions by range and congruence,
    // rather than stepping an index like the collection implementation.
    private static java.util.List<Object> expectedSlice(int size, Integer start, Integer end, int step) {
        long from = bound(size, start, step > 0 ? 0 : size - 1, step);
        long to = bound(size, end, step > 0 ? size : -1, step);
        java.util.List<Object> result = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (step > 0 ? i >= from && i < to : i <= from && i > to) {
                if ((i - from) % (long) step == 0) {
                    result.add(i);
                }
            }
        }
        if (step < 0) {
            java.util.Collections.reverse(result);
        }
        return result;
    }

    private static long bound(int size, Integer value, int omitted, int step) {
        if (value == null) {
            return omitted;
        }
        long adjusted = value < 0 ? (long) size + value : value;
        return Math.max(step > 0 ? 0 : -1, Math.min(adjusted, step > 0 ? size : size - 1));
    }
}
