package DataStructures;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A generic adjacency-map graph supporting directed and undirected edges.
 *
 * <p>Vertices are stored in insertion order. Each adjacency list also preserves
 * edge insertion order, making traversals deterministic. Vertices must keep
 * stable equals/hashCode state while stored. Null vertices are rejected and
 * parallel edges are not allowed; self-loops are supported.</p>
 *
 * <p>Edges are unweighted. Breadth-first shortest paths therefore minimize the
 * number of edges. The class provides deterministic BFS/DFS, cycle detection,
 * topological sorting for directed graphs, and connected components for
 * undirected graphs.</p>
 *
 * <p>This class is not thread-safe. Iterators and spliterators are fail-fast on
 * a best-effort basis. Equality and hashing use object identity.</p>
 *
 * @param <V> vertex type
 */
public final class Graph<V> implements Iterable<V> {
    private final boolean directed;
    private final Dictionary<V, Vertex<V>> vertices = new Dictionary<>();
    private int edgeCount;
    private int modCount;

    /** Creates an undirected graph. */
    public Graph() {
        this(false);
    }

    /**
     * @param directed true for directed edges, false for undirected edges
     */
    public Graph(boolean directed) {
        this.directed = directed;
    }

    // ============================================================
    // Basic graph state
    // ============================================================

    public boolean isDirected() {
        return directed;
    }

    public int vertexCount() {
        return vertices.size();
    }

    public int edgeCount() {
        return edgeCount;
    }

    public boolean isEmpty() {
        return vertices.isEmpty();
    }

    public boolean containsVertex(V vertex) {
        requireVertexValue(vertex);
        return vertices.containsKey(vertex);
    }

    /**
     * Adds one vertex.
     *
     * @return false if an equal vertex already exists
     */
    public boolean addVertex(V vertex) {
        requireVertexValue(vertex);

        if (vertices.containsKey(vertex)) {
            return false;
        }

        vertices.put(vertex, new Vertex<>(vertex));
        modCount++;
        return true;
    }

    /**
     * Removes a vertex and every incident edge.
     *
     * @return false when the vertex is absent
     */
    public boolean removeVertex(V vertex) {
        requireVertexValue(vertex);
        Vertex<V> removed = vertices.get(vertex, null);
        if (removed == null) {
            return false;
        }

        if (directed) {
            edgeCount -= removed.neighbors.size();

            for (V otherValue : vertices) {
                Vertex<V> other = vertices.get(otherValue);
                if (other == removed) {
                    continue;
                }
                if (other.neighbors.containsKey(removed.value)) {
                    other.neighbors.remove(removed.value);
                    edgeCount--;
                }
            }
        } else {
            edgeCount -= removed.neighbors.size();
            List<V> neighbors = removed.neighbors.keysSnapshot();

            for (V neighborValue : neighbors) {
                Vertex<V> neighbor = vertices.get(neighborValue);
                if (neighbor != removed) {
                    neighbor.neighbors.remove(removed.value);
                }
            }
        }

        vertices.remove(removed.value);
        modCount++;
        return true;
    }

    public void clear() {
        if (vertices.isEmpty()) {
            return;
        }

        vertices.clear();
        edgeCount = 0;
        modCount++;
    }

    // ============================================================
    // Edge operations
    // ============================================================

    /**
     * Adds an edge between two existing vertices.
     *
     * <p>For undirected graphs both adjacency directions are stored internally,
     * while edgeCount increases only once. A self-loop is stored once.</p>
     *
     * @return false if the edge already exists
     * @throws NoSuchElementException when either endpoint is absent
     */
    public boolean addEdge(V from, V to) {
        Vertex<V> source = requireStoredVertex(from);
        Vertex<V> target = requireStoredVertex(to);

        if (source.neighbors.containsKey(target.value)) {
            return false;
        }

        source.neighbors.put(target.value, Boolean.TRUE);

        if (!directed && source != target) {
            target.neighbors.put(source.value, Boolean.TRUE);
        }

        edgeCount++;
        modCount++;
        return true;
    }

    /**
     * Removes an edge.
     *
     * @return false if either endpoint or the edge is absent
     */
    public boolean removeEdge(V from, V to) {
        requireVertexValue(from);
        requireVertexValue(to);

        Vertex<V> source = vertices.get(from, null);
        Vertex<V> target = vertices.get(to, null);

        if (source == null || target == null
            || !source.neighbors.containsKey(target.value)) {
            return false;
        }

        source.neighbors.remove(target.value);

        if (!directed && source != target) {
            target.neighbors.remove(source.value);
        }

        edgeCount--;
        modCount++;
        return true;
    }

