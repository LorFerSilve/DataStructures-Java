package DataStructures;

/** Run with java -cp build/classes DataStructures.AllTests. */
public final class AllTests {
    private AllTests() { }

    public static void main(String[] args) {
        run("List", ListTests::run);
        run("LinkedList", LinkedListTests::run);
        run("Tuple", TupleTests::run);
        run("Set", SetTests::run);
        run("Dictionary", DictionaryTests::run);
        run("Stack", StackTests::run);
        run("Queue", QueueTests::run);
        run("ArrayDeque", ArrayDequeTests::run);
        run("BinaryHeap", BinaryHeapTests::run);
        run("BinarySearchTree", BinarySearchTreeTests::run);
        run("AVLTree", AVLTreeTests::run);
        run("Graph", GraphTests::run);
        run("DisjointSet", DisjointSetTests::run);
        run("Representation", RepresentationTests::run);
        System.out.println("All 14 test suites passed.");
    }

    private static void run(String name, Runnable suite) {
        suite.run();
        System.out.println("PASS " + name);
    }
}
