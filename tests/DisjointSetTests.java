package DataStructures;

import static DataStructures.TestSupport.*;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Spliterator;

public final class DisjointSetTests {
    private DisjointSetTests() { }

    public static void run() {
        constructionAndMembership();
        unionsAndSizes();
        componentsAndRepresentatives();
        copyIterationAndStreams();
        randomizedAgainstReference();
    }

    private static void constructionAndMembership() {
        DisjointSet<String> empty = new DisjointSet<>();
        equal(0, empty.size(), "empty size");
        equal(0, empty.componentCount(), "empty component count");
        check(empty.isEmpty(), "empty state");
        equal(List.of(), empty.elements(), "empty elements");
        equal(List.of(), empty.components(), "empty components");

        DisjointSet<String> set = DisjointSet.of("A", "B", "A", "C");
        equal(3, set.size(), "factory removes duplicates");
        equal(3, set.componentCount(), "singleton components");
        equal(List.of("A", "B", "C"), set.elements(), "insertion order");
        check(set.contains("B"), "contains present");
        check(!set.contains("missing"), "contains absent");
        equal("B", set.find("B"), "singleton representative");
        equal(1, set.componentSize("B"), "singleton size");

        check(set.add("D"), "new add");
        check(!set.add("D"), "duplicate add");
        equal(4, set.size(), "size after add");
        equal(4, set.componentCount(), "components after add");

        throwsType(NullPointerException.class, () -> set.add(null));
        throwsType(NullPointerException.class, () -> set.contains(null));
        throwsType(NullPointerException.class, () -> set.find(null));
        throwsType(NoSuchElementException.class, () -> set.find("missing"));
        throwsType(NoSuchElementException.class, () -> set.componentSize("missing"));
    }

    private static void unionsAndSizes() {
        DisjointSet<String> set = DisjointSet.of("A", "B", "C", "D", "E");

        check(set.union("A", "B"), "merge A/B");
        equal("A", set.find("A"), "A representative");
        equal("A", set.find("B"), "B representative");
        equal(2, set.componentSize("B"), "A/B component size");
        equal(4, set.componentCount(), "count after first union");

        check(set.union("C", "D"), "merge C/D");
        check(set.union("A", "C"), "merge equal-sized components");
        equal("A", set.find("D"), "first representative wins equal-size tie");
        equal(4, set.componentSize("C"), "merged size");
        check(set.connected("B", "D"), "connected after merge");
        check(!set.connected("A", "E"), "separate singleton");

        check(set.union("E", "B"), "smaller first argument attaches to larger root");
        equal("A", set.find("E"), "union by size representative");
        equal(5, set.componentSize("E"), "all values component size");
        equal(1, set.componentCount(), "single component");
        check(!set.union("B", "E"), "union already connected");
        check(set.connected("C", "E"), "all values connected");

        throwsType(NoSuchElementException.class, () -> set.union("A", "missing"));
        throwsType(NoSuchElementException.class, () -> set.connected("missing", "A"));
    }

    private static void componentsAndRepresentatives() {
        DisjointSet<String> set = DisjointSet.of("A", "B", "C", "D", "E", "F");
        set.union("B", "D");
        set.union("A", "C");
        set.union("D", "F");

        equal(
            List.of(
                List.of("A", "C"),
                List.of("B", "D", "F"),
                List.of("E")
            ),
            set.components(),
            "deterministic components"
        );
        equal(List.of("A", "B", "E"), set.representatives(),
            "representatives in component order");

        DisjointSet<String> reversedRoot = DisjointSet.of("A", "B");
        reversedRoot.union("B", "A");
        equal(List.of("B"), reversedRoot.representatives(),
            "actual root returned even when later inserted");
        equal(List.of(List.of("A", "B")), reversedRoot.components(),
            "component element order remains insertion order");

        set.clear();
        check(set.isEmpty(), "clear elements");
        equal(0, set.componentCount(), "clear components");
        equal(List.of(), set.representatives(), "clear representatives");
        set.clear();
    }

    private static void copyIterationAndStreams() {
        DisjointSet<String> set = DisjointSet.of("A", "B", "C");
        set.union("B", "A");

        DisjointSet<String> copy = set.copy();
        check(copy != set, "copy identity");
        equal(set.elements(), copy.elements(), "copy elements");
        equal(set.components(), copy.components(), "copy components");
        equal(set.representatives(), copy.representatives(),
            "copy representatives");

        copy.add("D");
        check(!set.contains("D"), "copy membership independent");
        copy.union("C", "D");
        check(!set.connected("A", "C"), "copy union does not affect source");

        Iterator<String> stableAcrossUnion = set.iterator();
        equal("A", stableAcrossUnion.next(), "iterator first element");
        set.union("A", "C");
        equal("B", stableAcrossUnion.next(), "union does not invalidate iterator");

        Iterator<String> failFast = set.iterator();
        failFast.next();
        set.add("D");
        throwsType(ConcurrentModificationException.class, failFast::hasNext);

        Iterator<String> cannotRemove = set.iterator();
        cannotRemove.next();
        throwsType(UnsupportedOperationException.class, cannotRemove::remove);

        var stream = set.stream();
        set.add("E");
        equal(java.util.List.of("A", "B", "C", "D", "E"), stream.toList(),
            "stream late binding");
        equal((long) set.size(), set.parallelStream().count(),
            "parallel stream count");

        Spliterator<String> split = set.spliterator();
        int characteristics = Spliterator.ORDERED | Spliterator.DISTINCT
            | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL;
        check(split.hasCharacteristics(characteristics),
            "spliterator characteristics");
        equal((long) set.size(), split.getExactSizeIfKnown(),
            "spliterator exact size");
    }

