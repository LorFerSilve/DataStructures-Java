package DataStructures;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;

/** Deterministic dictionary regressions and model-based checks. */
public final class DictionaryTests {
    private DictionaryTests() {
    }

    public static void run() {
        testViewsAndIteration();
        testBulkUpdatesAndCopies();
        testCapacityAndKeys();
        for (long seed : new long[] {0, 1, 731_591, 2_026_092_2}) {
            randomizedOperations(seed);
        }
    }

    private static void testViewsAndIteration() {
        Dictionary<String, Integer> dictionary = new Dictionary<>();
        Dictionary<String, Integer>.KeyView keys = dictionary.keys();
        Dictionary<String, Integer>.ValueView values = dictionary.values();
        Dictionary<String, Integer>.ItemView items = dictionary.items();
        Iterable<String> reversed = dictionary.reversed();
        Stream<String> stream = dictionary.stream();

        dictionary.put("first", null);
        dictionary.put("second", 2);
        check(collect(keys).equals(Arrays.asList("first", "second")), "Live keys");
        check(collect(values).equals(Arrays.asList(null, 2)), "Live null values");
        check(items.contains(Tuple.of("first", null)), "Live item membership");
        check(!items.contains(Tuple.of("absent", null)), "Missing null item");
        check(!items.contains(Tuple.of("first")), "Malformed item");
        check(stream.toList().equals(Arrays.asList("first", "second")), "Late-binding stream");
        check(collect(reversed).equals(Arrays.asList("second", "first")), "Live reversed keys");

        List<Integer> snapshot = dictionary.valuesSnapshot();
        Iterator<Integer> iterator = values.iterator();
        dictionary.put("first", 1);
        check(Objects.equals(iterator.next(), 1), "Iterator sees value replacement");
        dictionary.put("second", 20);
        check(Objects.equals(iterator.next(), 20), "Iterator sees later replacement");
        expect(NoSuchElementException.class, iterator::next);
        check(collect(snapshot).equals(Arrays.asList(null, 2)), "Independent snapshot");

        java.util.List<Iterator<?>> iterators = Arrays.asList(
            dictionary.iterator(), keys.iterator(), values.iterator(),
            items.iterator(), reversed.iterator()
        );
        dictionary.put("third", 3);
        for (Iterator<?> stale : iterators) {
            expect(ConcurrentModificationException.class, stale::hasNext);
            expect(ConcurrentModificationException.class, stale::next);
        }
        expect(UnsupportedOperationException.class, dictionary.iterator()::remove);
        dictionary.remove("first");
        dictionary.put("first", 10);
        check(collect(keys).equals(Arrays.asList("second", "third", "first")), "Reinsert order");
        check(collect(reversed).equals(Arrays.asList("first", "third", "second")), "Reverse links");
        check(keys.size() == 3 && values.size() == 3 && items.size() == 3, "View sizes");

        dictionary.forEach((key, value) -> dictionary.put(key, value + 1));
        check(collect(values).equals(Arrays.asList(21, 4, 11)), "Nonstructural forEach update");
        expect(ConcurrentModificationException.class,
            () -> dictionary.forEach((key, value) -> dictionary.remove(key)));
        dictionary.clear();
        check(keys.isEmpty() && values.isEmpty() && items.isEmpty(), "Views reflect clear");
        expect(NoSuchElementException.class, dictionary::popItem);
    }

