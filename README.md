# DataStructures

Eigen implementaties van algemene en Pythonachtige datastructuren in Java, zonder externe
dependencies. `List` gebruikt een dynamische array. `LinkedList` gebruikt expliciete
nodes met `prev`/`next`-links. `Set` en `Dictionary`
gebruiken eigen hashtabellen met open adressering. `Dictionary` bewaart bovendien
de invoegvolgorde. `Tuple` is een onveranderlijke reeks met eventueel verschillende
typen elementen. `Stack` gebruikt een eigen dynamische array voor LIFO-bewerkingen.
`Queue` gebruikt een circulaire array voor FIFO-bewerkingen. `ArrayDeque` gebruikt een
circulaire array voor bewerkingen aan beide uiteinden.
`BinaryHeap` gebruikt een array voor een prioriteitswachtrij. `BinarySearchTree`
gebruikt private nodes met parent/left/right-links voor geordende set-semantiek.

## Bouwen en testen

Vereist een **JDK 17 of nieuwer**, met `java` en `javac` op je PATH.
Open PowerShell in deze map en voer uit:

```powershell
.\test.ps1
.\test.ps1 -Demo
```

Het script compileert met `--release 17 -Xlint:all -Werror` en voert elf testsuites
uit. Tests gebruiken expliciete controles en werken ook zonder `-ea`.
De gecompileerde bestanden komen in `build/classes`.

Handmatig bouwen en uitvoeren kan ook, vanuit deze map:

```text
javac --release 17 -Xlint:all -Werror -d build/classes *.java tests/*.java
java -cp build/classes DataStructures.AllTests
java -cp build/classes DataStructures.Examples
```

Gebruik expliciete imports zoals `import DataStructures.List;` om verwarring met
`java.util.List` te voorkomen. De package blijft `DataStructures`.

## Voorbeelden

```java
List<Integer> values = List.of(0, 1, 2, 3, 4, 5);
values.get(-1);                         // 5
values.slice(1, 5, 2);                  // [1, 3]
values.sliceAll(-1);                    // [5, 4, 3, 2, 1, 0]
values.sliceFrom(3, -1);                // [3, 2, 1, 0]
values.setSlice(0, 6, 2, List.of(9, 8, 7)); // [9, 1, 8, 3, 7, 5]
values.removeSlice(1, 6, 2);            // [9, 8, 7]

List<String> names = List.of("Ada", "Grace", "Linus");
names.sort(String::length);            // stabiel; key eenmaal per element

LinkedList<String> route = LinkedList.of("A", "B", "C");
route.addFirst("start");
route.addLast("finish");
route.get(-1);                         // "finish"
route.removeAt(2);                     // verwijdert "B"

Tuple pair = Tuple.of("score", 42);
int score = pair.get(-1, Integer.class);

Set<Integer> left = Set.of(1, 2, 3);
Set<Integer> right = Set.of(3, 4);
Set<Integer> common = left.intersection(right); // {3}

Dictionary<String, Integer> counts = new Dictionary<>();
counts.put("apple", 2);
counts.setDefault("pear", 0);
counts.get("missing", 0);               // 0
counts.items();                         // live view van key/value-tuples
counts.popItem();                       // laatst ingevoegd item

Stack<String> history = Stack.of("open", "edit");
history.push("save");
history.peek();                              // "save"
history.pop();                               // "save"

Queue<String> jobs = Queue.of("compile", "test");
jobs.offer("package");
jobs.peek();                             // "compile"
jobs.poll();                             // "compile"

ArrayDeque<String> deque = ArrayDeque.of("first", "second");
deque.addLast("third");
deque.removeFirst();                    // "first"
deque.push("urgent");                   // toevoegen aan de voorkant
deque.pop();                            // "urgent": LIFO-stack

BinarySearchTree<Integer> tree =
    BinarySearchTree.of(8, 4, 12, 2, 6, 10, 14);
tree.inOrder();                         // [2, 4, 6, 8, 10, 12, 14]
tree.levelOrder();                      // [8, 4, 12, 2, 6, 10, 14]
tree.floor(9);                          // 8
tree.higher(10);                        // 12

BinaryHeap<Integer> heap = BinaryHeap.of(8, 3, 5, 1);
heap.peek();                            // 1, zonder te verwijderen
heap.poll();                            // verwijdert 1
heap.sorted();                          // [3, 5, 8], behoudt de heap

BinaryHeap<Integer> maxHeap = new BinaryHeap<>(
    List.of(8, 3, 5, 1), java.util.Comparator.reverseOrder()
);
maxHeap.poll();                         // 8
```

