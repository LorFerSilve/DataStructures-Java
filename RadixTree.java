package DataStructures;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Path-compressed prefix tree for distinct, case-sensitive strings.
 *
 * <p>Unlike {@link Trie}, one edge may store multiple UTF-16 code units. Chains
 * of non-branching, non-terminal trie nodes are therefore compressed into one
 * radix edge. Sibling edges are indexed by their first code unit using the
 * custom {@link Dictionary}, preserving deterministic insertion order.</p>
 *
 * <p>Empty strings are supported and null strings are rejected. Each node stores
 * the number of terminal words in its subtree, so exact lookup, prefix lookup
 * and prefix counting inspect only the compressed path.</p>
 *
 * <p>This class is not thread-safe. Iterators are fail-fast on membership
 * changes. Equality and hashing use object identity.</p>
 */
public final class RadixTree implements Iterable<String> {
    private static final int SPLITERATOR_CHARACTERISTICS =
        Spliterator.ORDERED
            | Spliterator.DISTINCT
            | Spliterator.SIZED
            | Spliterator.SUBSIZED
            | Spliterator.NONNULL;

    private final Node root = new Node("");
    private int size;
    private int modCount;

    /** Creates an empty radix tree. */
    public RadixTree() { }

    /** Adds every word from the iterable in encounter order. */
    public RadixTree(Iterable<String> words) {
        Objects.requireNonNull(words, "words");
        for (String word : words) {
            add(word);
        }
        modCount = 0;
    }

    /** Creates a radix tree from the supplied words. */
    public static RadixTree of(String... words) {
        Objects.requireNonNull(words, "words");
        RadixTree result = new RadixTree();
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

        if (word.isEmpty()) {
            if (root.terminal) {
                return false;
            }
            root.terminal = true;
            root.subtreeWords++;
            size++;
            modCount++;
            return true;
        }

        ArrayList<Node> path = new ArrayList<>();
        Node current = root;
        path.add(current);
        int offset = 0;

        while (offset < word.length()) {
            char key = word.charAt(offset);
            Node child = current.children.get(key, null);

            if (child == null) {
                Node added = new Node(word.substring(offset));
                added.terminal = true;
                added.subtreeWords = 1;
                current.children.put(key, added);
                incrementPath(path);
                finishAdd();
                return true;
            }

            int common = commonPrefixLength(word, offset, child.label);

            if (common == child.label.length()) {
                offset += common;
                current = child;
                path.add(current);
                continue;
            }

            Node split = new Node(child.label.substring(0, common));
            String oldSuffix = child.label.substring(common);
            child.label = oldSuffix;
            split.children.put(oldSuffix.charAt(0), child);
            split.subtreeWords = child.subtreeWords;
            current.children.put(key, split);

            offset += common;

            if (offset == word.length()) {
                split.terminal = true;
                split.subtreeWords++;
            } else {
                Node added = new Node(word.substring(offset));
                added.terminal = true;
                added.subtreeWords = 1;
                split.children.put(added.label.charAt(0), added);
                split.subtreeWords++;
            }

            incrementPath(path);
            finishAdd();
            return true;
        }

        if (current.terminal) {
            return false;
        }

        current.terminal = true;
        incrementPath(path);
        finishAdd();
        return true;
    }

    /** Returns whether the exact word is stored. */
    public boolean contains(String word) {
        requireText(word, "word");
        Node node = exactNode(word);
        return node != null && node.terminal;
    }

    /**
     * Returns whether at least one stored word begins with prefix.
     *
     * <p>The empty prefix matches exactly when the tree is non-empty.</p>
     */
    public boolean startsWith(String prefix) {
        return countWithPrefix(prefix) > 0;
    }

