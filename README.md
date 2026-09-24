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
`AVLTree` voegt per node hoogte-metadata en automatische rotations toe om de boom
na elke mutatie gebalanceerd te houden. `Graph` gebruikt een adjacency-map bovenop
de eigen `Dictionary`-implementatie en bewaart zowel vertices als neighbors in
invoegvolgorde. `DisjointSet` implementeert Union-Find met path compression en
union-by-size voor efficiënte dynamische componentqueries. `Trie` is een
klassieke prefixboom voor efficiënte string- en prefixqueries; `RadixTree`
comprimeert niet-vertakkende trie-paden tot meertekens-edges.

## Bouwen en testen

Vereist een **JDK 17 of nieuwer**, met `java` en `javac` op je PATH.
Open PowerShell in deze map en voer uit:

```powershell
.\test.ps1
.\test.ps1 -Demo
```

Het script compileert met `--release 17 -Xlint:all -Werror` en voert zestien testsuites
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

AVLTree<Integer> avl = new AVLTree<>();
for (int value = 1; value <= 7; value++) {
    avl.add(value);
}
avl.levelOrder();                       // [4, 2, 6, 1, 3, 5, 7]
avl.height();                           // 3

Graph<String> graph = new Graph<>(true);
for (String step : java.util.List.of("compile", "test", "package")) {
    graph.addVertex(step);
}
graph.addEdge("compile", "test");
graph.addEdge("test", "package");
graph.breadthFirst("compile");           // [compile, test, package]
graph.topologicalSort();                 // [compile, test, package]

DisjointSet<String> services =
    DisjointSet.of("frontend", "api", "database", "cache");
services.union("api", "database");
services.union("frontend", "api");
services.connected("frontend", "database"); // true
services.components();                  // [[frontend, api, database], [cache]]

Trie routes = Trie.of("api", "app", "apple", "auth");
routes.wordsWithPrefix("ap");           // [api, app, apple]
routes.prefixesOf("apple/pay");         // [app, apple]
routes.longestPrefixOf("apple/pay");    // "apple"

RadixTree compressed =
    RadixTree.of("compression", "compress", "company", "compact");
compressed.wordsWithPrefix("comp");     // alle vier woorden
compressed.nodeCount();                 // minder nodes dan een gewone trie

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
| `AVLTree<T>` | dezelfde geordende-set API als BST, plus automatische LL/RR/LR/RL balancing met opgeslagen subtree heights |
| `Graph<V>` | directed/undirected vertices en edges, neighbors/degrees, BFS/DFS, shortest path, components, cycle detection, topological sort, streams |
| `DisjointSet<T>` | add/find/union, connected, componentSize/count, representatives, components, kopieën, streams |
| `Trie` | add/remove, exact lookup, prefix lookup/count, removePrefix, wordsWithPrefix, prefixesOf, longestPrefixOf, kopieën, streams |
| `RadixTree` | dezelfde prefix-API als Trie, plus path-compressed edges, recompressie na removal en nodeCount |

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
  instanties van dezelfde custom structuur. `Stack`, `Queue`, `ArrayDeque`, `BinaryHeap`,
  `BinarySearchTree`, `AVLTree`, `Graph`, `DisjointSet`, `Trie` en `RadixTree` gebruiken objectidentiteit voor `equals` en `hashCode`.
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
met hoogte n vormen. Dit vormt het referentiepunt voor de zelfbalancerende AVL-tree.

## AVLTree

`AVLTree` rondt Phase 3 (Trees) af. De publieke ordered-set API sluit bewust aan op
`BinarySearchTree`: unieke comparatorwaarden, `minimum`/`maximum`,
`pollMinimum`/`pollMaximum`, `lower`/`floor`/`ceiling`/`higher`, depth/height,
de vier traversals, reverse iteratie, streams en shallow copies.

Elke AVL-node bewaart daarnaast zijn subtree-height. Na insertions en removals wordt
vanaf het gewijzigde pad naar de root opnieuw gebalanceerd. De implementatie bevat
de vier klassieke gevallen: LL en RR met één rotation, en LR en RL met een dubbele
rotation. Parent-links, child-links en height-metadata worden tijdens rotations
atomair hersteld voordat de volgende ancestor wordt verwerkt.

Daardoor blijft de hoogte O(log n), ook voor pathologische invoervolgordes zoals
`1, 2, 3, ..., n`. In tegenstelling tot de gewone BST kan een gesorteerde invoer de
AVL-boom dus niet degraderen tot een lineaire keten.

## Graph

`Graph<V>` start Phase 4 (Graph Structures). De structuur ondersteunt zowel
directed als undirected graphs. Vertices worden expliciet toegevoegd; `addEdge`
vereist dat beide endpoints al bestaan. Parallelle edges worden geweigerd,
self-loops zijn toegestaan en `null` vertices zijn niet toegestaan.

