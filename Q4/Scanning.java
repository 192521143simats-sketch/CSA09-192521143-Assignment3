import java.util.*;
import java.util.concurrent.*;

public class Scanning {
    static final int[] BATCH = {12, 15, 11, 14};
    static final int[] PRIORITY = {1, 3, 7, 10};

    static class Station implements Runnable {
        final int id;
        final Queue<Integer> finished;
        int scanned;

        Station(int id, Queue<Integer> finished) {
            this.id = id;
            this.finished = finished;
        }

        public void run() {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(5, 26));
                scanned = BATCH[id - 1];
                finished.add(id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static class ScannerThread extends Thread {
        final Station station;
        ScannerThread(Station station) { this.station = station; }
        @Override public void run() { station.run(); }
    }

    static void trial(int run, boolean subclass, boolean priorities) throws InterruptedException {
        Queue<Integer> order = new ConcurrentLinkedQueue<>();
        Station[] stations = new Station[4];
        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            stations[i] = new Station(i + 1, order);
            threads[i] = subclass ? new ScannerThread(stations[i]) : new Thread(stations[i]);
            if (priorities) threads[i].setPriority(PRIORITY[i]);
        }
        Thread.State before = threads[0].getState();
        for (Thread thread : threads) thread.start();
        boolean aliveAfterStart = threads[0].isAlive();
        for (Thread thread : threads) thread.join();
        int total = Arrays.stream(stations).mapToInt(s -> s.scanned).sum();
        if (total != 52 || order.size() != 4) throw new AssertionError("Incomplete scan");
        System.out.printf("%02d %-8s %-8s %s total=%d%n", run, subclass ? "Thread" : "Runnable",
                priorities ? "varied" : "equal", order, total);
        if (run == 1 && subclass) {
            System.out.println("Station 1: before=" + before + ", alive after start="
                    + aliveAfterStart + ", after join=" + threads[0].getState());
            System.out.println("Station script counts: " + Arrays.toString(BATCH));
        }
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length == 1 && args[0].equals("daemon")) {
            CountDownLatch started = new CountDownLatch(1);
            Thread station = new Thread(() -> {
                System.out.println("Daemon station started");
                started.countDown();
                try {
                    Thread.sleep(30_000);
                    System.out.println("Daemon station completed");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, "daemon-station");
            station.setDaemon(true);
            station.start();
            started.await();
            System.out.println("main ends while daemon is alive: " + station.isAlive());
            return;
        }
        Thread probe = new Thread(() -> System.out.println("Executed on " +
                Thread.currentThread().getName()), "station-probe");
        probe.run();
        System.out.println("After direct run(): " + probe.getState() + ", alive=" + probe.isAlive());
        probe.start();
        probe.join();
        for (int run = 1; run <= 10; run++) {
            trial(run, true, false);
            trial(run, false, false);
            trial(run, false, true);
        }
    }
}