    /** Returns the number of stored words beginning with prefix. */
    public int countWithPrefix(String prefix) {
        requireText(prefix, "prefix");

        if (prefix.isEmpty()) {
            return root.subtreeWords;
        }

        Node current = root;
        int offset = 0;

        while (offset < prefix.length()) {
            Node child = current.children.get(prefix.charAt(offset), null);
            if (child == null) {
                return 0;
            }

            int remaining = prefix.length() - offset;
            int common = commonPrefixLength(prefix, offset, child.label);

            if (common == remaining) {
                return child.subtreeWords;
            }

            if (common != child.label.length()) {
                return 0;
            }

            offset += common;
            current = child;
        }

        return current.subtreeWords;
    }

    /**
     * Removes one exact word and recompresses any newly unary path.
     *
     * @return false when the word is absent
     */
    public boolean remove(String word) {
        requireText(word, "word");

        ArrayList<Node> path = exactPath(word);
        if (path == null) {
            return false;
        }

        Node target = path.get(path.size() - 1);
        if (!target.terminal) {
            return false;
        }

        target.terminal = false;
        for (Node node : path) {
            node.subtreeWords--;
        }

        size--;
        modCount++;
        compactPath(path);
        return true;
    }

    /**
     * Removes every stored word beginning with prefix.
     *
     * <p>The prefix may end in the middle of a compressed edge.</p>
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

        ArrayList<Node> path = new ArrayList<>();
        Node current = root;
        path.add(current);
        int offset = 0;

        while (offset < prefix.length()) {
            Node child = current.children.get(prefix.charAt(offset), null);
            if (child == null) {
                return 0;
            }

            int remaining = prefix.length() - offset;
            int common = commonPrefixLength(prefix, offset, child.label);

            if (common == remaining) {
                int removed = child.subtreeWords;
                if (removed == 0) {
                    return 0;
                }

                for (Node node : path) {
                    node.subtreeWords -= removed;
                }

                current.children.remove(child.label.charAt(0));
                size -= removed;
                modCount++;
                compactPath(path);
                return removed;
            }

            if (common != child.label.length()) {
                return 0;
            }

            offset += common;
            current = child;
            path.add(current);
        }

        return 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the number of compressed non-root nodes.
     *
     * <p>This is mainly useful for observing path compression. It is O(n) in
     * the number of radix nodes.</p>
     */
    public int nodeCount() {
        int count = 0;
        java.util.ArrayDeque<Node> stack = new java.util.ArrayDeque<>();

        for (char key : root.children) {
            stack.push(root.children.get(key));
        }

        while (!stack.isEmpty()) {
            Node node = stack.pop();
            count++;
            for (char key : node.children) {
                stack.push(node.children.get(key));
            }
        }

        return count;
    }

    /** Removes all words. */
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

    /** Returns all words in deterministic prefix-first traversal order. */
    public List<String> words() {
        return collectWords(root, "");
    }

    /** Returns all stored words beginning with prefix. */
    public List<String> wordsWithPrefix(String prefix) {
        requireText(prefix, "prefix");

        if (prefix.isEmpty()) {
            return words();
        }

        Node current = root;
        int offset = 0;

        while (offset < prefix.length()) {
            Node child = current.children.get(prefix.charAt(offset), null);
            if (child == null) {
                return new List<>();
            }

            int remaining = prefix.length() - offset;
            int common = commonPrefixLength(prefix, offset, child.label);

            if (common == remaining) {
                String fullNodeWord = prefix.substring(0, offset) + child.label;
                return collectWords(child, fullNodeWord);
            }

            if (common != child.label.length()) {
                return new List<>();
            }

            offset += common;
            current = child;
        }

        return collectWords(current, prefix);
    }

