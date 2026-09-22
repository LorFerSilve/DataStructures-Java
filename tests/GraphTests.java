package DataStructures;

import static DataStructures.TestSupport.*;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Spliterator;

public final class GraphTests {
    private GraphTests() { }

    public static void run() {
        constructionAndVertices();
        undirectedEdgesAndRemoval();
        directedEdgesAndDegrees();
        traversalsAndPaths();
        componentsCyclesAndTopology();
        copyIterationAndStreams();
        randomizedAgainstReference(false);
        randomizedAgainstReference(true);
    }

    private static void constructionAndVertices() {
        Graph<String> graph = new Graph<>();

        check(!graph.isDirected(), "default graph undirected");
        equal(0, graph.vertexCount(), "empty vertex count");
        equal(0, graph.edgeCount(), "empty edge count");
        check(graph.isEmpty(), "empty predicate");
        equal(List.of(), graph.vertices(), "empty vertex snapshot");

        check(graph.addVertex("A"), "first vertex");
        check(graph.addVertex("B"), "second vertex");
        check(!graph.addVertex("A"), "duplicate vertex");
        equal(List.of("A", "B"), graph.vertices(), "insertion order");
        check(graph.containsVertex("A"), "contains vertex");
        check(!graph.containsVertex("C"), "missing vertex");

        throwsType(NullPointerException.class, () -> graph.addVertex(null));
        throwsType(NullPointerException.class, () -> graph.containsVertex(null));
        throwsType(IllegalArgumentException.class,
            () -> new Graph<List<Integer>>().addVertex(List.of(1)));

        Graph<Integer> directed = new Graph<>(true);
        check(directed.isDirected(), "directed constructor");
    }

    private static void undirectedEdgesAndRemoval() {
        Graph<String> graph = new Graph<>();

        for (String value : java.util.List.of("A", "B", "C", "D")) {
            graph.addVertex(value);
        }

        throwsType(NoSuchElementException.class, () -> graph.addEdge("A", "missing"));

        check(graph.addEdge("A", "B"), "first undirected edge");
        check(graph.addEdge("A", "C"), "second undirected edge");
        check(!graph.addEdge("B", "A"), "reverse duplicate rejected");
        check(graph.addEdge("C", "C"), "self-loop");

        equal(3, graph.edgeCount(), "undirected edge count");
        check(graph.containsEdge("A", "B"), "forward undirected edge");
        check(graph.containsEdge("B", "A"), "reverse undirected edge");
        check(graph.containsEdge("C", "C"), "self-loop present");
        check(!graph.containsEdge("A", "D"), "missing edge");

        equal(List.of("B", "C"), graph.neighbors("A"), "A neighbors");
        equal(2, graph.degree("A"), "regular undirected degree");
        equal(3, graph.degree("C"), "self-loop contributes two");
        equal(3, graph.inDegree("C"), "undirected indegree aliases degree");
        equal(3, graph.outDegree("C"), "undirected outdegree aliases degree");

        equal(
            List.of(
                Tuple.of("A", "B"),
                Tuple.of("A", "C"),
                Tuple.of("C", "C")
            ),
            graph.edgeList(),
            "undirected edge list once per edge"
        );

        check(graph.removeEdge("B", "A"), "remove reverse orientation");
        check(!graph.containsEdge("A", "B"), "both directions removed");
        equal(2, graph.edgeCount(), "edge count after removal");
        check(!graph.removeEdge("A", "B"), "missing edge removal false");

        check(graph.removeVertex("C"), "remove incident vertex");
        equal(2, graph.vertexCount(), "vertex count after removal");
        equal(0, graph.edgeCount(), "incident edges removed");
        equal(List.of("A", "B", "D"), graph.vertices(),
            "remaining insertion order after vertex removal");
        check(!graph.removeVertex("missing"), "missing vertex removal false");

        graph.clear();
        check(graph.isEmpty(), "clear vertices");
        equal(0, graph.edgeCount(), "clear edges");
        graph.clear();
    }

