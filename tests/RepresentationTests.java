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

        equal("ArrayDeque([])", new ArrayDeque<>().toString(), "empty deque");
        equal("ArrayDeque(['first', True])", ArrayDeque.of("first", true).toString(), "deque repr");
        ArrayDeque<Object> deque = new ArrayDeque<>();
        deque.addLast(deque);
        equal("ArrayDeque([ArrayDeque([...])])", deque.toString(), "self-referencing deque");

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
        identityKeys.put(deque, "deque");
        identityKeys.put(heap, "heap");
        deque.addLast("changed");
        heap.add("changed");
        equal("deque", identityKeys.get(deque), "deque identity key survives mutation");
        equal("heap", identityKeys.get(heap), "heap identity key survives mutation");
    }
}