Elke vertex heeft een eigen adjacency-`Dictionary`, zodat neighbor-volgorde
reproduceerbaar blijft. `breadthFirst` en `depthFirst` zijn daardoor deterministisch.
`shortestPath` gebruikt BFS en geeft voor deze ongewogen graph een pad met minimaal
aantal edges. Undirected graphs ondersteunen `connectedComponents` en
`isConnected`; `hasCycle` werkt voor beide graph-types. Directed acyclic graphs
kunnen met Kahn's algoritme via `topologicalSort` geordend worden.

`edgeList()` geeft `Tuple`-paren terug. Bij undirected graphs verschijnt elke edge
slechts één keer, ook al worden beide adjacency-richtingen intern opgeslagen.
Structurele wijzigingen aan vertices **of edges** maken bestaande Graph-iterators
en gebonden spliterators ongeldig.

## DisjointSet

`DisjointSet<T>` is de tweede structuur van Phase 4. Elk toegevoegd element start
als een afzonderlijke component. `union(a, b)` voegt twee componenten samen,
`find(value)` geeft de huidige representative terug en `connected(a, b)` controleert
of twee elementen tot dezelfde component behoren. Ontbrekende elementen worden
niet impliciet toegevoegd: queries en unions vereisen dat beide waarden al bestaan.

De interne Union-Find-bomen gebruiken union-by-size: de kleinere boom wordt onder
de grotere gehangen. Bij gelijke grootte blijft de representative van het eerste
argument behouden. `find` past volledige path compression toe, waardoor bezochte
nodes rechtstreeks naar hun root gaan wijzen. `componentSize`, `componentCount`,
`representatives` en `components` bieden snapshots van de huidige partitionering.
Componenten en hun leden worden deterministisch gerapporteerd volgens globale
invoegvolgorde.

Iterators lopen uitsluitend over opgeslagen elementen. Een `union` of `find`
wijzigt daarom geen iteratievolgorde en maakt bestaande iterators niet ongeldig;
`add` en `clear` doen dat wel. `copy()` bewaart de partitionering en dezelfde
representative-waarden, maar heeft onafhankelijke interne Union-Find-nodes.

Met `Graph<V>` en `DisjointSet<T>` is Phase 4 (Graph Structures) afgerond.

## Trie

`Trie` start Phase 5 (String Structures). De structuur slaat unieke,
case-sensitive strings op in een prefixboom. Elke node gebruikt een eigen
`Dictionary<Character, Node>`, waardoor sibling-takken deterministisch de
volgorde volgen waarin hun edges voor het eerst zijn aangemaakt. Een opgeslagen
prefix wordt vóór zijn langere descendants gerapporteerd.

`contains` controleert een exact woord, terwijl `startsWith` en
`countWithPrefix` een prefix onderzoeken. Elke node bewaart het aantal terminale
woorden in zijn subtree, zodat prefix-counting niet de volledige tak hoeft te
doorlopen. `wordsWithPrefix` geeft alle matches terug, `prefixesOf` geeft alle
opgeslagen woorden die zelf prefix zijn van een invoertekst en
`longestPrefixOf` kiest daarvan de langste.

De lege string is een geldig woord. `remove` verwijdert één exact woord en
prunet dode nodes. `removePrefix` verwijdert een volledige prefix-subtree en
ruimt eveneens lege vooroudertakken op. Het verzamelen van woorden gebruikt een
iteratieve traversal in plaats van recursie, zodat ook zeer diepe tries door
lange strings geen call-stack overflow veroorzaken.

Iterators werken over de deterministische woordvolgorde en zijn fail-fast bij
membershipwijzigingen. `copy()` maakt een onafhankelijke shallow kopie met
dezelfde woorden en traversalvolgorde.

## RadixTree

`RadixTree` is de tweede structuur van Phase 5 (String Structures) en gebruikt
dezelfde publieke prefix-semantiek als `Trie`. Het verschil zit in de opslag:
een edge bevat een volledige niet-vertakkende substring in plaats van exact één
code-unit. Een enkel lang woord kan daardoor in één radix-node worden opgeslagen,
terwijl de klassieke trie voor elke code-unit een afzonderlijke node nodig heeft.

Bij `add` wordt een bestaande compressed edge gesplitst zodra het nieuwe woord
slechts een gedeeltelijke prefix deelt. Wanneer een verwijderd woord een interne
node niet langer terminal maakt en die node nog maar één child heeft, worden beide
edges opnieuw samengevoegd. `removePrefix` ondersteunt ook prefixes die midden in
een compressed edge eindigen en verwijdert dan de volledige onderliggende subtree.

