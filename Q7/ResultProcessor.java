import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class ResultProcessor {
    record Result(int id, String status, String detail) {}

    record ScoreJob(int id, Path file) implements Callable<Result> {
        public Result call() {
            try {
                String[] fields = Files.readString(file).strip().split(",", -1);
                if (fields.length != 3) throw new IllegalArgumentException("Expected three marks");
                int sum = 0;
                for (String field : fields) {
                    int mark = Integer.parseInt(field.strip());
                    if (mark < 0 || mark > 100) throw new IllegalArgumentException("Mark out of range");
                    sum += mark;
                }
                return new Result(id, "OK", String.format(Locale.ROOT, "%.2f %s", sum / 3.0,
                        sum >= 150 ? "PASS" : "FAIL"));
            } catch (NoSuchFileException e) {
                return new Result(id, "MISSING", file.getFileName().toString());
            } catch (java.nio.charset.CharacterCodingException | IllegalArgumentException e) {
                return new Result(id, "CORRUPT", e.toString());
            } catch (IOException | SecurityException e) {
                return new Result(id, "UNREADABLE", e.toString());
            }
        }
    }

    static ConcurrentNavigableMap<Integer, Result> process(Path folder, int count, int workers)
            throws InterruptedException {
        ConcurrentNavigableMap<Integer, Result> results = new ConcurrentSkipListMap<>();
        List<Future<Result>> futures = new ArrayList<>(count);
        try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
            for (int id = 1; id <= count; id++) {
                ScoreJob job = new ScoreJob(id, scoreFile(folder, id));
                futures.add(pool.submit(() -> {
                    Result result = job.call();
                    results.put(result.id(), result);
                    return result;
                }));
            }
            try {
                for (int index = 0; index < futures.size(); index++) {
                    int id = index + 1;
                    try {
                        futures.get(index).get();
                    } catch (ExecutionException e) {
                        if (e.getCause() instanceof Error fatal) throw fatal;
                        results.put(id, new Result(id, "INTERNAL", e.getCause().toString()));
                    }
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                throw e;
            }
        }
        if (results.size() != count) throw new IllegalStateException("Incomplete result set");
        return results;
    }

    static Path scoreFile(Path folder, int id) {
        return folder.resolve(id + ".txt");
    }

    static void generate(Path folder, int count) throws IOException {
        Files.createDirectory(folder);
        for (int id = 1; id <= count; id++) {
            if (id % 100 == 0) continue;
            Files.writeString(scoreFile(folder, id), id % 100 == 1 ? "75,broken,90" : "65,75,85");
        }
    }

    static Map<String, Integer> tally(Collection<Result> results) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Result result : results) counts.merge(result.status, 1, Integer::sum);
        return counts;
    }

    public static void main(String[] args) throws Exception {
        var small = process(Path.of("sample"), 5, 2);
        small.values().forEach(System.out::println);
        var smallCounts = tally(small.values());
        System.out.println("Hand-checked set: " + smallCounts);
        if (!smallCounts.equals(Map.of("OK", 2, "MISSING", 1, "CORRUPT", 2))
                || !small.get(1).detail.equals("70.00 PASS") || !small.get(2).detail.equals("40.00 FAIL")
                || !small.get(4).status.equals("MISSING")) throw new AssertionError(small);

        Path parent = Files.createTempDirectory("semester-results-");
        Path folder = parent.resolve("scores");
        try {
            generate(folder, 10_000);
            System.out.println("Trial | Threads | Time (ms) | Summary");
            int[] sizes = {1, 2, 4, 8};
            process(folder, 10_000, 4);
            for (int trial = 0; trial < sizes.length; trial++) {
                for (int offset = 0; offset < sizes.length; offset++) {
                    int workers = sizes[(offset + trial) % sizes.length];
                    long begin = System.nanoTime();
                    var counts = tally(process(folder, 10_000, workers).values());
                    double millis = (System.nanoTime() - begin) / 1_000_000.0;
                    if (!counts.equals(Map.of("OK", 9800, "MISSING", 100, "CORRUPT", 100))) {
                        throw new AssertionError(counts);
                    }
                    System.out.printf(Locale.ROOT, "%5d | %7d | %9.3f | %s%n",
                            trial + 1, workers, millis, counts);
                }
            }
        } finally {
            if (Files.isDirectory(folder)) {
                try (var files = Files.newDirectoryStream(folder)) {
                    for (Path file : files) Files.delete(file);
                }
                Files.delete(folder);
            }
            Files.delete(parent);
        }
    }
}