    public boolean containsEdge(V from, V to) {
        requireVertexValue(from);
        requireVertexValue(to);

        Vertex<V> source = vertices.get(from, null);
        Vertex<V> target = vertices.get(to, null);

        return source != null
            && target != null
            && source.neighbors.containsKey(target.value);
    }

    /** Returns a shallow neighbor snapshot in edge insertion order. */
    public List<V> neighbors(V vertex) {
        return requireStoredVertex(vertex).neighbors.keysSnapshot();
    }

    /**
     * Returns the mathematical degree of an undirected vertex.
     * Self-loops contribute two.
     */
    public int degree(V vertex) {
        requireUndirected("degree");
        Vertex<V> stored = requireStoredVertex(vertex);
        int degree = stored.neighbors.size();
        if (stored.neighbors.containsKey(stored.value)) {
            degree++;
        }
        return degree;
    }

    /** Directed out-degree; for undirected graphs this equals degree(). */
    public int outDegree(V vertex) {
        if (!directed) {
            return degree(vertex);
        }
        return requireStoredVertex(vertex).neighbors.size();
    }

    /** Directed in-degree; for undirected graphs this equals degree(). */
    public int inDegree(V vertex) {
        if (!directed) {
            return degree(vertex);
        }

        Vertex<V> target = requireStoredVertex(vertex);
        int count = 0;

        for (V value : vertices) {
            Vertex<V> candidate = vertices.get(value);
            if (candidate.neighbors.containsKey(target.value)) {
                count++;
            }
        }

        return count;
    }

    /** Returns vertices in graph insertion order. */
    public List<V> vertices() {
        return vertices.keysSnapshot();
    }

    /**
     * Returns edge endpoint tuples in deterministic order.
     *
     * <p>Undirected edges appear only once. Tuple element 0 is the source
     * endpoint and element 1 is the target endpoint.</p>
     */
    public List<Tuple> edgeList() {
        List<Tuple> result = new List<>(edgeCount);

        if (directed) {
            for (V sourceValue : vertices) {
                Vertex<V> source = vertices.get(sourceValue);
                for (V targetValue : source.neighbors) {
                    result.append(Tuple.of(source.value, targetValue));
                }
            }
            return result;
        }

        Dictionary<V, Integer> positions = new Dictionary<>(vertices.size());
        int position = 0;
        for (V value : vertices) {
            positions.put(value, position++);
        }

        for (V sourceValue : vertices) {
            Vertex<V> source = vertices.get(sourceValue);
            int sourcePosition = positions.get(source.value);

            for (V targetValue : source.neighbors) {
                int targetPosition = positions.get(targetValue);
                if (sourcePosition <= targetPosition) {
                    result.append(Tuple.of(source.value, targetValue));
                }
            }
        }

        return result;
    }

    // ============================================================
    // Traversal / reachability
    // ============================================================

    /** Deterministic breadth-first traversal from one start vertex. */
    public List<V> breadthFirst(V start) {
        Vertex<V> startVertex = requireStoredVertex(start);
        Dictionary<V, Boolean> visited = new Dictionary<>();
        Queue<Vertex<V>> queue = new Queue<>();
        List<V> result = new List<>();

        visited.put(startVertex.value, Boolean.TRUE);
        queue.add(startVertex);

        while (!queue.isEmpty()) {
            Vertex<V> current = queue.remove();
            result.append(current.value);

            for (V neighborValue : current.neighbors) {
                if (!visited.containsKey(neighborValue)) {
                    Vertex<V> neighbor = vertices.get(neighborValue);
                    visited.put(neighbor.value, Boolean.TRUE);
                    queue.add(neighbor);
                }
            }
        }

        return result;
    }