`Examples.java` bevat een uitvoerbare demo.

## Gedrag en beschikbare bewerkingen

| Structuur | Belangrijkste bewerkingen |
| --- | --- |
| `List<T>` | append, extend, insert, get/set, remove/pop, zoeken, slicing, slice vervangen/verwijderen, sort/sorted, concat, repeat, streams |
| `LinkedList<T>` | append/prepend, add/remove aan beide uiteinden, insert, get/set, removeAt/pop, zoeken, reverse, voorwaartse/achterwaartse iteratie, streams |
| `Tuple` | get met optioneel type, first/last, zoeken, slicing, concat, repeat, reversed, sorted, Java-conversies, streams |
| `Set<T>` | add/remove/discard/pop, union, intersection, difference, symmetricDifference, varianten die de set wijzigen, subset/superset/disjoint, streams |
| `Dictionary<K,V>` | put/get/setDefault, remove/pop/popItem, update/union, updateEntries/updateItems, live keys/values/items, snapshots, reversed, streams |
| `Stack<T>` | push/pop/peek, contains/search, clear, kopieën, snapshots, streams |
| `Queue<T>` | add/offer, remove/poll, element/peek, contains/remove(value), clear, kopieën, snapshots, streams |
| `ArrayDeque<T>` | add/remove/poll/peek aan beide uiteinden, queue- en stackmethoden, verwijderen op waarde, voorwaartse/achterwaartse iteratie, kopieën, streams |
| `BinaryHeap<T>` | add/offer, peek/poll, element/remove, verwijderen op waarde, comparator, heapify-constructor, gesorteerde kopie, streams |
| `BinarySearchTree<T>` | add/remove/contains, min/max, lower/floor/ceiling/higher, height/depth, in/pre/post/level-order traversals, reverse iteratie, streams |

- Negatieve indices tellen vanaf het einde; een slice-eindpunt is exclusief.
  Slicegrenzen worden begrensd tot de reeks. Een stap van nul is ongeldig.
- `sliceFrom`, `sliceTo` en `sliceAll` ondersteunen weggelaten grenzen voor lists
  en tuples. Bij een negatieve stap is een weggelaten eindpunt iets anders dan
  expliciet `-1`: `sliceAll(-1)` keert de reeks om, `slice(5, -1, -1)` is leeg.
- `setSlice(start, end, values)` mag de lengte veranderen. Bij de overload met
  een stap ongelijk aan één moet het aantal vervangingen precies passen.
  Een lengtefout verandert de lijst niet. De lijst zelf mag als vervanging dienen.
- Sorteren is stabiel. Een fout in de comparator of keyfunctie publiceert geen
  gedeeltelijk gesorteerde lijst. Structurele wijzigingen vanuit zo'n callback
  worden gedetecteerd; de eigen wijzigingen van de callback worden niet teruggedraaid.
- `List`, `LinkedList`, `Tuple`, `Set`, `Dictionary` en `Stack` staan `null` toe als element, waarde of key. Bij de
  weergave verschijnt dit als `None`, met `True`/`False` voor booleans en quotes
  rond strings. Cyclische verwijzingen krijgen een `...`-placeholder.
- Kopieën en Java-conversies zijn oppervlakkig: de container wordt gekopieerd,
  de elementen niet. Dictionaryviews blijven gekoppeld aan de dictionary;
  `keysSnapshot`, `valuesSnapshot` en `itemsSnapshot` maken losse lijsten.
- `Dictionary.updateEntries(...)` accepteert getypeerde Java `Map.Entry`-paren.
  `updateItems(...)` accepteert tuples; daarbij moet je zelf de key- en waardetypen
  juist kiezen, omdat tuples geen generieke typecontrole bieden.
- Structurele wijzigingen maken bestaande iterators ongeldig. De structuren
  zijn niet bedoeld voor gelijktijdig wijzigen vanuit meerdere threads.
- `List`, `LinkedList`, `Tuple`, `Set` en `Dictionary` vergelijken hun inhoud met
  instanties van dezelfde custom structuur. `Stack`, `Queue`, `ArrayDeque`, `BinaryHeap`
  en `BinarySearchTree` gebruiken objectidentiteit voor `equals` en `hashCode`.
  Hashkeys en setelementen moeten stabiele
  `equals`/`hashCode` houden. Dictionary weigert mutable `List`, `LinkedList`,
  `Set` en `Dictionary`-instanties als key, ook wanneer die in een tuple zitten.