    private static void randomizedAgainstReference() {
        DisjointSet<Integer> actual = new DisjointSet<>();
        ReferenceDisjointSet expected = new ReferenceDisjointSet();
        Random random = new Random(0xD15A017L);

        for (int step = 0; step < 30_000; step++) {
            int first = random.nextInt(64);
            int second = random.nextInt(64);

            switch (random.nextInt(7)) {
                case 0, 1 ->
                    equal(expected.add(first), actual.add(first), "random add");
                case 2 -> {
                    if (expected.contains(first) && expected.contains(second)) {
                        equal(expected.union(first, second),
                            actual.union(first, second), "random union");
                    } else {
                        throwsType(NoSuchElementException.class,
                            () -> actual.union(first, second));
                    }
                }
                case 3 -> {
                    if (expected.contains(first) && expected.contains(second)) {
                        equal(expected.connected(first, second),
                            actual.connected(first, second), "random connected");
                    }
                }
                case 4 -> {
                    if (expected.contains(first)) {
                        equal(expected.find(first), actual.find(first), "random find");
                        equal(expected.componentSize(first),
                            actual.componentSize(first), "random component size");
                    } else {
                        throwsType(NoSuchElementException.class,
                            () -> actual.find(first));
                    }
                }
                case 5 -> {
                    if (random.nextInt(80) == 0) {
                        expected.clear();
                        actual.clear();
                    }
                }
                case 6 ->
                    equal(expected.contains(first), actual.contains(first),
                        "random contains");
                default -> throw new AssertionError("Unknown operation");
            }

            equal(expected.size(), actual.size(), "random size");
            equal(expected.componentCount(), actual.componentCount(),
                "random component count");
            equal(expected.elements(), asJavaList(actual.elements()),
                "random element order");

            if (step % 200 == 0) {
                equal(expected.components(), nestedAsJava(actual.components()),
                    "random components");
                equal(expected.representatives(),
                    asJavaList(actual.representatives()),
                    "random representatives");
            }
        }
    }

    private static <T> java.util.List<T> asJavaList(Iterable<T> values) {
        ArrayList<T> result = new ArrayList<>();
        for (T value : values) {
            result.add(value);
        }
        return result;
    }

    private static <T> java.util.List<java.util.List<T>> nestedAsJava(
        Iterable<? extends Iterable<T>> values
    ) {
        ArrayList<java.util.List<T>> result = new ArrayList<>();
        for (Iterable<T> component : values) {
            result.add(asJavaList(component));
        }
        return result;
    }

    private static final class ReferenceDisjointSet {
        private final LinkedHashMap<Integer, RefNode> nodes = new LinkedHashMap<>();
        private int componentCount;

        private boolean add(int value) {
            if (nodes.containsKey(value)) {
                return false;
            }
            nodes.put(value, new RefNode(value));
            componentCount++;
            return true;
        }

        private boolean contains(int value) {
            return nodes.containsKey(value);
        }

        private int size() {
            return nodes.size();
        }

        private int componentCount() {
            return componentCount;
        }

        private int find(int value) {
            return root(require(value)).value;
        }

        private int componentSize(int value) {
            return root(require(value)).size;
        }

        private boolean connected(int first, int second) {
            return root(require(first)) == root(require(second));
        }

        private boolean union(int first, int second) {
            RefNode firstRoot = root(require(first));
            RefNode secondRoot = root(require(second));

            if (firstRoot == secondRoot) {
                return false;
            }

            if (firstRoot.size < secondRoot.size) {
                RefNode temporary = firstRoot;
                firstRoot = secondRoot;
                secondRoot = temporary;
            }

            secondRoot.parent = firstRoot;
            firstRoot.size += secondRoot.size;
            componentCount--;
            return true;
        }

        private java.util.List<Integer> elements() {
            return new ArrayList<>(nodes.keySet());
        }

        private java.util.List<Integer> representatives() {
            ArrayList<Integer> result = new ArrayList<>();
            java.util.IdentityHashMap<RefNode, Boolean> seen =
                new java.util.IdentityHashMap<>();

            for (RefNode node : nodes.values()) {
                RefNode root = root(node);
                if (!seen.containsKey(root)) {
                    seen.put(root, Boolean.TRUE);
                    result.add(root.value);
                }
            }
            return result;
        }

        private java.util.List<java.util.List<Integer>> components() {
            LinkedHashMap<RefNode, ArrayList<Integer>> grouped = new LinkedHashMap<>();

            for (RefNode node : nodes.values()) {
                RefNode root = root(node);
                grouped.computeIfAbsent(root, ignored -> new ArrayList<>())
                    .add(node.value);
            }

            return new ArrayList<>(grouped.values());
        }

        private void clear() {
            nodes.clear();
            componentCount = 0;
        }

        private RefNode require(int value) {
            RefNode node = nodes.get(value);
            if (node == null) {
                throw new NoSuchElementException();
            }
            return node;
        }

        private RefNode root(RefNode node) {
            RefNode root = node;
            while (root.parent != root) {
                root = root.parent;
            }

            RefNode current = node;
            while (current.parent != current) {
                RefNode next = current.parent;
                current.parent = root;
                current = next;
            }
            return root;
        }
    }

    private static final class RefNode {
        private final int value;
        private RefNode parent;
        private int size = 1;

        private RefNode(int value) {
            this.value = value;
            parent = this;
        }
    }
}