    /**
     * Deterministic iterative depth-first traversal.
     * Earlier-added neighbors are visited first.
     */
    public List<V> depthFirst(V start) {
        Vertex<V> startVertex = requireStoredVertex(start);
        Dictionary<V, Boolean> visited = new Dictionary<>();
        Stack<Vertex<V>> stack = new Stack<>();
        List<V> result = new List<>();

        visited.put(startVertex.value, Boolean.TRUE);
        stack.push(startVertex);

        while (!stack.isEmpty()) {
            Vertex<V> current = stack.pop();
            result.append(current.value);

            List<V> neighborValues = current.neighbors.keysSnapshot();
            for (int index = neighborValues.size() - 1; index >= 0; index--) {
                V neighborValue = neighborValues.get(index);
                if (!visited.containsKey(neighborValue)) {
                    Vertex<V> neighbor = vertices.get(neighborValue);
                    visited.put(neighbor.value, Boolean.TRUE);
                    stack.push(neighbor);
                }
            }
        }

        return result;
    }

    public boolean hasPath(V start, V target) {
        return !shortestPath(start, target).isEmpty();
    }

    /**
     * Returns a minimum-edge path using BFS, or an empty list when unreachable.
     */
    public List<V> shortestPath(V start, V target) {
        Vertex<V> startVertex = requireStoredVertex(start);
        Vertex<V> targetVertex = requireStoredVertex(target);

        if (startVertex == targetVertex) {
            return List.of(startVertex.value);
        }

        Dictionary<V, Boolean> visited = new Dictionary<>();
        Dictionary<V, V> parent = new Dictionary<>();
        Queue<Vertex<V>> queue = new Queue<>();

        visited.put(startVertex.value, Boolean.TRUE);
        parent.put(startVertex.value, null);
        queue.add(startVertex);

        boolean found = false;

        while (!queue.isEmpty() && !found) {
            Vertex<V> current = queue.remove();

            for (V neighborValue : current.neighbors) {
                if (visited.containsKey(neighborValue)) {
                    continue;
                }

                Vertex<V> neighbor = vertices.get(neighborValue);
                visited.put(neighbor.value, Boolean.TRUE);
                parent.put(neighbor.value, current.value);

                if (neighbor == targetVertex) {
                    found = true;
                    break;
                }

                queue.add(neighbor);
            }
        }

        if (!found) {
            return new List<>();
        }

        Stack<V> reversePath = new Stack<>();
        V current = targetVertex.value;

        while (true) {
            reversePath.push(current);
            if (Objects.equals(current, startVertex.value)) {
                break;
            }
            current = parent.get(current);
        }

        List<V> result = new List<>(reversePath.size());
        while (!reversePath.isEmpty()) {
            result.append(reversePath.pop());
        }
        return result;
    }

    // ============================================================
    // Components / cycles / topology
    // ============================================================

    /**
     * Returns connected components of an undirected graph.
     *
     * @throws IllegalStateException for directed graphs
     */
    public List<List<V>> connectedComponents() {
        requireUndirected("connectedComponents");

        List<List<V>> components = new List<>();
        Dictionary<V, Boolean> visited = new Dictionary<>();

        for (V rootValue : vertices) {
            if (visited.containsKey(rootValue)) {
                continue;
            }

            Vertex<V> root = vertices.get(rootValue);
            List<V> component = new List<>();
            Queue<Vertex<V>> queue = new Queue<>();

            visited.put(root.value, Boolean.TRUE);
            queue.add(root);

            while (!queue.isEmpty()) {
                Vertex<V> current = queue.remove();
                component.append(current.value);

                for (V neighborValue : current.neighbors) {
                    if (!visited.containsKey(neighborValue)) {
                        Vertex<V> neighbor = vertices.get(neighborValue);
                        visited.put(neighbor.value, Boolean.TRUE);
                        queue.add(neighbor);
                    }
                }
            }

            components.append(component);
        }

        return components;
    }

    /**
     * Empty and single-vertex undirected graphs are considered connected.
     */
    public boolean isConnected() {
        requireUndirected("isConnected");

        if (vertices.size() <= 1) {
            return true;
        }

        V first = vertices.iterator().next();
        return breadthFirst(first).size() == vertices.size();
    }

    public boolean hasCycle() {
        return directed ? hasDirectedCycle() : hasUndirectedCycle();
    }

