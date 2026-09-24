package DataStructures;

import static DataStructures.TestSupport.*;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Spliterator;

public final class TrieTests {
    private TrieTests() { }

    public static void run() {
        constructionAndMembership();
        prefixQueriesAndOrdering();
        removalAndPruning();
        copyIterationAndStreams();
        longWordDoesNotRequireRecursiveTraversal();
        randomizedAgainstReference();
    }

    private static void constructionAndMembership() {
        Trie empty = new Trie();
        equal(0, empty.size(), "empty size");
        check(empty.isEmpty(), "empty state");
        check(!empty.startsWith(""), "empty prefix on empty trie");
        equal(0, empty.countWithPrefix(""), "empty prefix count");

        Trie trie = Trie.of("car", "cat", "car", "", "dog");
        equal(4, trie.size(), "factory distinct size");
        check(trie.contains("car"), "contains car");
        check(trie.contains(""), "contains empty word");
        check(!trie.contains("ca"), "prefix not exact word");
        check(trie.startsWith("ca"), "prefix exists");
        check(trie.startsWith(""), "non-empty trie matches empty prefix");
        equal(2, trie.countWithPrefix("ca"), "prefix count");
        equal(4, trie.countWithPrefix(""), "root subtree count");

        check(trie.add("cart"), "add longer word");
        check(!trie.add("cart"), "duplicate add");
        equal(5, trie.size(), "size after add");

        throwsType(NullPointerException.class, () -> trie.add(null));
        throwsType(NullPointerException.class, () -> trie.contains(null));
        throwsType(NullPointerException.class, () -> trie.startsWith(null));
        throwsType(NullPointerException.class, () -> trie.countWithPrefix(null));
    }

    private static void prefixQueriesAndOrdering() {
        Trie trie = new Trie();
        for (String word : java.util.List.of(
            "dog", "car", "cart", "cat", "do", "apple", ""
        )) {
            trie.add(word);
        }

        equal(
            List.of("", "dog", "do", "car", "cart", "cat", "apple"),
            trie.words(),
            "deterministic trie traversal"
        );
        equal(
            List.of("car", "cart"),
            trie.wordsWithPrefix("car"),
            "prefix words include exact prefix"
        );
        equal(
            List.of("dog", "do"),
            trie.wordsWithPrefix("do"),
            "prefix branch insertion order"
        );
        equal(List.of(), trie.wordsWithPrefix("missing"),
            "missing prefix words");

        equal(List.of("", "car", "cart"), trie.prefixesOf("cartwheel"),
            "stored prefixes shortest first");
        equal("cart", trie.longestPrefixOf("cartwheel"),
            "longest stored prefix");
        equal("", trie.longestPrefixOf("zzz"),
            "empty stored word is valid longest prefix");

        Trie noEmpty = Trie.of("car", "cart");
        equal(null, noEmpty.longestPrefixOf("dog"),
            "no matching stored prefix");
        equal(List.of(), noEmpty.prefixesOf("dog"),
            "no stored prefixes");
    }

    private static void removalAndPruning() {
        Trie trie = Trie.of("", "car", "cart", "carbon", "cat", "dog");

        check(trie.remove("cart"), "remove existing word");
        check(!trie.contains("cart"), "removed word absent");
        check(trie.contains("car"), "prefix word retained");
        equal(2, trie.countWithPrefix("car"),
            "subtree count updated after exact removal");
        check(!trie.remove("cart"), "remove absent false");
        check(!trie.remove("ca"), "remove non-terminal prefix false");

        equal(2, trie.removePrefix("car"), "removePrefix count");
        check(!trie.startsWith("car"), "removed prefix branch pruned");
        check(trie.contains("cat"), "sibling branch retained");
        equal(3, trie.size(), "size after prefix removal");
        equal(0, trie.removePrefix("missing"), "missing prefix removal");
        equal(0, trie.removePrefix("car"), "already removed prefix");

        check(trie.remove(""), "remove empty word");
        check(!trie.contains(""), "empty word removed");
        equal(2, trie.size(), "size after empty removal");

        equal(2, trie.removePrefix(""), "empty prefix clears all");
        check(trie.isEmpty(), "removePrefix empty clears trie");
        equal(0, trie.removePrefix(""), "clear empty trie removes zero");
    }

    private static void copyIterationAndStreams() {
        Trie trie = Trie.of("car", "cart", "cat", "dog");

        Trie copy = trie.copy();
        check(copy != trie, "copy identity");
        equal(trie.words(), copy.words(), "copy words");
        copy.add("apple");
        check(!trie.contains("apple"), "copy mutation independent");
        copy.removePrefix("car");
        check(trie.contains("car"), "copy prefix removal independent");

        Iterator<String> iterator = trie.iterator();
        equal("car", iterator.next(), "iterator first word");
        trie.add("cab");
        throwsType(ConcurrentModificationException.class, iterator::hasNext);

        Iterator<String> cannotRemove = trie.iterator();
        cannotRemove.next();
        throwsType(UnsupportedOperationException.class, cannotRemove::remove);

        var stream = trie.stream();
        trie.add("zebra");
        equal(asJavaList(trie.words()), stream.toList(),
            "stream late binding");

        equal((long) trie.size(), trie.parallelStream().count(),
            "parallel stream count");

        Spliterator<String> split = trie.spliterator();
        int characteristics = Spliterator.ORDERED | Spliterator.DISTINCT
            | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL;
        check(split.hasCharacteristics(characteristics),
            "spliterator characteristics");
        equal((long) trie.size(), split.getExactSizeIfKnown(),
            "spliterator exact size");

        trie.clear();
        check(trie.isEmpty(), "clear empties trie");
        equal(List.of(), trie.words(), "words after clear");
        trie.clear();
    }

    private static void longWordDoesNotRequireRecursiveTraversal() {
        String word = "a".repeat(20_000);
        Trie trie = Trie.of(word);

        equal(List.of(word), trie.words(), "long word traversal");
        equal(List.of(word), trie.wordsWithPrefix("a"),
            "long word prefix traversal");
        equal(word, trie.longestPrefixOf(word + "b"),
            "long word longest prefix");
        check(trie.remove(word), "remove long word");
        check(trie.isEmpty(), "long word removal prunes trie");
    }

    private static void randomizedAgainstReference() {
        Trie actual = new Trie();
        LinkedHashSet<String> expected = new LinkedHashSet<>();
        Random random = new Random(0x7A1E5L);

        for (int step = 0; step < 25_000; step++) {
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
                "random word membership snapshot");

            if (step % 500 == 0) {
                for (String value : expected) {
                    check(actual.contains(value),
                        "all reference words must be present");
                }
            }
        }
    }

    private static String randomWord(Random random) {
        int length = random.nextInt(7);
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append((char) ('a' + random.nextInt(5)));
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