    private static void directedEdgesAndDegrees() {
        Graph<String> graph = new Graph<>(true);

        for (String value : java.util.List.of("A", "B", "C", "D")) {
            graph.addVertex(value);
        }

        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("C", "A");
        graph.addEdge("C", "C");
        graph.addEdge("D", "C");

        equal(5, graph.edgeCount(), "directed edge count");
        check(graph.containsEdge("A", "B"), "directed edge present");
        check(!graph.containsEdge("B", "A"), "reverse directed edge absent");
        equal(2, graph.outDegree("A"), "outdegree");
        equal(1, graph.inDegree("A"), "indegree");
        equal(2, graph.outDegree("C"), "outdegree with loop");
        equal(3, graph.inDegree("C"), "indegree with loop");
        throwsType(IllegalStateException.class, () -> graph.degree("A"));
        throwsType(IllegalStateException.class, graph::connectedComponents);
        throwsType(IllegalStateException.class, graph::isConnected);

        check(graph.removeVertex("C"), "remove directed vertex");
        equal(3, graph.vertexCount(), "directed vertex removal count");
        equal(1, graph.edgeCount(), "incoming and outgoing edges removed");
        check(graph.containsEdge("A", "B"), "unrelated edge preserved");
    }

    private static void traversalsAndPaths() {
        Graph<String> graph = new Graph<>();

        for (String value : java.util.List.of("A", "B", "C", "D", "E", "F", "G")) {
            graph.addVertex(value);
        }

        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        graph.addEdge("C", "E");
        graph.addEdge("D", "F");

        equal(List.of("A", "B", "C", "D", "E", "F"),
            graph.breadthFirst("A"), "BFS order");
        equal(List.of("A", "B", "D", "F", "C", "E"),
            graph.depthFirst("A"), "DFS order");
        equal(List.of("A", "C", "E"),
            graph.shortestPath("A", "E"), "shortest path");
        equal(List.of("E", "C", "A", "B", "D", "F"),
            graph.shortestPath("E", "F"), "undirected shortest path");
        equal(List.of("A"), graph.shortestPath("A", "A"),
            "zero-edge path");
        equal(List.of(), graph.shortestPath("A", "G"),
            "unreachable path empty");
        check(graph.hasPath("A", "F"), "reachable path predicate");
        check(!graph.hasPath("A", "G"), "unreachable path predicate");

        throwsType(NoSuchElementException.class, () -> graph.breadthFirst("missing"));
        throwsType(NoSuchElementException.class, () -> graph.depthFirst("missing"));
        throwsType(NoSuchElementException.class,
            () -> graph.shortestPath("A", "missing"));
    }

    private static void componentsCyclesAndTopology() {
        Graph<Integer> forest = new Graph<>();
        for (int value = 1; value <= 7; value++) {
            forest.addVertex(value);
        }

        forest.addEdge(1, 2);
        forest.addEdge(2, 3);
        forest.addEdge(4, 5);

        equal(
            List.of(
                List.of(1, 2, 3),
                List.of(4, 5),
                List.of(6),
                List.of(7)
            ),
            forest.connectedComponents(),
            "connected components"
        );
        check(!forest.isConnected(), "forest disconnected");
        check(!forest.hasCycle(), "forest acyclic");

        forest.addEdge(1, 3);
        check(forest.hasCycle(), "undirected triangle cycle");

        Graph<Integer> loop = new Graph<>();
        loop.addVertex(1);
        loop.addEdge(1, 1);
        check(loop.hasCycle(), "undirected self-loop cycle");

        Graph<String> dag = new Graph<>(true);
        for (String value : java.util.List.of("A", "B", "C", "D")) {
            dag.addVertex(value);
        }
        dag.addEdge("A", "B");
        dag.addEdge("A", "C");
        dag.addEdge("B", "D");
        dag.addEdge("C", "D");

        check(!dag.hasCycle(), "DAG acyclic");
        equal(List.of("A", "B", "C", "D"),
            dag.topologicalSort(), "deterministic topological order");

        dag.addEdge("D", "A");
        check(dag.hasCycle(), "directed cycle");
        throwsType(IllegalStateException.class, dag::topologicalSort);

        throwsType(IllegalStateException.class,
            () -> new Graph<Integer>().topologicalSort());

        Graph<Integer> connected = new Graph<>();
        connected.addVertex(1);
        connected.addVertex(2);
        connected.addEdge(1, 2);
        check(connected.isConnected(), "connected graph");

        check(new Graph<Integer>().isConnected(),
            "empty undirected graph connected by convention");
    }

