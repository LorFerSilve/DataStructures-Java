package DataStructures;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Prefix tree for distinct, case-sensitive strings.
 *
 * <p>Each node stores outgoing UTF-16 code-unit edges in insertion order using
 * the custom {@link Dictionary}. The trie therefore has deterministic traversal
 * order: a stored prefix is emitted before its longer descendants, while sibling
 * branches follow the order in which those edges were first created.</p>
 *
 * <p>Empty strings are supported. Null strings are rejected. Prefix lookup is
 * O(p), where p is the prefix length. Each node also stores the number of terminal
 * words in its subtree, making {@link #countWithPrefix(String)} O(p).</p>
 *
 * <p>This class is not thread-safe. Iterators are fail-fast on membership
 * changes. Equality and hashing use object identity.</p>
 */
public final class Trie implements Iterable<String> {
    private static final int SPLITERATOR_CHARACTERISTICS =
        Spliterator.ORDERED
            | Spliterator.DISTINCT
            | Spliterator.SIZED
            | Spliterator.SUBSIZED
            | Spliterator.NONNULL;

    private final Node root = new Node();
    private int size;
    private int modCount;

    /** Creates an empty trie. */
    public Trie() { }

    /** Adds all words from the iterable in encounter order. */
    public Trie(Iterable<String> words) {
        Objects.requireNonNull(words, "words");
        for (String word : words) {
            add(word);
        }
        modCount = 0;
    }

    /** Creates a trie from the supplied words. */
    public static Trie of(String... words) {
        Objects.requireNonNull(words, "words");
        Trie result = new Trie();
        for (String word : words) {
            result.add(word);
        }
        result.modCount = 0;
        return result;
    }

    /**
     * Adds one word.
     *
     * @return false if the word was already stored
     */
    public boolean add(String word) {
        requireText(word, "word");

        ArrayList<Node> path = new ArrayList<>(word.length() + 1);
        Node current = root;
        path.add(current);

        for (int index = 0; index < word.length(); index++) {
            char edge = word.charAt(index);
            Node next = current.children.get(edge, null);
            if (next == null) {
                next = new Node();
                current.children.put(edge, next);
            }
            current = next;
            path.add(current);
        }

        if (current.terminal) {
            return false;
        }

        current.terminal = true;
        for (Node node : path) {
            node.subtreeWords++;
        }

        size++;
        modCount++;
        return true;
    }

    /** Returns whether the exact word is stored. */
    public boolean contains(String word) {
        requireText(word, "word");
        Node node = findNode(word);
        return node != null && node.terminal;
    }

    /**
     * Returns whether at least one stored word starts with prefix.
     *
     * <p>The empty prefix matches whenever the trie is non-empty.</p>
     */
    public boolean startsWith(String prefix) {
        requireText(prefix, "prefix");
        Node node = findNode(prefix);
        return node != null && node.subtreeWords > 0;
    }

    /**
     * Returns the number of stored words beginning with prefix.
     */
    public int countWithPrefix(String prefix) {
        requireText(prefix, "prefix");
        Node node = findNode(prefix);
        return node == null ? 0 : node.subtreeWords;
    }

    /**
     * Removes one exact word.
     *
     * @return false when the word is absent
     */
    public boolean remove(String word) {
        requireText(word, "word");

        ArrayList<Node> path = new ArrayList<>(word.length() + 1);
        Node current = root;
        path.add(current);

        for (int index = 0; index < word.length(); index++) {
            current = current.children.get(word.charAt(index), null);
            if (current == null) {
                return false;
            }
            path.add(current);
        }

        if (!current.terminal) {
            return false;
        }

        current.terminal = false;
        for (Node node : path) {
            node.subtreeWords--;
        }

        for (int index = word.length() - 1; index >= 0; index--) {
            Node child = path.get(index + 1);
            if (child.subtreeWords != 0) {
                break;
            }
            path.get(index).children.remove(word.charAt(index));
        }

        size--;
        modCount++;
        return true;
    }

    /**
     * Removes all words beginning with prefix.
     *
     * @return number of removed words
     */
    public int removePrefix(String prefix) {
        requireText(prefix, "prefix");

        if (prefix.isEmpty()) {
            int removed = size;
            clear();
            return removed;
        }

        ArrayList<Node> path = new ArrayList<>(prefix.length() + 1);
        Node current = root;
        path.add(current);

        for (int index = 0; index < prefix.length(); index++) {
            current = current.children.get(prefix.charAt(index), null);
            if (current == null) {
                return 0;
            }
            path.add(current);
        }

        int removed = current.subtreeWords;
        if (removed == 0) {
            return 0;
        }

        for (int index = 0; index < path.size() - 1; index++) {
            path.get(index).subtreeWords -= removed;
        }

        Node parent = path.get(path.size() - 2);
        parent.children.remove(prefix.charAt(prefix.length() - 1));

        for (int index = prefix.length() - 2; index >= 0; index--) {
            Node child = path.get(index + 1);
            if (child.subtreeWords != 0) {
                break;
            }
            path.get(index).children.remove(prefix.charAt(index));
        }

        size -= removed;
        modCount++;
        return removed;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Removes all stored words. */
    public void clear() {
        if (size == 0) {
            return;
        }

        root.children.clear();
        root.terminal = false;
        root.subtreeWords = 0;
        size = 0;
        modCount++;
    }

    /** Returns all stored words in deterministic trie traversal order. */
    public List<String> words() {
        return collectWords(root, "");
    }

    /** Returns all stored words beginning with prefix. */
    public List<String> wordsWithPrefix(String prefix) {
        requireText(prefix, "prefix");
        Node node = findNode(prefix);
        if (node == null || node.subtreeWords == 0) {
            return new List<>();
        }
        return collectWords(node, prefix);
    }

    /**
     * Returns every stored word that is a prefix of text, shortest first.
     */
    public List<String> prefixesOf(String text) {
        requireText(text, "text");
        List<String> result = new List<>();
        Node current = root;

        if (current.terminal) {
            result.append("");
        }

        for (int index = 0; index < text.length(); index++) {
            current = current.children.get(text.charAt(index), null);
            if (current == null) {
                break;
            }
            if (current.terminal) {
                result.append(text.substring(0, index + 1));
            }
        }

        return result;
    }

    /**
     * Returns the longest stored word that is a prefix of text, or null when
     * no stored word is a prefix.
     */
    public String longestPrefixOf(String text) {
        requireText(text, "text");
        Node current = root;
        int longestLength = current.terminal ? 0 : -1;

        for (int index = 0; index < text.length(); index++) {
            current = current.children.get(text.charAt(index), null);
            if (current == null) {
                break;
            }
            if (current.terminal) {
                longestLength = index + 1;
            }
        }

        return longestLength < 0 ? null : text.substring(0, longestLength);
    }

    /** Returns an independent copy preserving traversal order. */
    public Trie copy() {
        Trie result = new Trie();
        for (String word : words()) {
            result.add(word);
        }
        result.modCount = 0;
        return result;
    }

    @Override
    public Iterator<String> iterator() {
        Iterator<String> snapshot = words().iterator();
        int expectedModCount = modCount;

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                checkForModification(expectedModCount);
                return snapshot.hasNext();
            }

            @Override
            public String next() {
                checkForModification(expectedModCount);
                return snapshot.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Spliterator<String> spliterator() {
        return Spliterators.spliterator(
            iterator(),
            size,
            SPLITERATOR_CHARACTERISTICS
        );
    }

    public Stream<String> stream() {
        return StreamSupport.stream(
            this::spliterator,
            SPLITERATOR_CHARACTERISTICS,
            false
        );
    }

    public Stream<String> parallelStream() {
        return StreamSupport.stream(
            this::spliterator,
            SPLITERATOR_CHARACTERISTICS,
            true
        );
    }

    @Override
    public String toString() {
        return "Trie(" + words() + ")";
    }

    private Node findNode(String text) {
        Node current = root;

        for (int index = 0; index < text.length(); index++) {
            current = current.children.get(text.charAt(index), null);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private List<String> collectWords(Node start, String prefix) {
        List<String> result = new List<>(start.subtreeWords);
        StringBuilder current = new StringBuilder(prefix);

        if (start.terminal) {
            result.append(prefix);
        }

        java.util.ArrayDeque<TraversalFrame> stack = new java.util.ArrayDeque<>();
        stack.push(new TraversalFrame(start, prefix.length()));

        while (!stack.isEmpty()) {
            TraversalFrame frame = stack.peek();

            if (!frame.children.hasNext()) {
                current.setLength(frame.prefixLength);
                stack.pop();
                continue;
            }

            char edge = frame.children.next();
            Node child = frame.node.children.get(edge);

            current.setLength(frame.prefixLength);
            current.append(edge);

            int childLength = current.length();
            if (child.terminal) {
                result.append(current.toString());
            }

            stack.push(new TraversalFrame(child, childLength));
        }

        return result;
    }

    private void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
    }

    private void checkForModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    private static final class Node {
        private final Dictionary<Character, Node> children = new Dictionary<>();
        private boolean terminal;
        private int subtreeWords;
    }

    private static final class TraversalFrame {
        private final Node node;
        private final Iterator<Character> children;
        private final int prefixLength;

        private TraversalFrame(Node node, int prefixLength) {
            this.node = node;
            this.children = node.children.iterator();
            this.prefixLength = prefixLength;
        }
    }
}