## LinkedList

`LinkedList` is een doubly linked list. Elke interne node bewaart een waarde en
referenties naar de vorige en volgende node. `addFirst`, `addLast`, `removeFirst`
en `removeLast` zijn daardoor O(1). `get`, `set`, `insert` en `removeAt` lopen vanaf
het dichtstbijzijnde uiteinde en kosten O(min(i, n-i)). Negatieve indices worden
zoals bij `List` vanaf het einde geïnterpreteerd; `insert` begrenst indices zoals
Python. `descendingIterator()` en `reversed()` lopen van tail naar head.

De nodes zijn implementatiedetails en worden niet publiek blootgesteld. `reverse()`
draait de links in-place om. De klasse laat `null` toe en gebruikt structurele,
volgordegevoelige `equals`/`hashCode`, waardoor een mutable `LinkedList` niet als
Dictionary-key mag worden gebruikt.

## BinarySearchTree

`BinarySearchTree` is de eerste structuur van Phase 3 (Trees). Waarden zijn uniek
volgens natuurlijke ordening of een opgegeven `Comparator<? super T>`; wanneer
twee waarden `compare(...) == 0` opleveren, blijft de reeds opgeslagen waarde staan
en geeft `add` `false` terug. `null` wordt geweigerd.

De boom bewaart private parent/left/right-links. `minimum`, `maximum`,
`pollMinimum` en `pollMaximum` werken via de buitenste paden. `lower`, `floor`,
`ceiling` en `higher` bieden navigatie zoals een geordende set. `iterator()` en
`stream()` leveren in-order (gesorteerde) waarden; `descendingIterator()` en
`reversed()` lopen in de omgekeerde comparatorvolgorde. Daarnaast zijn expliciete
`inOrder`, `preOrder`, `postOrder` en `levelOrder` traversals beschikbaar.

De boom balanceert zichzelf bewust **niet**. `height()` telt niveaus: een lege boom
heeft hoogte 0 en een losse root hoogte 1. Een oplopende invoer kan dus een keten
met hoogte n vormen. Dit maakt het verschil met de volgende tree-fase, een
zelfbalancerende AVL-tree, expliciet zichtbaar.

## Stack, Queue, ArrayDeque en BinaryHeap

`Stack` laat `null` toe. `pop()` en `peek()` gooien een `NoSuchElementException`
wanneer de stack leeg is, zodat `null` nooit als leegtesentinel hoeft te dienen.
Iteratie en snapshots lopen van onder naar boven; `search(value)` telt vanaf de top
met een één-gebaseerde afstand en geeft `-1` terug wanneer de waarde ontbreekt.

`Queue`, `ArrayDeque` en `BinaryHeap` weigeren `null`. Daardoor kan `poll` of `peek` ondubbelzinnig
`null` teruggeven wanneer de structuur leeg is. `remove()` en `element()` gooien
dan een `NoSuchElementException`; hetzelfde geldt voor `pop`, `removeFirst`,
`removeLast`, `getFirst` en `getLast` bij de deque.

`Queue` voegt met `add`/`offer` achteraan toe en leest of verwijdert met
`element`/`peek`/`remove`/`poll` vooraan. `remove(value)` verwijdert de eerste
overeenkomst. De queue gebruikt een circulaire buffer, zodat normaal enqueue- en
dequeuewerk geen elementen hoeft te verschuiven.

Bij de deque voegen `add`/`offer` achteraan toe en lezen of verwijderen
`element`/`peek`/`remove`/`poll` vooraan. `push` en `pop` werken beide vooraan.
`remove(value)` verwijdert de eerste overeenkomst, met een boolean als resultaat.
`descendingIterator()` loopt van achteren naar voren.

De heap gebruikt standaard natuurlijke ordening, met het kleinste element
bovenaan. Een `Comparator<? super T>` bepaalt een andere volgorde, bijvoorbeeld
`Comparator.reverseOrder()` voor een max-heap. De constructor met een `Iterable`
bouwt de heap met bottom-up heapify. Duplicaten zijn toegestaan; de volgorde
tussen elementen met gelijke prioriteit is niet gegarandeerd.

