import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;

public class Coordination {
    static class Wallet {
        final int id;
        int balance = 100;
        Wallet(int id) { this.id = id; }
    }

    static void badTransfer(Wallet from, Wallet to, CountDownLatch locked) {
        synchronized (from) {
            locked.countDown();
            try { locked.await(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            synchronized (to) { from.balance--; to.balance++; }
        }
    }

    static void transfer(Wallet from, Wallet to) {
        Wallet first = from.id < to.id ? from : to;
        Wallet second = first == from ? to : from;
        synchronized (first) {
            synchronized (second) { from.balance--; to.balance++; }
        }
    }

    static class Counter {
        final Queue<Integer> tokens = new ArrayDeque<>();
        final List<Integer> sizes = new ArrayList<>(List.of(0));
        final boolean useNotify;
        Counter(boolean useNotify) { this.useNotify = useNotify; }

        synchronized void put(int token) throws InterruptedException {
            while (tokens.size() == 5) wait();
            tokens.add(token);
            sizes.add(tokens.size());
            signal();
        }

        synchronized int take() throws InterruptedException {
            while (tokens.isEmpty()) wait();
            int token = tokens.remove();
            sizes.add(tokens.size());
            signal();
            return token;
        }

        void signal() {
            if (useNotify) notify(); else notifyAll();
        }
    }

    static void counterTrial(boolean useNotify) throws Exception {
        Counter counter = new Counter(useNotify);
        Set<Integer> delivered = ConcurrentHashMap.newKeySet();
        // Negative tokens let each consumer exit after the producer finishes.
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 3; i++) {
                jobs.add(pool.submit(() -> {
                    for (int token; (token = counter.take()) > 0; ) {
                        if (!delivered.add(token)) throw new AssertionError("Duplicate token");
                    }
                    return null;
                }));
            }
            jobs.add(pool.submit(() -> {
                for (int i = 1; i <= 20; i++) counter.put(i);
                for (int i = 0; i < 3; i++) counter.put(-1);
                return null;
            }));
            String outcome = "completed";
            try {
                for (Future<?> job : jobs) job.get(2, TimeUnit.SECONDS);
                if (delivered.size() != 20 || !counter.tokens.isEmpty()) {
                    throw new AssertionError("Token count mismatch");
                }
            } catch (TimeoutException e) {
                outcome = "timed out";
                jobs.forEach(job -> job.cancel(true));
            }
            synchronized (counter) {
                System.out.println((useNotify ? "notify" : "notifyAll") + ": " + outcome
                        + ", delivered=" + delivered.size() + ", sizes=" + counter.sizes);
                if (!useNotify && outcome.equals("timed out")) throw new AssertionError(outcome);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Wallet left = new Wallet(1), right = new Wallet(2);
        CountDownLatch bothLocked = new CountDownLatch(2);
        Thread first = new Thread(() -> badTransfer(left, right, bothLocked), "wallet-1-to-2");
        Thread second = new Thread(() -> badTransfer(right, left, bothLocked), "wallet-2-to-1");
        first.setDaemon(true);
        second.setDaemon(true);
        first.start();
        second.start();
        var bean = ManagementFactory.getThreadMXBean();
        long[] deadlocked = null;
        for (int attempts = 0; deadlocked == null && attempts < 100; attempts++) {
            Thread.sleep(10);
            deadlocked = bean.findMonitorDeadlockedThreads();
        }
        if (deadlocked == null) throw new AssertionError("Deadlock did not reproduce");
        for (var info : bean.getThreadInfo(deadlocked)) {
            System.out.printf("%s %s waiting for %s%n",
                    info.getThreadName(), info.getThreadState(), info.getLockOwnerName());
        }

        Wallet a = new Wallet(1), b = new Wallet(2);
        Thread x = new Thread(() -> transfer(a, b)), y = new Thread(() -> transfer(b, a));
        x.start();
        y.start();
        x.join();
        y.join();
        System.out.printf("Ordered transfers complete. Balances: %d and %d%n", a.balance, b.balance);
        counterTrial(false);
        for (int run = 0; run < 3; run++) counterTrial(true);
    }
}
