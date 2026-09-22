package DataStructures;

/** Small executable examples of the public API. */
public final class Examples {
    private Examples() { }

    public static void main(String[] args) {
        List<Integer> numbers = List.of(0, 1, 2, 3, 4, 5);
        System.out.println("Reversed slice: " + numbers.sliceAll(-1));
        numbers.setSlice(0, numbers.size(), 2, List.of(10, 20, 30));
        System.out.println("Replace every second value: " + numbers);
        numbers.removeSlice(1, numbers.size(), 2);
        System.out.println("Remove every second value: " + numbers);

        List<String> words = List.of("pear", "fig", "apple", "plum");
        words.sort(String::length);
        System.out.println("Stable sort by length: " + words);

        LinkedList<String> route = LinkedList.of("A", "B", "C");
        route.addFirst("start");
        route.addLast("finish");
        route.removeAt(2);
        System.out.println("Linked route: " + route);

        Tuple point = Tuple.of("origin", 0, 0);
        System.out.println("Tuple: " + point + ", label: " + point.get(0, String.class));

        Set<String> backend = Set.of("Java", "Python");
        Set<String> scripting = Set.of("Python", "JavaScript");
        System.out.println("Shared languages: " + backend.intersection(scripting));

        Dictionary<String, Integer> inventory = new Dictionary<>();
        inventory.updateEntries(java.util.List.of(java.util.Map.entry("apples", 3)));
        Dictionary<String, Integer>.KeyView liveKeys = inventory.keys();
        inventory.put("pears", 5);
        System.out.println("Live keys: " + liveKeys);
        System.out.println("Last inserted item: " + inventory.popItem());
        System.out.println("Inventory: " + inventory);

        Stack<String> history = Stack.of("open", "edit");
        history.push("save");
        System.out.println("Latest stack entry: " + history.peek());
        System.out.println("Undo stack entry: " + history.pop());

        Queue<String> jobs = Queue.of("compile", "test");
        jobs.offer("package");
        System.out.println("Next queued job: " + jobs.remove());
        System.out.println("Remaining jobs: " + jobs);

        ArrayDeque<String> deque = ArrayDeque.of("first", "second");
        deque.addLast("third");
        System.out.println("Next deque item: " + deque.removeFirst());
        deque.push("urgent");
        System.out.println("Deque stack pop: " + deque.pop());
        System.out.println("Remaining deque: " + deque);

        BinarySearchTree<Integer> searchTree =
            BinarySearchTree.of(8, 4, 12, 2, 6, 10, 14);
        System.out.println("BST in-order: " + searchTree.inOrder());
        System.out.println("BST level-order: " + searchTree.levelOrder());
        System.out.println("BST floor(9): " + searchTree.floor(9));

        AVLTree<Integer> balancedTree = new AVLTree<>();
        for (int value = 1; value <= 7; value++) {
            balancedTree.add(value);
        }
        System.out.println("AVL level-order: " + balancedTree.levelOrder());
        System.out.println("AVL height after sorted inserts: " + balancedTree.height());

        Graph<String> dependencyGraph = new Graph<>(true);
        for (String step : java.util.List.of("compile", "test", "package")) {
            dependencyGraph.addVertex(step);
        }
        dependencyGraph.addEdge("compile", "test");
        dependencyGraph.addEdge("test", "package");
        System.out.println("Graph BFS: " + dependencyGraph.breadthFirst("compile"));
        System.out.println("Graph topological order: " + dependencyGraph.topologicalSort());

        BinaryHeap<Integer> priorities = BinaryHeap.of(8, 3, 5, 1);
        System.out.println("Lowest priority value: " + priorities.poll());
        System.out.println("Remaining priorities, sorted: " + priorities.sorted());

        BinaryHeap<Integer> highestFirst = new BinaryHeap<>(
            List.of(8, 3, 5, 1), java.util.Comparator.reverseOrder()
        );
        System.out.println("Highest priority value: " + highestFirst.poll());

        BinaryHeap<String> shortestFirst = new BinaryHeap<>(
            List.of("pear", "fig", "apple"), java.util.Comparator.comparingInt(String::length)
        );
        System.out.println("Custom priority (shortest word): " + shortestFirst.poll());

        List<Object> recursive = new List<>();
        recursive.append(recursive);
        System.out.println("Recursive list: " + recursive);
    }
}