    private static void copyIterationAndStreams() {
        Graph<String> graph = new Graph<>(true);
        for (String value : java.util.List.of("A", "B", "C")) {
            graph.addVertex(value);
        }
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");

        Graph<String> copy = graph.copy();
        check(copy != graph, "copy identity");
        equal(graph.vertices(), copy.vertices(), "copy vertices");
        equal(graph.edgeList(), copy.edgeList(), "copy edges");
        equal(graph.isDirected(), copy.isDirected(), "copy directionality");

        copy.addVertex("D");
        check(!graph.containsVertex("D"), "copy vertex mutation independent");
        graph.addEdge("A", "C");
        check(!copy.containsEdge("A", "C"), "copy edge mutation independent");

        Iterator<String> iterator = graph.iterator();
        equal("A", iterator.next(), "iterator first vertex");
        graph.removeEdge("A", "B");
        throwsType(ConcurrentModificationException.class, iterator::hasNext);

        Iterator<String> cannotRemove = graph.iterator();
        cannotRemove.next();
        throwsType(UnsupportedOperationException.class, cannotRemove::remove);

        var stream = graph.stream();
        graph.addVertex("D");
        equal(asJavaList(graph.vertices()), stream.toList(),
            "stream late binding");
        equal((long) graph.vertexCount(), graph.parallelStream().count(),
            "parallel stream count");

        Spliterator<String> split = graph.spliterator();
        int characteristics = Spliterator.ORDERED | Spliterator.DISTINCT
            | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL;
        check(split.hasCharacteristics(characteristics),
            "spliterator characteristics");
        equal((long) graph.vertexCount(), split.getExactSizeIfKnown(),
            "spliterator exact size");

        Spliterator<String> prefix = split.trySplit();
        check(prefix != null, "spliterator split");
        ArrayList<String> combined = new ArrayList<>();
        prefix.forEachRemaining(combined::add);
        split.forEachRemaining(combined::add);
        equal(asJavaList(graph.vertices()), combined, "split preserves order");

        Spliterator<String> bound = graph.spliterator();
        bound.estimateSize();
        graph.addEdge("B", "A");
        throwsType(ConcurrentModificationException.class, bound::estimateSize);

        Spliterator<String> callback = graph.spliterator();
        throwsType(ConcurrentModificationException.class,
            () -> callback.tryAdvance(value -> graph.clear()));
    }

    private static void randomizedAgainstReference(boolean directed) {
        Graph<Integer> actual = new Graph<>(directed);
        ReferenceGraph expected = new ReferenceGraph(directed);
        Random random = new Random(directed ? 0xD1A6A9L : 0xA11CE5L);

        for (int step = 0; step < 20_000; step++) {
            int first = random.nextInt(24);
            int second = random.nextInt(24);

            switch (random.nextInt(9)) {
                case 0, 1 ->
                    equal(expected.addVertex(first), actual.addVertex(first),
                        "random add vertex");
                case 2 ->
                    equal(expected.removeVertex(first), actual.removeVertex(first),
                        "random remove vertex");
                case 3, 4 -> {
                    if (expected.containsVertex(first)
                        && expected.containsVertex(second)) {
                        equal(expected.addEdge(first, second),
                            actual.addEdge(first, second),
                            "random add edge");
                    } else {
                        throwsType(NoSuchElementException.class,
                            () -> actual.addEdge(first, second));
                    }
                }
                case 5 ->
                    equal(expected.removeEdge(first, second),
                        actual.removeEdge(first, second),
                        "random remove edge");
                case 6 ->
                    equal(expected.containsEdge(first, second),
                        actual.containsEdge(first, second),
                        "random contains edge");
                case 7 -> {
                    if (expected.containsVertex(first)) {
                        equal(expected.neighbors(first),
                            asJavaList(actual.neighbors(first)),
                            "random neighbors");
                    }
                }
                case 8 -> {
                    if (random.nextInt(40) == 0) {
                        expected.clear();
                        actual.clear();
                    }
                }
                default -> throw new AssertionError("Unknown random graph operation");
            }

            assertReferenceState(actual, expected);

            if (step % 250 == 0 && !expected.vertices.isEmpty()) {
                int root = expected.vertices.keySet().iterator().next();
                equal(expected.breadthFirst(root),
                    asJavaList(actual.breadthFirst(root)),
                    "random BFS");
            }
        }
    }