    /** Returns all stored words that are prefixes of text, shortest first. */
    public List<String> prefixesOf(String text) {
        requireText(text, "text");

        List<String> result = new List<>();
        Node current = root;
        int offset = 0;

        if (root.terminal) {
            result.append("");
        }

        while (offset < text.length()) {
            Node child = current.children.get(text.charAt(offset), null);
            if (child == null || !matchesLabel(text, offset, child.label)) {
                break;
            }

            offset += child.label.length();
            current = child;

            if (current.terminal) {
                result.append(text.substring(0, offset));
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
        int offset = 0;
        int longestLength = root.terminal ? 0 : -1;

        while (offset < text.length()) {
            Node child = current.children.get(text.charAt(offset), null);
            if (child == null || !matchesLabel(text, offset, child.label)) {
                break;
            }

            offset += child.label.length();
            current = child;

            if (current.terminal) {
                longestLength = offset;
            }
        }

        return longestLength < 0 ? null : text.substring(0, longestLength);
    }

    /** Returns an independent copy preserving traversal order. */
    public RadixTree copy() {
        RadixTree result = new RadixTree();
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
        return "RadixTree(" + words() + ")";
    }

    private void finishAdd() {
        size++;
        modCount++;
    }

    private void incrementPath(ArrayList<Node> path) {
        for (Node node : path) {
            node.subtreeWords++;
        }
    }

    private Node exactNode(String word) {
        ArrayList<Node> path = exactPath(word);
        return path == null ? null : path.get(path.size() - 1);
    }

    private ArrayList<Node> exactPath(String word) {
        ArrayList<Node> path = new ArrayList<>();
        Node current = root;
        path.add(current);

        if (word.isEmpty()) {
            return path;
        }

        int offset = 0;

        while (offset < word.length()) {
            Node child = current.children.get(word.charAt(offset), null);
            if (child == null || !matchesLabel(word, offset, child.label)) {
                return null;
            }

            offset += child.label.length();
            if (offset > word.length()) {
                return null;
            }

            current = child;
            path.add(current);
        }

        return offset == word.length() ? path : null;
    }

    private void compactPath(ArrayList<Node> path) {
        for (int index = path.size() - 1; index >= 1; index--) {
            Node node = path.get(index);
            Node parent = path.get(index - 1);

            if (node.subtreeWords == 0) {
                parent.children.remove(node.label.charAt(0));
                continue;
            }

            if (!node.terminal && node.children.size() == 1) {
                mergeOnlyChild(node);
            }
        }
    }

    private void mergeOnlyChild(Node node) {
        Iterator<Character> iterator = node.children.iterator();
        if (!iterator.hasNext()) {
            return;
        }

        char key = iterator.next();
        Node child = node.children.get(key);

        node.label = node.label + child.label;
        node.terminal = child.terminal;
        node.subtreeWords = child.subtreeWords;
        node.children = child.children;
    }

    private List<String> collectWords(Node start, String fullWordAtStart) {
        List<String> result = new List<>(start.subtreeWords);
        StringBuilder current = new StringBuilder(fullWordAtStart);

        if (start.terminal) {
            result.append(fullWordAtStart);
        }

        java.util.ArrayDeque<TraversalFrame> stack = new java.util.ArrayDeque<>();
        stack.push(new TraversalFrame(start, fullWordAtStart.length()));

        while (!stack.isEmpty()) {
            TraversalFrame frame = stack.peek();

            if (!frame.children.hasNext()) {
                current.setLength(frame.prefixLength);
                stack.pop();
                continue;
            }

            char key = frame.children.next();
            Node child = frame.node.children.get(key);

            current.setLength(frame.prefixLength);
            current.append(child.label);

            int childLength = current.length();
            if (child.terminal) {
                result.append(current.toString());
            }

            stack.push(new TraversalFrame(child, childLength));
        }

        return result;
    }

    private static int commonPrefixLength(
        String text,
        int offset,
        String label
    ) {
        int limit = Math.min(text.length() - offset, label.length());
        int index = 0;

        while (index < limit && text.charAt(offset + index) == label.charAt(index)) {
            index++;
        }

        return index;
    }

    private static boolean matchesLabel(
        String text,
        int offset,
        String label
    ) {
        return text.length() - offset >= label.length()
            && text.regionMatches(offset, label, 0, label.length());
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
    }

    private void checkForModification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    private static final class Node {
        private String label;
        private Dictionary<Character, Node> children = new Dictionary<>();
        private boolean terminal;
        private int subtreeWords;

        private Node(String label) {
            this.label = label;
        }
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
