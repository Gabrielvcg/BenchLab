import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Dependency-free teaching harness; not a replacement for JMH. */
public final class TopKLab {
    record Item(long id, int score) {}
    static final Comparator<Item> BEST = Comparator.comparingInt(Item::score).reversed().thenComparingLong(Item::id);
    static volatile long sink;

    static int limit(List<Item> input, int k) {
        if (k < 0) throw new IllegalArgumentException("k must be nonnegative");
        return Math.min(k, input.size());
    }
    static List<Item> fullSort(List<Item> input, int k) {
        int size = limit(input, k);
        if (size == 0) return List.of();
        var copy = new ArrayList<>(input);
        copy.sort(BEST);
        return new ArrayList<>(copy.subList(0, size));
    }
    static List<Item> boundedHeap(List<Item> input, int k) {
        int size = limit(input, k);
        if (size == 0) return List.of();
        var heap = new PriorityQueue<Item>(size, BEST.reversed());
        for (Item item : input) {
            if (heap.size() < size) heap.offer(item);
            else if (BEST.compare(item, heap.peek()) < 0) {
                heap.poll();
                heap.offer(item);
            }
        }
        var result = new ArrayList<>(heap);
        result.sort(BEST);
        return result;
    }
    // Independent rank-counting oracle, including equal records as a multiset.
    // Deliberately O(n^2), used only for small functional tests.
    static List<Item> oracle(List<Item> input, int k) {
        int size = limit(input, k);
        var selected = new ArrayList<Item>();
        for (int i = 0; i < input.size(); i++) {
            Item item = input.get(i);
            int rank = 0;
            for (int j = 0; j < input.size(); j++) {
                Item other = input.get(j);
                if (other.score() > item.score() || (other.score() == item.score() && other.id() < item.id())
                    || (other.equals(item) && j < i)) rank++;
            }
            if (rank < size) selected.add(item);
        }
        selected.sort(BEST);
        return selected;
    }
    // Independent ordered multiset reference for larger campaign inputs.
    static List<Item> reference(List<Item> input, int k) {
        int size = limit(input, k);
        var counts = new TreeMap<Item, Integer>(BEST);
        for (Item item : input) counts.merge(item, 1, Integer::sum);
        var result = new ArrayList<Item>();
        for (var entry : counts.entrySet()) {
            for (int i = 0; i < entry.getValue() && result.size() < size; i++) result.add(entry.getKey());
            if (result.size() == size) break;
        }
        return result;
    }
    static List<Item> dataset(int n, long seed) {
        var random = new Random(seed);
        var input = new ArrayList<Item>(n);
        for (int i = 0; i < n; i++) input.add(new Item(i, random.nextInt(1000)));
        return List.copyOf(input);
    }
    static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Mismatching result: " + expected + " vs " + actual);
    }
    static void verify(List<Item> input, int k, List<Item> expected) {
        var before = new ArrayList<>(input);
        equal(expected, fullSort(input, k));
        equal(expected, boundedHeap(input, k));
        equal(expected, reference(input, k));
        equal(before, input);
    }
    static void tests() {
        var cases = List.of(List.<Item>of(), List.of(new Item(1, 3)),
            List.of(new Item(3, 9), new Item(1, 9), new Item(2, 9), new Item(1, 9), new Item(0, -1)),
            List.of(new Item(Long.MAX_VALUE, Integer.MIN_VALUE), new Item(Long.MIN_VALUE, Integer.MAX_VALUE)));
        for (var input : cases) for (int k : new int[]{0, 1, 2, 10}) verify(input, k, oracle(input, k));
        equal(List.of(new Item(1, 9), new Item(2, 9)), fullSort(List.of(new Item(3, 9), new Item(2, 9), new Item(1, 9)), 2));
        for (long seed = 0; seed < 20; seed++) {
            var input = dataset(31, seed);
            for (int k = 0; k <= 33; k++) verify(input, k, oracle(input, k));
        }
        for (boolean heap : new boolean[]{false, true}) {
            try {
                if (heap) boundedHeap(List.of(), -1); else fullSort(List.of(), -1);
                throw new AssertionError("Negative k accepted");
            } catch (IllegalArgumentException expected) { /* contract */ }
        }
        System.out.println("PASS: deterministic oracle, boundary, duplicate, tie, extreme and immutability tests");
    }
    static long checksum(List<Item> items) {
        long value = 1;
        for (Item item : items) value = 31 * value + item.id() * 17 + item.score();
        return value;
    }
    static void sample(List<Item> input, int k, boolean heap, boolean measured, int repetition,
                       int position, long seed, PrintWriter output, List<Item> expected) {
        long start = System.nanoTime();
        List<Item> result = heap ? boundedHeap(input, k) : fullSort(input, k);
        long nanos = System.nanoTime() - start;
        long hash = checksum(result);
        sink = Long.rotateLeft(sink, 1) ^ hash;
        equal(expected, result); // Outside timed region: ordering alone is never the correctness oracle.
        if (measured) output.printf(Locale.ROOT, "%d,%d,%d,%d,%d,%s,%d,%d%n", input.size(), k, seed,
            repetition, position, heap ? "bounded_heap" : "full_sort", nanos, hash);
    }
    public static void main(String[] args) throws Exception {
        tests();
        if (args.length == 1 && args[0].equals("--test")) return;
        if (args.length != 1) throw new IllegalArgumentException("Usage: TopKLab --test | NEW_OUTPUT_DIRECTORY");
        Path directory = Path.of(args[0]);
        Files.createDirectory(directory); // Refuse accidental overwrite of prior results.
        int warmups = 12, repetitions = 12;
        long seed = 20261023L;
        long campaignStart = System.nanoTime();
        var metadata = new Properties();
        for (String key : List.of("java.version", "java.vendor", "java.vm.name", "java.vm.version", "os.name", "os.version", "os.arch"))
            metadata.setProperty(key, System.getProperty(key));
        metadata.setProperty("availableProcessors", "" + Runtime.getRuntime().availableProcessors());
        metadata.setProperty("cpuDescription", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unavailable; availableProcessors is not a CPU model"));
        metadata.setProperty("jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments().toString());
        metadata.setProperty("maxHeapBytes", "" + Runtime.getRuntime().maxMemory());
        metadata.setProperty("seed", "" + seed);
        metadata.setProperty("warmupPairsPerCase", "" + warmups);
        metadata.setProperty("measurementPairsPerCase", "" + repetitions);
        metadata.setProperty("order", "alternate first strategy by pair and case; same JVM; one operation per sample");
        metadata.setProperty("input", "java.util.Random(seed+n); IDs 0..n-1; scores uniform integers 0..999; reused immutable list");
        metadata.setProperty("comparator", "score descending then id ascending; exact duplicate records retain multiplicity");
        metadata.setProperty("startedAtUTC", Instant.now().toString());
        int caseIndex = 0;
        try (var output = new PrintWriter(Files.newBufferedWriter(directory.resolve("samples.csv")))) {
            output.println("n,k,seed,repetition,position,strategy,nanoseconds,checksum");
            for (int n : new int[]{1000, 10000, 50000}) {
                var input = dataset(n, seed + n);
                for (int k : new int[]{10, n / 2, n}) {
                    var expected = reference(input, k);
                    verify(input, k, expected);
                    for (int pair = -warmups; pair < repetitions; pair++) {
                        if (System.nanoTime() - campaignStart > 25_000_000_000L)
                            throw new IllegalStateException("Campaign budget exceeded; partial output must not be treated as complete");
                        boolean heapFirst = ((pair + warmups + caseIndex) & 1) == 0;
                        sample(input, k, heapFirst, pair >= 0, pair, 0, seed + n, output, expected);
                        sample(input, k, !heapFirst, pair >= 0, pair, 1, seed + n, output, expected);
                    }
                    caseIndex++;
                }
            }
            if (output.checkError()) throw new IOException("Could not write samples");
        }
        metadata.setProperty("campaignElapsedNs", "" + (System.nanoTime() - campaignStart));
        metadata.setProperty("observableSink", "" + sink);
        metadata.setProperty("complete", "true");
        try (var writer = Files.newBufferedWriter(directory.resolve("metadata.properties"))) {
            metadata.store(writer, "Real local top-k campaign; elapsed includes setup, warmups, validation and CSV output");
        }
        System.out.println("COMPLETE: " + directory + "; sink=" + sink);
    }
}
