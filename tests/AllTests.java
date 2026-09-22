package DataStructures;

/** Run with java -cp build/classes DataStructures.AllTests. */
public final class AllTests {
    private AllTests() { }

    public static void main(String[] args) {
        run("List", ListTests::run);
        run("Tuple", TupleTests::run);
        run("Set", SetTests::run);
        run("Dictionary", DictionaryTests::run);
        run("Stack", StackTests::run);
        run("Queue", QueueTests::run);
        run("ArrayDeque", ArrayDequeTests::run);
        run("BinaryHeap", BinaryHeapTests::run);
        run("Representation", RepresentationTests::run);
        System.out.println("All 9 test suites passed.");
    }

    private static void run(String name, Runnable suite) {
        suite.run();
        System.out.println("PASS " + name);
    }
}
