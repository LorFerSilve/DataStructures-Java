package DataStructures;

import static DataStructures.TestSupport.*;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Spliterator;

public final class RadixTreeTests {
    private RadixTreeTests() { }

    public static void run() {
        constructionAndMembership();
        compressedSplitsAndOrdering();
        prefixQueries();
        removalAndRecompression();
        copyIterationAndStreams();
        longWordsStayCompressed();
        randomizedAgainstReference();
    }

    private static void constructionAndMembership() {
        RadixTree empty = new RadixTree();
        equal(0, empty.size(), "empty size");
        equal(0, empty.nodeCount(), "empty node count");
        check(empty.isEmpty(), "empty state");
        check(!empty.startsWith(""), "empty prefix on empty tree");

        RadixTree tree = RadixTree.of("car", "cat", "car", "", "dog");
        equal(4, tree.size(), "factory distinct size");
        check(tree.contains("car"), "contains car");
        check(tree.contains(""), "contains empty word");
        check(!tree.contains("ca"), "compressed prefix not exact word");
        check(tree.startsWith("ca"), "prefix exists");
        equal(2, tree.countWithPrefix("ca"), "prefix count");
        equal(4, tree.countWithPrefix(""), "root count");

        check(tree.add("cart"), "add longer word");
        check(!tree.add("cart"), "duplicate add");
        equal(5, tree.size(), "size after add");

        throwsType(NullPointerException.class, () -> tree.add(null));
        throwsType(NullPointerException.class, () -> tree.contains(null));
        throwsType(NullPointerException.class, () -> tree.startsWith(null));
        throwsType(NullPointerException.class, () -> tree.countWithPrefix(null));
    }

    private static void compressedSplitsAndOrdering() {
        RadixTree tree = new RadixTree();

        tree.add("compression");
        equal(1, tree.nodeCount(), "single word uses one compressed node");

        tree.add("compress");
        equal(2, tree.nodeCount(), "terminal split creates two nodes");

        tree.add("company");
        equal(4, tree.nodeCount(), "branch split creates compressed branches");

        tree.add("dog");
        equal(5, tree.nodeCount(), "independent branch adds one node");

        equal(
            List.of("compress", "compression", "company", "dog"),
            tree.words(),
            "deterministic prefix-first radix traversal"
        );

        RadixTree insertionOrder = new RadixTree();
        insertionOrder.add("dog");
        insertionOrder.add("car");
        insertionOrder.add("cat");
        insertionOrder.add("cart");

        equal(
            List.of("dog", "car", "cart", "cat"),
            insertionOrder.words(),
            "root and sibling branch insertion order"
        );
    }

    private static void prefixQueries() {
        RadixTree tree = RadixTree.of(
            "", "compression", "compress", "company", "compact", "dog"
        );

        check(tree.startsWith("comp"), "prefix ends inside compressed edge");
        equal(4, tree.countWithPrefix("comp"),
            "count inside compressed edge");
        equal(
            new HashSet<>(java.util.List.of(
                "compression", "compress", "company", "compact"
            )),
            new HashSet<>(asJavaList(tree.wordsWithPrefix("comp"))),
            "wordsWithPrefix inside compressed edge"
        );

        equal(List.of("", "compress"), tree.prefixesOf("compressor"),
            "stored prefixes of text");
        equal("compress", tree.longestPrefixOf("compressor"),
            "longest prefix");
        equal("", tree.longestPrefixOf("zzz"),
            "empty word remains valid longest prefix");

        RadixTree noEmpty = RadixTree.of("compression", "compress");
        equal(null, noEmpty.longestPrefixOf("company"),
            "partial edge mismatch is not a word prefix");
        equal(List.of(), noEmpty.prefixesOf("company"),
            "partial edge mismatch yields no prefixes");
        equal(0, noEmpty.countWithPrefix("company"),
            "mismatching compressed branch count");
    }

    private static void removalAndRecompression() {
        RadixTree tree = RadixTree.of(
            "", "car", "cart", "carbon", "cat", "dog"
        );

        int before = tree.nodeCount();
        check(tree.remove("cart"), "remove exact word");
        check(!tree.contains("cart"), "removed word absent");
        check(tree.contains("car"), "prefix word retained");
        check(tree.nodeCount() < before,
            "dead unary path recompressed after removal");
        equal(2, tree.countWithPrefix("car"),
            "prefix count after exact removal");

        check(!tree.remove("cart"), "remove absent false");
        check(!tree.remove("ca"), "remove non-terminal prefix false");

        equal(2, tree.removePrefix("car"), "remove aligned prefix subtree");
        check(!tree.startsWith("car"), "car subtree gone");
        check(tree.contains("cat"), "sibling branch retained");

        RadixTree middle = RadixTree.of("compression", "compressive", "company");
        equal(2, middle.removePrefix("compr"),
            "prefix ending inside compressed edge removes subtree");
        check(!middle.contains("compression"), "inside-edge prefix removal first");
        check(!middle.contains("compressive"), "inside-edge prefix removal second");
        check(middle.contains("company"), "other split branch retained");
        equal(1, middle.nodeCount(),
            "remaining branch recompressed to one node");

        check(tree.remove(""), "remove empty word");
        equal(2, tree.size(), "size after empty removal");

        equal(2, tree.removePrefix(""), "empty prefix clears all");
        check(tree.isEmpty(), "empty-prefix removal clears tree");
        equal(0, tree.nodeCount(), "clear removes all radix nodes");
        equal(0, tree.removePrefix(""), "clearing empty tree removes zero");
    }

