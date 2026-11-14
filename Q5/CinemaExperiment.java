import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class CinemaExperiment {
    enum Mode { UNSAFE, METHOD, BLOCK }

    static class Screen {
        final boolean[] booked;
        final Object lock = new Object();

        Screen(int seats) { booked = new boolean[seats]; }

        boolean reserve(int seat) {
            if (booked[seat]) return false;
            booked[seat] = true;
            return true;
        }

        synchronized boolean reserveMethod(int seat) { return reserve(seat); }

        boolean reserveBlock(int seat) {
            synchronized (lock) { return reserve(seat); }
        }

        boolean book(int seat, Mode mode) {
            return switch (mode) {
                case UNSAFE -> reserve(seat);
                case METHOD -> reserveMethod(seat);
                case BLOCK -> reserveBlock(seat);
            };
        }
    }

    static void reproduce() throws InterruptedException {
        for (int run = 1; run <= 10; run++) {
            Screen screen = new Screen(1);
            CyclicBarrier bothRead = new CyclicBarrier(2);
            AtomicIntegerArray approvals = new AtomicIntegerArray(1);
            Runnable customer = () -> {
                boolean free = !screen.booked[0];
                try { bothRead.await(5, TimeUnit.SECONDS); }
                catch (Exception e) { throw new AssertionError(e); }
                if (free) {
                    screen.booked[0] = true;
                    approvals.incrementAndGet(0);
                }
            };
            Thread first = new Thread(customer), second = new Thread(customer);
            first.start();
            second.start();
            first.join();
            second.join();
            if (approvals.get(0) != 2) throw new AssertionError("Race did not reproduce");
            System.out.printf("Forced race %02d: approvals=%d, double bookings=%d%n",
                    run, approvals.get(0), approvals.get(0) - 1);
        }
    }

    static void measure(ExecutorService pool, Mode mode, int trial) throws Exception {
        int attempts = 10_000, workers = 8, seats = 1_000;
        Screen screen = new Screen(seats);
        AtomicIntegerArray approvals = new AtomicIntegerArray(seats);
        CountDownLatch ready = new CountDownLatch(workers), start = new CountDownLatch(1);
        var tasks = new java.util.ArrayList<Future<?>>();
        for (int w = 0; w < workers; w++) {
            final int worker = w;
            tasks.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                for (int i = 0; i < attempts / workers; i++) {
                    int seat = (i + worker * 13) % seats;
                    if (screen.book(seat, mode)) approvals.incrementAndGet(seat);
                }
                return null;
            }));
        }
        ready.await();
        long begin = System.nanoTime();
        start.countDown();
        for (Future<?> task : tasks) task.get();
        double ms = (System.nanoTime() - begin) / 1_000_000.0;
        int extras = 0, unique = 0;
        for (int i = 0; i < seats; i++) {
            int count = approvals.get(i);
            if (count > 0) unique++;
            extras += Math.max(0, count - 1);
        }
        if (unique != seats || (mode != Mode.UNSAFE && extras != 0)) throw new AssertionError(mode);
        if (trial > 0) System.out.printf(java.util.Locale.ROOT,
                "%d %-6s %8.3f ms  unique=%d  double bookings=%d%n", trial, mode, ms, unique, extras);
    }

    public static void main(String[] args) throws Exception {
        reproduce();
        System.out.println("10,000 attempts per trial with 8 workers");
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int trial = 0; trial <= 5; trial++) {
                for (int offset = 0; offset < Mode.values().length; offset++) {
                    measure(pool, Mode.values()[(trial + offset) % Mode.values().length], trial);
                }
            }
        }
    }
}
