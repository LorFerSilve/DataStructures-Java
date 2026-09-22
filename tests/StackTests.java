package DataStructures;

import static DataStructures.TestSupport.*;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

public final class StackTests {
    private StackTests() { }

    public static void run() {
        constructionAndCapacity();
        lifoOperations();
        nullsAndSearch();
        copyAndConversions();
        traversalContracts();
        randomizedAgainstReference();
    }

    private static void constructionAndCapacity() {
        Stack<Integer> empty = new Stack<>();
        equal(0, empty.size(), "new stack size");
        check(empty.isEmpty(), "new stack empty");
        check(empty.capacity() >= 0, "new stack capacity");
        throwsType(IllegalArgumentException.class, () -> new Stack<>(-1));

        Stack<Integer> exact = new Stack<>(0);
        equal(0, exact.capacity(), "zero capacity accepted");
        exact.push(1);
        check(exact.capacity() >= 1, "zero-capacity stack grows");
        exact.trimToSize();
        equal(1, exact.capacity(), "trim to current size");

        Stack<Integer> fromIterable = new Stack<>(java.util.List.of(1, 2, 3));
        equal(java.util.List.of(1, 2, 3), fromIterable.toJavaList(), "iterable constructor order");
        equal(3, fromIterable.peek(), "iterable constructor top");

        Stack<Integer> factory = Stack.of(4, 5, 6);
        equal(java.util.List.of(4, 5, 6), factory.toJavaList(), "factory order");
        equal(6, factory.peek(), "factory top");
    }

    private static void lifoOperations() {
        Stack<String> stack = new Stack<>();
        equal("a", stack.push("a"), "push returns value");
        stack.push("b");
        stack.push("c");
        equal("c", stack.peek(), "peek top");
        equal(3, stack.size(), "peek preserves size");
        equal("c", stack.pop(), "first pop");
        equal("b", stack.pop(), "second pop");
        equal("a", stack.pop(), "third pop");
        check(stack.isEmpty(), "stack empty after pops");
        throwsType(NoSuchElementException.class, stack::peek);
        throwsType(NoSuchElementException.class, stack::pop);

        int retained = stack.capacity();
        stack.push("x");
        stack.push("y");
        stack.clear();
        equal(0, stack.size(), "clear size");
        equal(retained, stack.capacity(), "clear retains capacity");
        stack.clear();
        equal(0, stack.size(), "clear empty no-op");
    }

    private static void nullsAndSearch() {
        Stack<String> stack = Stack.of("bottom", null, "middle", null, "top");
        equal("top", stack.peek(), "top with null elements below");
        equal(2, stack.search(null), "nearest null distance from top");
        equal(5, stack.search("bottom"), "bottom distance from top");
        equal(-1, stack.search("missing"), "missing search");
        check(stack.contains(null), "contains null");
        check(stack.contains("middle"), "contains value");
        check(!stack.contains("missing"), "does not contain missing value");
        equal("top", stack.pop(), "pop above null");
        equal(null, stack.pop(), "pop null value");
    }

    private static void copyAndConversions() {
        Stack<Object> original = new Stack<>();
        Object shared = new Object();
        original.push("first");
        original.push(shared);
        original.push(3);

        Stack<Object> copy = original.copy();
        check(copy != original, "copy has independent identity");
        equal(original.toJavaList(), copy.toJavaList(), "copy content");
        copy.pop();
        equal(3, original.size(), "copy mutation does not affect original");
        equal(2, copy.size(), "copy mutation size");

        Object[] array = original.toArray();
        equal(3, array.length, "array length");
        equal("first", array[0], "array bottom");
        check(array[1] == shared, "array is shallow");
        equal(3, array[2], "array top");

        equal(java.util.List.of("first", shared, 3), original.toJavaList(), "java list snapshot");
        throwsType(UnsupportedOperationException.class, () -> original.toJavaList().add("x"));
        equal(List.of("first", shared, 3), original.toDataList(), "custom list snapshot");
        equal(java.util.List.of("first", shared, 3), original.stream().toList(), "stream order");
        equal(3L, original.parallelStream().count(), "parallel stream count");
    }

    private static void traversalContracts() {
        Stack<Integer> stack = Stack.of(1, 2, 3);
        Iterator<Integer> iterator = stack.iterator();
        equal(1, iterator.next(), "iterator starts at bottom");
        stack.push(4);
        throwsType(ConcurrentModificationException.class, iterator::hasNext);

        stack = new Stack<>(10);
        stack.push(1);
        stack.push(2);
        stack.push(3);
        Iterator<Integer> stable = stack.iterator();
        stack.trimToSize();
        equal(3, stack.capacity(), "trim actually changes capacity");
        equal(1, stable.next(), "capacity-only trim keeps iterator valid");

        stack = Stack.of(1, 2, 3);
        var spliterator = stack.spliterator();
        equal(3L, spliterator.estimateSize(), "spliterator size");
        stack.pop();
        throwsType(ConcurrentModificationException.class, spliterator::estimateSize);
    }

    private static void randomizedAgainstReference() {
        Stack<Integer> stack = new Stack<>();
        java.util.ArrayList<Integer> reference = new java.util.ArrayList<>();
        Random random = new Random(0x5A17C0DEL);

        for (int step = 0; step < 20_000; step++) {
            int operation = random.nextInt(5);
            if (operation <= 2 || reference.isEmpty()) {
                Integer value = random.nextInt(8) == 0 ? null : random.nextInt(101) - 50;
                stack.push(value);
                reference.add(value);
            } else if (operation == 3) {
                Integer expected = reference.remove(reference.size() - 1);
                equal(expected, stack.pop(), "random pop at step " + step);
            } else {
                equal(reference.get(reference.size() - 1), stack.peek(),
                    "random peek at step " + step);
            }

            equal(reference.size(), stack.size(), "random size at step " + step);
            if (!reference.isEmpty()) {
                equal(reference.get(reference.size() - 1), stack.peek(),
                    "random top at step " + step);
            }
        }

        equal(reference, stack.toJavaList(), "random final order");
    }
}