    private static void copyIterationAndStreams() {
        RadixTree tree = RadixTree.of("car", "cart", "cat", "dog");

        RadixTree copy = tree.copy();
        check(copy != tree, "copy identity");
        equal(tree.words(), copy.words(), "copy words");
        equal(tree.nodeCount(), copy.nodeCount(), "copy compressed node count");

        copy.add("apple");
        check(!tree.contains("apple"), "copy mutation independent");
        copy.removePrefix("car");
        check(tree.contains("car"), "copy removal independent");

        Iterator<String> iterator = tree.iterator();
        equal("car", iterator.next(), "iterator first word");
        tree.add("cab");
        throwsType(ConcurrentModificationException.class, iterator::hasNext);

        Iterator<String> cannotRemove = tree.iterator();
        cannotRemove.next();
        throwsType(UnsupportedOperationException.class, cannotRemove::remove);

        var stream = tree.stream();
        tree.add("zebra");
        equal(asJavaList(tree.words()), stream.toList(),
            "stream late binding");
        equal((long) tree.size(), tree.parallelStream().count(),
            "parallel stream count");

        Spliterator<String> split = tree.spliterator();
        int characteristics = Spliterator.ORDERED | Spliterator.DISTINCT
            | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL;
        check(split.hasCharacteristics(characteristics),
            "spliterator characteristics");
        equal((long) tree.size(), split.getExactSizeIfKnown(),
            "spliterator exact size");

        tree.clear();
        check(tree.isEmpty(), "clear empties tree");
        equal(List.of(), tree.words(), "words after clear");
        tree.clear();
    }

    private static void longWordsStayCompressed() {
        String word = "a".repeat(50_000);
        String sibling = "a".repeat(49_999) + "b";

        RadixTree tree = RadixTree.of(word);
        equal(1, tree.nodeCount(), "long word stored as one edge");
        equal(List.of(word), tree.words(), "long compressed word traversal");

        tree.add(sibling);
        equal(3, tree.nodeCount(),
            "long shared prefix represented by split plus two suffixes");
        equal(2, tree.countWithPrefix("a".repeat(40_000)),
            "long mid-edge prefix count");

        check(tree.remove(sibling), "remove long sibling");
        equal(1, tree.nodeCount(), "long path recompressed after removal");
        equal(word, tree.longestPrefixOf(word + "x"),
            "long compressed longest prefix");
    }

    private static void randomizedAgainstReference() {
        RadixTree actual = new RadixTree();
        LinkedHashSet<String> expected = new LinkedHashSet<>();
        Random random = new Random(0xBAD1A5L);

        for (int step = 0; step < 30_000; step++) {
            String word = randomWord(random);
            String prefix = randomPrefix(random);

            switch (random.nextInt(8)) {
                case 0, 1 ->
                    equal(expected.add(word), actual.add(word), "random add");
                case 2 ->
                    equal(expected.remove(word), actual.remove(word),
                        "random remove");
                case 3 ->
                    equal(expected.contains(word), actual.contains(word),
                        "random contains");
                case 4 -> {
                    int count = 0;
                    for (String value : expected) {
                        if (value.startsWith(prefix)) {
                            count++;
                        }
                    }
                    equal(count, actual.countWithPrefix(prefix),
                        "random prefix count");
                    equal(count > 0, actual.startsWith(prefix),
                        "random startsWith");
                }
                case 5 -> {
                    int removed = 0;
                    Iterator<String> iterator = expected.iterator();
                    while (iterator.hasNext()) {
                        if (iterator.next().startsWith(prefix)) {
                            iterator.remove();
                            removed++;
                        }
                    }
                    equal(removed, actual.removePrefix(prefix),
                        "random removePrefix");
                }
                case 6 -> {
                    HashSet<String> matches = new HashSet<>();
                    for (String value : expected) {
                        if (value.startsWith(prefix)) {
                            matches.add(value);
                        }
                    }
                    equal(matches, new HashSet<>(asJavaList(
                        actual.wordsWithPrefix(prefix))),
                        "random prefix words");
                }
                case 7 -> {
                    String text = word + randomWord(random);
                    java.util.List<String> prefixes = new ArrayList<>();
                    for (String value : expected) {
                        if (text.startsWith(value)) {
                            prefixes.add(value);
                        }
                    }
                    prefixes.sort(java.util.Comparator.comparingInt(String::length));

                    equal(prefixes, asJavaList(actual.prefixesOf(text)),
                        "random prefixesOf");

                    String longest = prefixes.isEmpty()
                        ? null
                        : prefixes.get(prefixes.size() - 1);
                    equal(longest, actual.longestPrefixOf(text),
                        "random longestPrefixOf");
                }
                default -> throw new AssertionError("Unknown random operation");
            }

            equal(expected.size(), actual.size(), "random size");
            equal(expected.isEmpty(), actual.isEmpty(), "random empty state");
            equal(new HashSet<>(expected),
                new HashSet<>(asJavaList(actual.words())),
                "random word membership");

            if (step % 500 == 0) {
                check(actual.nodeCount() <= totalNonEmptyCodeUnits(expected),
                    "radix node count bounded by uncompressed trie nodes");
            }
        }
    }

    private static int totalNonEmptyCodeUnits(Iterable<String> words) {
        int total = 0;
        for (String word : words) {
            total += word.length();
        }
        return total;
    }

    private static String randomWord(Random random) {
        int length = random.nextInt(9);
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append((char) ('a' + random.nextInt(6)));
        }
        return result.toString();
    }

    private static String randomPrefix(Random random) {
        String word = randomWord(random);
        return word.substring(0, random.nextInt(word.length() + 1));
    }

    private static <T> java.util.List<T> asJavaList(Iterable<T> values) {
        ArrayList<T> result = new ArrayList<>();
        for (T value : values) {
            result.add(value);
        }
        return result;
    }
}
