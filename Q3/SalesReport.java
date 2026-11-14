import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SalesReport {
    static final List<TrackedReader> openReaders = new ArrayList<>();
    static int openWriters;

    static class TrackedReader extends BufferedReader {
        TrackedReader(Path file) throws IOException {
            super(Files.newBufferedReader(file));
            openReaders.add(this);
        }
        @Override public void close() throws IOException {
            super.close();
            openReaders.remove(this);
        }
    }

    static class TrackedWriter extends BufferedWriter {
        TrackedWriter(Path file) throws IOException {
            super(Files.newBufferedWriter(file));
            openWriters++;
        }
        @Override public void close() throws IOException {
            super.close();
            openWriters--;
        }
    }

    static long total(BufferedReader reader, Path file) throws IOException {
        long sum = 0;
        int lineNumber = 0;
        for (String line; (line = reader.readLine()) != null; ) {
            lineNumber++;
            try {
                long value = Long.parseLong(line.strip());
                if (value < 0) throw new NumberFormatException("Negative sale");
                sum = Math.addExact(sum, value);
            } catch (NumberFormatException | ArithmeticException e) {
                throw new IOException(file + ":" + lineNumber + " invalid sale: " + line, e);
            }
        }
        return sum;
    }

    static void row(BufferedWriter writer, Path file, long sum) throws IOException {
        writer.write(file.getFileName() + "," + sum + "\n");
    }

    static void manual(List<Path> files, Path output) throws IOException {
        TrackedWriter writer = null;
        try {
            writer = new TrackedWriter(output);
            for (Path file : files) {
                TrackedReader reader = new TrackedReader(file);
                row(writer, file, total(reader, file));
                reader.close();
            }
        } catch (IOException e) {
            System.out.println("Manual log: " + e.getMessage());
            throw e;
        } finally {
            if (writer != null) writer.close();
        }
    }

    static void automatic(List<Path> files, Path output) throws IOException {
        try (TrackedWriter writer = new TrackedWriter(output)) {
            for (Path file : files) {
                try (TrackedReader reader = new TrackedReader(file)) {
                    row(writer, file, total(reader, file));
                }
            }
        }
    }

    static void failure(boolean manual, Path output) throws IOException {
        List<Path> files = List.of(Path.of("north.txt"), Path.of("broken.txt"));
        try {
            if (manual) manual(files, output); else automatic(files, output);
            throw new AssertionError("Bad row accepted");
        } catch (IOException e) {
            String mode = manual ? "Manual" : "Try-with-resources";
            if (!manual) System.out.println(mode + " log: " + e.getMessage());
            System.out.printf("%s after failure: %d open reader(s), %d open writer(s)%n",
                    mode, openReaders.size(), openWriters);
            if (openReaders.size() != (manual ? 1 : 0) || openWriters != 0) {
                throw new AssertionError("Unexpected handle count");
            }
        } finally {
            for (TrackedReader reader : List.copyOf(openReaders)) reader.close();
        }
    }

    public static void main(String[] args) throws IOException {
        Path folder = Files.createTempDirectory("sales-report-");
        Path first = folder.resolve("manual.csv");
        Path second = folder.resolve("automatic.csv");
        try {
            List<Path> good = List.of(Path.of("north.txt"), Path.of("south.txt"));
            manual(good, first);
            automatic(good, second);
            if (Files.mismatch(first, second) != -1) throw new AssertionError("Reports differ");
            System.out.println("Reports match byte-for-byte:");
            System.out.print(Files.readString(second));
            failure(true, first);
            failure(false, second);
        } finally {
            Files.deleteIfExists(first);
            Files.deleteIfExists(second);
            Files.delete(folder);
        }
    }
}