    private static void assertReferenceState(
        Graph<Integer> actual,
        ReferenceGraph expected
    ) {
        equal(expected.vertices.size(), actual.vertexCount(), "vertex count");
        equal(expected.edgeCount, actual.edgeCount(), "edge count");
        equal(new ArrayList<>(expected.vertices.keySet()),
            asJavaList(actual.vertices()), "vertex order");

        for (Map.Entry<Integer, LinkedHashSet<Integer>> entry
            : expected.vertices.entrySet()) {
            equal(new ArrayList<>(entry.getValue()),
                asJavaList(actual.neighbors(entry.getKey())),
                "neighbor order");
        }
    }

    private static <T> java.util.List<T> asJavaList(Iterable<T> values) {
        ArrayList<T> result = new ArrayList<>();
        for (T value : values) {
            result.add(value);
        }
        return result;
    }

    private static final class ReferenceGraph {
        private final boolean directed;
        private final LinkedHashMap<Integer, LinkedHashSet<Integer>> vertices =
            new LinkedHashMap<>();
        private int edgeCount;

        private ReferenceGraph(boolean directed) {
            this.directed = directed;
        }

        private boolean containsVertex(int value) {
            return vertices.containsKey(value);
        }

        private boolean addVertex(int value) {
            if (vertices.containsKey(value)) {
                return false;
            }
            vertices.put(value, new LinkedHashSet<>());
            return true;
        }

        private boolean removeVertex(int value) {
            LinkedHashSet<Integer> removed = vertices.get(value);
            if (removed == null) {
                return false;
            }

            if (directed) {
                edgeCount -= removed.size();
                for (Map.Entry<Integer, LinkedHashSet<Integer>> entry
                    : vertices.entrySet()) {
                    if (entry.getKey() != value && entry.getValue().remove(value)) {
                        edgeCount--;
                    }
                }
            } else {
                edgeCount -= removed.size();
                for (int neighbor : new ArrayList<>(removed)) {
                    if (neighbor != value) {
                        vertices.get(neighbor).remove(value);
                    }
                }
            }

            vertices.remove(value);
            return true;
        }

        private boolean addEdge(int from, int to) {
            LinkedHashSet<Integer> fromNeighbors = vertices.get(from);
            LinkedHashSet<Integer> toNeighbors = vertices.get(to);

            if (fromNeighbors == null || toNeighbors == null) {
                throw new NoSuchElementException();
            }

            if (!fromNeighbors.add(to)) {
                return false;
            }

            if (!directed && from != to) {
                toNeighbors.add(from);
            }

            edgeCount++;
            return true;
        }

        private boolean removeEdge(int from, int to) {
            LinkedHashSet<Integer> fromNeighbors = vertices.get(from);
            LinkedHashSet<Integer> toNeighbors = vertices.get(to);

            if (fromNeighbors == null || toNeighbors == null
                || !fromNeighbors.remove(to)) {
                return false;
            }

            if (!directed && from != to) {
                toNeighbors.remove(from);
            }

            edgeCount--;
            return true;
        }

        private boolean containsEdge(int from, int to) {
            LinkedHashSet<Integer> neighbors = vertices.get(from);
            return neighbors != null
                && vertices.containsKey(to)
                && neighbors.contains(to);
        }

        private java.util.List<Integer> neighbors(int vertex) {
            return new ArrayList<>(vertices.get(vertex));
        }

        private java.util.List<Integer> breadthFirst(int start) {
            ArrayList<Integer> result = new ArrayList<>();
            LinkedHashSet<Integer> visited = new LinkedHashSet<>();
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();

            visited.add(start);
            queue.add(start);

            while (!queue.isEmpty()) {
                int current = queue.remove();
                result.add(current);

                for (int neighbor : vertices.get(current)) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }

            return result;
        }

        private void clear() {
            vertices.clear();
            edgeCount = 0;
        }
    }
}