    /**
     * Kahn topological ordering for directed acyclic graphs.
     *
     * @throws IllegalStateException for undirected graphs or cyclic digraphs
     */
    public List<V> topologicalSort() {
        requireDirected("topologicalSort");

        Dictionary<V, Integer> indegrees = new Dictionary<>(vertices.size());
        Queue<Vertex<V>> ready = new Queue<>();

        for (V value : vertices) {
            indegrees.put(value, 0);
        }

        for (V sourceValue : vertices) {
            Vertex<V> source = vertices.get(sourceValue);
            for (V targetValue : source.neighbors) {
                indegrees.put(targetValue, indegrees.get(targetValue) + 1);
            }
        }

        for (V value : vertices) {
            if (indegrees.get(value) == 0) {
                ready.add(vertices.get(value));
            }
        }

        List<V> result = new List<>(vertices.size());

        while (!ready.isEmpty()) {
            Vertex<V> current = ready.remove();
            result.append(current.value);

            for (V neighborValue : current.neighbors) {
                int nextDegree = indegrees.get(neighborValue) - 1;
                indegrees.put(neighborValue, nextDegree);
                if (nextDegree == 0) {
                    ready.add(vertices.get(neighborValue));
                }
            }
        }

        if (result.size() != vertices.size()) {
            throw new IllegalStateException(
                "Topological order is undefined for a cyclic directed graph."
            );
        }

        return result;
    }

    // ============================================================
    // Copy / iteration / streams
    // ============================================================

    /** Returns a shallow independent copy preserving vertex and edge order. */
    public Graph<V> copy() {
        Graph<V> result = new Graph<>(directed);

        for (V vertex : vertices) {
            result.addVertex(vertex);
        }

        for (Tuple edge : edgeList()) {
            @SuppressWarnings("unchecked")
            V from = (V) edge.get(0);
            @SuppressWarnings("unchecked")
            V to = (V) edge.get(1);
            result.addEdge(from, to);
        }

        result.modCount = 0;
        return result;
    }

    @Override
    public Iterator<V> iterator() {
        Iterator<V> delegate = vertices.iterator();
        int expectedModCount = modCount;

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                checkForModification(expectedModCount);
                return delegate.hasNext();
            }