    private static void testBulkUpdatesAndCopies() {
        Dictionary<CharSequence, Number> dictionary = new Dictionary<>();
        java.util.List<Map.Entry<String, Integer>> entries = Arrays.asList(
            new AbstractMap.SimpleImmutableEntry<>("first", 1),
            new AbstractMap.SimpleImmutableEntry<>(null, null),
            new AbstractMap.SimpleImmutableEntry<>("second", 2),
            new AbstractMap.SimpleImmutableEntry<>("first", 10)
        );
        dictionary.updateEntries(entries);
        check(collect(dictionary).equals(Arrays.asList("first", null, "second")), "Typed update order");
        check(dictionary.get("first").equals(10), "Typed update last value wins");
        check(dictionary.containsKey(null) && dictionary.get(null) == null, "Typed update nulls");
        expect(NullPointerException.class, () -> dictionary.updateEntries(null));
        expect(NullPointerException.class,
            () -> dictionary.updateEntries(Collections.singletonList(null)));

        dictionary.updateItems(Arrays.asList(Tuple.of("second", 20), Tuple.of("third", 3)));
        expect(IllegalArgumentException.class,
            () -> dictionary.updateItems(Collections.singletonList(Tuple.of("wrong"))));
        expect(NullPointerException.class,
            () -> dictionary.updateItems(Collections.singletonList(null)));
        dictionary.update(dictionary);

        Dictionary<CharSequence, Number> copy = dictionary.copy();
        Dictionary<CharSequence, Number> constructed = new Dictionary<>(dictionary);
        check(copy.equals(dictionary) && constructed.equals(dictionary), "Copy equality");
        check(copy.hashCode() == dictionary.hashCode(), "Equal dictionaries hash equally");
        copy.put("first", 100);
        check(dictionary.get("first").equals(10), "Independent copy values");
        check(constructed.toJavaMap().equals(dictionary.toJavaMap()), "Map snapshot values");
        Map<CharSequence, Number> snapshot = dictionary.toJavaMap();
        expect(UnsupportedOperationException.class, () -> snapshot.put("new", 5));
        expect(UnsupportedOperationException.class,
            () -> snapshot.entrySet().iterator().next().setValue(5));
        dictionary.put("fourth", 4);
        check(!snapshot.containsKey("fourth"), "Independent map snapshot");

        LinkedHashMap<CharSequence, Number> later = new LinkedHashMap<>();
        later.put("first", 50);
        later.put("last", null);
        Dictionary<CharSequence, Number> merged = dictionary.union(later);
        check(dictionary.get("first").equals(10), "Union preserves source");
        check(merged.get("first").equals(50) && merged.containsKey("last"), "Union values");
        check(collect(merged).equals(Arrays.asList("first", null, "second", "third", "fourth", "last")),
            "Union insertion order");

        Dictionary<CharSequence, Number> reordered = new Dictionary<>();
        java.util.List<CharSequence> order = collect(dictionary);
        Collections.reverse(order);
        for (CharSequence key : order) {
            reordered.put(key, dictionary.get(key));
        }
        check(dictionary.equals(reordered) && reordered.equals(dictionary), "Order-independent equality");
        check(dictionary.hashCode() == reordered.hashCode(), "Order-independent hash");
        check(!dictionary.equals(dictionary.toJavaMap()), "Only custom dictionaries compare equal");
    }

    private static void testCapacityAndKeys() {
        expect(IllegalArgumentException.class, () -> new Dictionary<>(-1));
        expect(IllegalArgumentException.class, () -> new Dictionary<>(Integer.MAX_VALUE));
        Dictionary<Integer, Integer> large = new Dictionary<>(1_000);
        int expectedCapacity = large.capacity();
        for (int i = 0; i < 1_000; i++) {
            large.put(i, i);
        }
        check(large.capacity() == expectedCapacity, "Expected-size capacity");
        for (int i = 0; i < 999; i++) {
            check(Objects.equals(large.remove(i), i), "Removal during shrinking");
        }
        check(large.get(999).equals(999) && large.capacity() == 16, "Shrink preserves mapping");
        large.clear();
        check(large.isEmpty() && large.capacity() == 16, "Clear resets capacity");

        Dictionary<Object, Integer> dictionary = new Dictionary<>();
        expect(IllegalArgumentException.class, () -> dictionary.put(List.of(1), 1));
        expect(IllegalArgumentException.class, () -> dictionary.put(Set.of(1), 1));
        expect(IllegalArgumentException.class, () -> dictionary.put(new Dictionary<>(), 1));
        expect(IllegalArgumentException.class, () -> dictionary.put(Tuple.of(Tuple.of(List.of(1))), 1));
        dictionary.put(Tuple.of(1, Tuple.of("nested", null)), 5);
        check(dictionary.get(Tuple.of(1, Tuple.of("nested", null))).equals(5), "Stable tuple key");

        Dictionary<String, Object> fromKeys = Dictionary.fromKeys(Arrays.asList("a", "b", "a"));
        check(collect(fromKeys).equals(Arrays.asList("a", "b")), "fromKeys order and deduplication");
        check(fromKeys.get("a") == null, "fromKeys default value");
    }

