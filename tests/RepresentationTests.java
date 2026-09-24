package DataStructures;

import static DataStructures.TestSupport.*;

public final class RepresentationTests {
    private RepresentationTests() { }

    public static void run() {
        equal("['hello', None, True, '\\n', '\\x00', '\\x7f']",
            List.of("hello", null, true, '\n', '\0', (char) 0x7f).toString(), "list repr");
        equal("('it\\'s\\\\ok',)", Tuple.of("it's\\ok").toString(), "quotes and singleton comma");
        equal("()", Tuple.EMPTY.toString(), "empty tuple");
        equal("set()", new Set<>().toString(), "empty set");
        equal("{None}", Set.of((Object) null).toString(), "null set element");

        List<Object> self = new List<>();
        self.append(self);
        equal("[[...]]", self.toString(), "self-referencing list");
        self.clear();
        Tuple tuple = Tuple.of(self);
        self.append(tuple);
        equal("([(...)],)", tuple.toString(), "tuple/list cycle");

        Dictionary<String, Object> dictionary = new Dictionary<>();
        dictionary.put("self", dictionary);
        equal("{'self': {...}}", dictionary.toString(), "self-referencing dictionary");
        dictionary.clear();
        Object view = dictionary.values();
        dictionary.put("view", view);
        equal("dict_values([dict_values([...])])", view.toString(), "recursive values view");
        dictionary.clear();
        Object items = dictionary.items();
        dictionary.put("items", items);
        equal("dict_items([('items', dict_items([...]))])", items.toString(), "recursive items view");

        List<Integer> shared = List.of(1);
        equal("[[1], [1]]", List.of(shared, shared).toString(), "shared objects are not cycles");

        List<Object> failing = List.of(new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("intentional formatting failure");
            }
        });
        throwsType(IllegalStateException.class, failing::toString);
        failing.set(0, "recovered");
        equal("['recovered']", failing.toString(), "guard cleaned after exception");

        equal("LinkedList([])", new LinkedList<>().toString(), "empty linked list");
        LinkedList<Object> linked = new LinkedList<>();
        linked.append(linked);
        equal("LinkedList([LinkedList([...])])", linked.toString(),
            "self-referencing linked list");

        equal("Stack([])", new Stack<>().toString(), "empty stack");
        Stack<Object> stack = new Stack<>();
        stack.push(stack);
        equal("Stack([Stack([...])])", stack.toString(), "self-referencing stack");

        equal("Queue([])", new Queue<>().toString(), "empty queue");
        Queue<Object> queue = new Queue<>();
        queue.add(queue);
        equal("Queue([Queue([...])])", queue.toString(), "self-referencing queue");

        equal("ArrayDeque([])", new ArrayDeque<>().toString(), "empty deque");
        equal("ArrayDeque(['first', True])", ArrayDeque.of("first", true).toString(), "deque repr");
        ArrayDeque<Object> deque = new ArrayDeque<>();
        deque.addLast(deque);
        equal("ArrayDeque([ArrayDeque([...])])", deque.toString(), "self-referencing deque");

        equal("Graph(directed=false, vertices=[], edges=[])",
            new Graph<>().toString(), "empty graph");
        Graph<Object> graph = new Graph<>();
        graph.addVertex(graph);
        equal("Graph(directed=false, vertices=[Graph(...)], edges=[])",
            graph.toString(), "self-referencing graph");

        equal("DisjointSet([])", new DisjointSet<>().toString(),
            "empty disjoint set");
        DisjointSet<Object> disjoint = new DisjointSet<>();
        disjoint.add(disjoint);
        equal("DisjointSet([[DisjointSet(...)]])", disjoint.toString(),
            "self-referencing disjoint set");

        equal("Trie([])", new Trie().toString(), "empty trie");
        equal("Trie(['car', 'cart', 'cat'])",
            Trie.of("car", "cart", "cat").toString(), "trie repr");

        equal("AVLTree([])", new AVLTree<>().toString(), "empty AVL tree");
        AVLTree<Object> avl = new AVLTree<>((left, right) -> 0);
        avl.add(avl);
        equal("AVLTree([AVLTree([...])])", avl.toString(),
            "self-referencing AVL tree");

        equal("BinarySearchTree([])", new BinarySearchTree<>().toString(), "empty BST");
        BinarySearchTree<Object> tree = new BinarySearchTree<>((left, right) -> 0);
        tree.add(tree);
        equal("BinarySearchTree([BinarySearchTree([...])])", tree.toString(),
            "self-referencing BST");

        equal("BinaryHeap([])", new BinaryHeap<>().toString(), "empty heap");
        BinaryHeap<Object> heap = new BinaryHeap<>((left, right) -> 0);
        heap.add(heap);
        equal("BinaryHeap([BinaryHeap([...])])", heap.toString(), "self-referencing heap");

        deque.clear();
        heap.clear();
        deque.addLast(heap);
        heap.add(deque);
        equal("ArrayDeque([BinaryHeap([ArrayDeque([...])])])", deque.toString(), "deque/heap cycle");

        // These collections deliberately retain identity equality and hashing,
        // so mutation does not change their identity when used as keys.
        Dictionary<Object, String> identityKeys = new Dictionary<>();
        identityKeys.put(stack, "stack");
        identityKeys.put(queue, "queue");
        identityKeys.put(deque, "deque");
        identityKeys.put(heap, "heap");
        BinarySearchTree<Integer> treeKey = BinarySearchTree.of(2, 1, 3);
        identityKeys.put(treeKey, "tree");
        AVLTree<Integer> avlKey = AVLTree.of(2, 1, 3);
        identityKeys.put(avlKey, "avl");
        Graph<Integer> graphKey = new Graph<>();
        graphKey.addVertex(1);
        identityKeys.put(graphKey, "graph");
        DisjointSet<Integer> disjointKey = DisjointSet.of(1, 2);
        identityKeys.put(disjointKey, "disjoint");
        Trie trieKey = Trie.of("a");
        identityKeys.put(trieKey, "trie");
        stack.push("changed");
        queue.add("changed");
        deque.addLast("changed");
        heap.add("changed");
        treeKey.add(4);
        avlKey.add(4);
        graphKey.addVertex(2);
        disjointKey.union(1, 2);
        trieKey.add("ab");
        equal("stack", identityKeys.get(stack), "stack identity key survives mutation");
        equal("queue", identityKeys.get(queue), "queue identity key survives mutation");
        equal("deque", identityKeys.get(deque), "deque identity key survives mutation");
        equal("heap", identityKeys.get(heap), "heap identity key survives mutation");
        equal("tree", identityKeys.get(treeKey), "BST identity key survives mutation");
        equal("avl", identityKeys.get(avlKey), "AVL identity key survives mutation");
        equal("graph", identityKeys.get(graphKey), "Graph identity key survives mutation");
        equal("disjoint", identityKeys.get(disjointKey),
            "DisjointSet identity key survives mutation");
        equal("trie", identityKeys.get(trieKey),
            "Trie identity key survives mutation");
    }
}