            @Override
            public V next() {
                checkForModification(expectedModCount);
                return delegate.next();
            }
        };
    }

    @Override
    public Spliterator<V> spliterator() {
        return new GraphSpliterator();
    }

    public Stream<V> stream() {
        return StreamSupport.stream(
            this::spliterator,
            graphCharacteristics(),
            false
        );
    }

    public Stream<V> parallelStream() {
        return StreamSupport.stream(
            this::spliterator,
            graphCharacteristics(),
            true
        );
    }

    @Override
    public String toString() {
        return PythonRepr.container(this, "Graph(...)", () -> {
            StringBuilder result = new StringBuilder("Graph(directed=");
            result.append(directed);
            result.append(", vertices=[");

            int vertexIndex = 0;
            for (V value : vertices) {
                if (vertexIndex++ > 0) {
                    result.append(", ");
                }
                result.append(PythonRepr.format(value));
            }

            result.append("], edges=[");
            List<Tuple> edges = edgeList();
            for (int index = 0; index < edges.size(); index++) {
                if (index > 0) {
                    result.append(", ");
                }
                result.append(PythonRepr.format(edges.get(index)));
            }

            return result.append("])").toString();
        });
    }

    // ============================================================
    // Cycle helpers
    // ============================================================

    private boolean hasDirectedCycle() {
        Dictionary<V, Integer> colors = new Dictionary<>(vertices.size());

        for (V value : vertices) {
            colors.put(value, 0);
        }

        for (V rootValue : vertices) {
            if (colors.get(rootValue) != 0) {
                continue;
            }

            Vertex<V> root = vertices.get(rootValue);
            Stack<TraversalFrame<V>> stack = new Stack<>();
            colors.put(root.value, 1);
            stack.push(new TraversalFrame<>(root, null));

            while (!stack.isEmpty()) {
                TraversalFrame<V> frame = stack.peek();

                if (!frame.neighbors.hasNext()) {
                    colors.put(frame.vertex.value, 2);
                    stack.pop();
                    continue;
                }

                V neighborValue = frame.neighbors.next();
                int color = colors.get(neighborValue);

                if (color == 1) {
                    return true;
                }

                if (color == 0) {
                    Vertex<V> neighbor = vertices.get(neighborValue);
                    colors.put(neighbor.value, 1);
                    stack.push(new TraversalFrame<>(neighbor, null));
                }
            }
        }

        return false;
    }

    private boolean hasUndirectedCycle() {
        Dictionary<V, Boolean> visited = new Dictionary<>();

        for (V rootValue : vertices) {
            if (visited.containsKey(rootValue)) {
                continue;
            }

            Vertex<V> root = vertices.get(rootValue);
            Stack<TraversalFrame<V>> stack = new Stack<>();
            visited.put(root.value, Boolean.TRUE);
            stack.push(new TraversalFrame<>(root, null));

            while (!stack.isEmpty()) {
                TraversalFrame<V> frame = stack.peek();

                if (!frame.neighbors.hasNext()) {
                    stack.pop();
                    continue;
                }

                V neighborValue = frame.neighbors.next();

                if (!visited.containsKey(neighborValue)) {
                    Vertex<V> neighbor = vertices.get(neighborValue);
                    visited.put(neighbor.value, Boolean.TRUE);
                    stack.push(new TraversalFrame<>(
                        neighbor,
                        frame.vertex.value
                    ));
                } else if (!Objects.equals(neighborValue, frame.parent)) {
                    return true;
                }
            }
        }

        return false;
    }

    // ============================================================
    // Private helpers
    // ============================================================

    private Vertex<V> requireStoredVertex(V value) {
        requireVertexValue(value);
        Vertex<V> result = vertices.get(value, null);
        if (result == null) {
            throw new NoSuchElementException(
                "Vertex not found in graph: " + PythonRepr.format(value)
            );
        }
        return result;
    }

    private void requireVertexValue(V value) {
        Objects.requireNonNull(value, "vertex");
    }

    private void requireDirected(String operation) {
        if (!directed) {
            throw new IllegalStateException(
                operation + " requires a directed graph."
            );
        }
    }

    private void requireUndirected(String operation) {
        if (directed) {
            throw new IllegalStateException(
                operation + " requires an undirected graph."
            );
        }
    }

    private void checkForModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException(
                "Graph was structurally modified during traversal."
            );
        }
    }

    private int graphCharacteristics() {
        return Spliterator.ORDERED
            | Spliterator.DISTINCT
            | Spliterator.SIZED
            | Spliterator.SUBSIZED
            | Spliterator.NONNULL;
    }

    private final class GraphSpliterator implements Spliterator<V> {
        private Iterator<V> iterator;
        private int remaining = -1;
        private int expectedModCount;

        private int bind() {
            if (remaining < 0) {
                iterator = Graph.this.iterator();
                remaining = vertices.size();
                expectedModCount = modCount;
            }
            return remaining;
        }

        @Override
        public Spliterator<V> trySplit() {
            int currentRemaining = bind();
            checkForModification(expectedModCount);

            int batchSize = currentRemaining >>> 1;
            if (batchSize == 0) {
                return null;
            }

            ArrayList<V> prefix = new ArrayList<>(batchSize);
            for (int index = 0; index < batchSize; index++) {
                prefix.add(iterator.next());
            }

            remaining -= batchSize;
            checkForModification(expectedModCount);

            return Spliterators.spliterator(
                prefix.iterator(),
                prefix.size(),
                graphCharacteristics()
            );
        }

        @Override
        public boolean tryAdvance(Consumer<? super V> action) {
            Objects.requireNonNull(action, "action");
            bind();
            checkForModification(expectedModCount);

            if (remaining == 0) {
                return false;
            }

            V value = iterator.next();
            remaining--;
            action.accept(value);
            checkForModification(expectedModCount);
            return true;
        }

        @Override
        public void forEachRemaining(Consumer<? super V> action) {
            Objects.requireNonNull(action, "action");
            bind();
            checkForModification(expectedModCount);

            while (remaining > 0) {
                V value = iterator.next();
                remaining--;
                action.accept(value);
                checkForModification(expectedModCount);
            }
        }

        @Override
        public long estimateSize() {
            int currentRemaining = bind();
            checkForModification(expectedModCount);
            return currentRemaining;
        }

        @Override
        public int characteristics() {
            return graphCharacteristics();
        }
    }

    private static final class Vertex<E> {
        private final E value;
        private final Dictionary<E, Boolean> neighbors = new Dictionary<>();

        private Vertex(E value) {
            this.value = value;
        }
    }

    private static final class TraversalFrame<E> {
        private final Vertex<E> vertex;
        private final E parent;
        private final Iterator<E> neighbors;

        private TraversalFrame(Vertex<E> vertex, E parent) {
            this.vertex = vertex;
            this.parent = parent;
            this.neighbors = vertex.neighbors.iterator();
        }
    }
}