    private static void randomizedOperations(long seed) {
        Random random = new Random(seed);
        Dictionary<CollisionKey, Integer> actual = new Dictionary<>();
        LinkedHashMap<CollisionKey, Integer> expected = new LinkedHashMap<>();
        for (int step = 0; step < 12_000; step++) {
            CollisionKey key = random.nextInt(20) == 0 ? null : new CollisionKey(random.nextInt(128));
            Integer value = random.nextInt(5) == 0 ? null : random.nextInt(1_000);
            switch (random.nextInt(10)) {
                case 0, 1, 2 -> check(Objects.equals(actual.put(key, value), expected.put(key, value)),
                    "put result");
                case 3 -> {
                    if (!expected.containsKey(key)) {
                        expected.put(key, value);
                    }
                    check(Objects.equals(actual.setDefault(key, value), expected.get(key)), "setDefault result");
                }
                case 4 -> {
                    Integer removed = expected.containsKey(key) ? expected.remove(key) : value;
                    check(Objects.equals(actual.pop(key, value), removed), "pop result");
                }
                case 5 -> {
                    if (expected.containsKey(key)) {
                        check(Objects.equals(actual.remove(key), expected.remove(key)), "remove result");
                    } else {
                        expect(NoSuchElementException.class, () -> actual.remove(key));
                    }
                }
                case 6 -> {
                    check(actual.containsKey(key) == expected.containsKey(key), "containsKey result");
                    check(Objects.equals(actual.get(key, value), expected.getOrDefault(key, value)), "get default");
                    check(actual.containsValue(value) == expected.containsValue(value), "containsValue result");
                    if (expected.containsKey(key)) {
                        check(Objects.equals(actual.get(key), expected.get(key)), "get result");
                    } else {
                        expect(NoSuchElementException.class, () -> actual.get(key));
                    }
                }
                case 7 -> {
                    if (expected.isEmpty()) {
                        expect(NoSuchElementException.class, actual::popItem);
                    } else {
                        CollisionKey last = null;
                        for (CollisionKey present : expected.keySet()) {
                            last = present;
                        }
                        Tuple item = actual.popItem();
                        check(Objects.equals(item.get(0), last), "popItem last key");
                        check(Objects.equals(item.get(1), expected.remove(last)), "popItem last value");
                    }
                }
                case 8 -> {
                    if (random.nextInt(80) == 0) {
                        actual.clear();
                        expected.clear();
                    } else {
                        LinkedHashMap<CollisionKey, Integer> update = new LinkedHashMap<>();
                        update.put(key, value);
                        update.put(new CollisionKey(random.nextInt(128)), random.nextInt(1_000));
                        actual.update(update);
                        expected.putAll(update);
                    }
                }
                case 9 -> check(actual.copy().toJavaMap().equals(expected), "Random copy");
                default -> throw new AssertionError("Unreachable operation");
            }
            if (step % 16 == 0) {
                verifyModel(actual, expected, seed, step);
            }
        }
        verifyModel(actual, expected, seed, 12_000);
    }

    private static void verifyModel(
        Dictionary<CollisionKey, Integer> actual,
        LinkedHashMap<CollisionKey, Integer> expected,
        long seed,
        int step
    ) {
        String context = " (seed " + seed + ", step " + step + ")";
        check(actual.size() == expected.size(), "Size" + context);
        check(actual.isEmpty() == expected.isEmpty(), "Empty state" + context);
        check(actual.toJavaMap().equals(expected), "Mappings" + context);
        check(collect(actual).equals(new ArrayList<>(expected.keySet())), "Order" + context);
        check(collect(actual.values()).equals(new ArrayList<>(expected.values())), "Values" + context);
        java.util.List<CollisionKey> reverse = new ArrayList<>(expected.keySet());
        Collections.reverse(reverse);
        check(collect(actual.reversed()).equals(reverse), "Reverse order" + context);
        for (Map.Entry<CollisionKey, Integer> entry : expected.entrySet()) {
            check(actual.items().contains(Tuple.of(entry.getKey(), entry.getValue())), "Item membership" + context);
        }
    }

    private static <T> java.util.List<T> collect(Iterable<T> values) {
        java.util.List<T> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expect(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
        } catch (Throwable exception) {
            if (expected.isInstance(exception)) {
                return;
            }
            throw new AssertionError("Expected " + expected.getSimpleName(), exception);
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }

    private record CollisionKey(int id) {
        @Override
        public int hashCode() {
            return id % 3 == 0 ? Integer.MIN_VALUE : id % 3;
        }
    }
}