Elke radix-node bewaart, net zoals bij `Trie`, het aantal terminale woorden in
zijn subtree. Daardoor blijven `startsWith` en `countWithPrefix` afhankelijk van
de lengte van het gezochte pad in plaats van van het aantal opgeslagen woorden.
`wordsWithPrefix`, `prefixesOf` en `longestPrefixOf` hebben dezelfde
betekenis als bij `Trie`. `nodeCount()` maakt de structurele compressie
observeerbaar zonder interne nodes publiek bloot te stellen.

Traversal is iteratief en deterministisch: een opgeslagen prefix verschijnt vóór
langere descendants; sibling-branches volgen de oorspronkelijke edge-invoegvolgorde.
Iterators zijn fail-fast bij membershipwijzigingen en `copy()` maakt een
onafhankelijke kopie met dezelfde woord- en compressed-tree-semantiek.

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

Bij `AVLTree` blijft h door rotations O(log n). `add`, `contains`, `remove`,
`minimum`/`maximum` en ordered navigation zijn daardoor worst-case O(log n).
`height()` is O(1) omdat elke node zijn subtree-height bijhoudt. Volledige traversals
en kopiëren zijn O(n); een insertion of deletion gebruikt O(log n) padwerk en O(1)
rotations per ongebalanceerde ancestor.

Bij `Graph` zijn vertex- en adjacency-lookups gemiddeld O(1) door hashing.
`addEdge`, `removeEdge` en `containsEdge` zijn gemiddeld O(1); `inDegree` van een
directed vertex is O(V + E) in de huidige adjacency-out representatie. BFS, DFS,
cycle detection, connected components en topological sort zijn O(V + E).
`shortestPath` is eveneens O(V + E) en gebruikt O(V) extra traversal-state.

Bij `DisjointSet` zijn `add` en membership-lookups gemiddeld O(1) door hashing.
Door union-by-size en path compression hebben `find`, `union`, `connected` en
`componentSize` een geamortiseerde kost van O(α(n)), praktisch bijna constant.
`components`, `representatives` en kopiëren zijn O(n). De opslag is O(n).

Bij `Trie` kosten exact lookup, toevoegen en verwijderen O(L), waarbij L de
stringlengte is. Prefix lookup en `countWithPrefix` kosten O(P) voor een prefix
van lengte P. `wordsWithPrefix` kost O(P + R), waarbij R de omvang is van de
geretourneerde subtree-output. `removePrefix` kost O(P) plus het vrijgeven van de
losgekoppelde subtree door de garbage collector. De opslag is O(C), met C het
aantal opgeslagen trie-nodes/code-units.

Bij `RadixTree` blijven exact lookup, insertie en removal O(L) in het aantal
vergeleken code-units. Prefix lookup en `countWithPrefix` zijn O(P).
Path compression vermindert het aantal node-objecten voor gedeelde,
niet-vertakkende paden, maar de totale opgeslagen labeltekst blijft O(C).
`nodeCount()`, volledige traversal en kopiëren zijn O(N + output), waarbij N het
aantal compressed nodes is.

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
alle vier traversals, comparatorgedrag en degeneratie. Voor AVL worden alle vier
rotationfamilies, deletion-rebalancing, gesorteerde invoer, logarithmische
hoogtegrenzen en 30.000 randomized differential operations tegen `TreeSet`
gecontroleerd. Daarmee is Phase 3 (Trees) afgerond. De Graph-suite controleert
directed/undirected edge-semantiek, self-loops, degrees, vertex-removal, BFS/DFS,
shortest paths, connected components, cycles, topological sorting, fail-fast
traversal en twee 20.000-step randomized differential runs tegen een
`LinkedHashMap`/`LinkedHashSet` referentiemodel. De DisjointSet-suite controleert
union-by-size, representatives, componentgroottes, kopieën, iteratorgedrag en
30.000 randomized differential operations tegen een onafhankelijk
`LinkedHashMap`-referentiemodel. Daarmee is Phase 4 afgerond. De Trie-suite
controleert empty-string-semantiek, deterministische prefixtraversal, subtree
counts, pruning, bulk-prefixverwijdering, lange niet-recursieve traversals en
25.000 randomized differential operations tegen een `LinkedHashSet`-referentie.
De RadixTree-suite controleert edge-splits, recompressie, prefixes die midden in
een compressed edge eindigen, zeer lange gedeelde prefixes en 30.000 randomized
differential operations tegen een onafhankelijke `LinkedHashSet`-referentie.
De suites controleren
ook linked-list pointerinvarianten, negatieve indices, reverse traversal en
randomized differential tests tegen `java.util.LinkedList`, LIFO-gedrag,
stackgroei en nullwaarden, FIFO-gedrag en queue-wraparound,
circulaire deque-wraparound, groei/trimmen,
comparatoren, heapvolgorde, duplicaten en de lege-structuurcontracten.