Iteratie, `toArray`, `toDataList`, `toJavaList` en `toString` van de heap tonen de
interne heapvolgorde, **niet noodzakelijk een gesorteerde volgorde**. Gebruik
`sorted()` voor een nieuwe custom lijst in prioriteitsvolgorde, of herhaaldelijk
`poll()` om de heap in die volgorde leeg te halen. Verander de prioriteit van een
opgeslagen element niet; verwijder het eerst en voeg het na aanpassing opnieuw toe.
Comparatoren moeten een consistente ordening geven en mogen de heap niet wijzigen.
Wanneer een vergelijking faalt, wordt de voorgenomen toevoeging of verwijdering
niet gedeeltelijk uitgevoerd. Structurele wijzigingen door een comparator worden
gedetecteerd; de eigen wijzigingen van die callback worden niet teruggedraaid.

`copy()` maakt een onafhankelijke, oppervlakkige kopie. `toJavaList()` geeft een
onveranderbare Java-lijstsnapshot. `trimToSize()` geeft ongebruikte arraycapaciteit
vrij; verwijderen verkleint de array niet automatisch. De iterators ondersteunen
geen `remove()`. De klassen implementeren `Iterable<T>`, met een API die aansluit
op Java's queues; ze implementeren niet de volledige `java.util.Deque`- of
`java.util.Queue`-interface.

## Complexiteit

`List.get/set` zijn O(1), `append` is geamortiseerd O(1), invoegen/verwijderen
binnen de lijst is O(n), en sorteren is O(n log n). Zoeken, slicing en kopiëren
zijn lineair in het aantal onderzochte of gekopieerde elementen.
Bij `LinkedList` zijn toevoegen en verwijderen aan head/tail O(1). Geïndexeerde
toegang kost O(min(i, n-i)); zoeken, kopiëren en omkeren zijn O(n). De structuur
gebruikt O(n) extra node-opslag en hoeft geen overcapaciteit te reserveren.

Hashbewerkingen van `Set` en `Dictionary` zijn gemiddeld O(1), met O(n) als
veel hashes botsen. Het opschalen of verkleinen van een hashtabel is O(n).

Bij `Stack` is `push` geamortiseerd O(1) en zijn `pop` en `peek` O(1).
Zoeken is O(n); een vergroting, kopie of `trimToSize()` kost O(n).

Bij `Queue` zijn `add`/`offer` geamortiseerd O(1) en zijn frontinspectie en
frontverwijdering O(1). Zoeken en verwijderen op waarde zijn O(n). Groei, kopiëren
en `trimToSize()` kosten O(n).

Bij `ArrayDeque` zijn toevoegen en verwijderen aan beide uiteinden geamortiseerd
O(1), en bekijken is O(1). Zoeken/verwijderen op waarde is O(n). De capaciteit
groeit geometrisch; een vergroting, kopie of `trimToSize()` kost O(n).

Bij `BinarySearchTree` kosten `add`, `contains`, `remove`, navigatie en
`minimum`/`maximum` O(h), waarbij h de boomhoogte is. Voor een redelijk gevormde
boom is dat typisch O(log n), maar zonder balancing kan h tot n groeien.
Traversals, `height()` en kopiëren zijn O(n); opslag is O(n).

Bij `BinaryHeap` is `peek` O(1). Toevoegen en het bovenste element verwijderen
kosten O(log n), afgezien van incidentele O(n)-arraygroei bij toevoegen.
Bulkconstructie met heapify is O(n); zoeken of verwijderen op waarde is O(n).
Een gesorteerde kopie kost O(n log n). Beide structuren gebruiken O(n) opslag,
met eventueel gereserveerde ruimte voor groei.

De tests controleren onder meer extreme slicegrenzen en stapgroottes,
sortering en callbackfouten, recursieve weergave, tuple-immutabiliteit,
hashbotsingen, nulls, verwijderingen en invoegvolgorde. Willekeurige bewerkingen
met vaste seeds worden vergeleken met Java's standaardcollecties, waaronder
`java.util.ArrayDeque`, `java.util.PriorityQueue` en `java.util.TreeSet`. De nieuwe
tests controleren BST-verwijderingen met nul/één/twee kinderen, navigatiegrenzen,
alle vier traversals, comparatorgedrag, degeneratie en randomized differential
operations, en controleren
ook linked-list pointerinvarianten, negatieve indices, reverse traversal en
randomized differential tests tegen `java.util.LinkedList`, LIFO-gedrag,
stackgroei en nullwaarden, FIFO-gedrag en queue-wraparound,
circulaire deque-wraparound, groei/trimmen,
comparatoren, heapvolgorde, duplicaten en de lege-structuurcontracten.
