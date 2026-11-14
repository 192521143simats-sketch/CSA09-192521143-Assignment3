import java.io.IOException;

public class ExamPortal {
    static class WindowClosed extends ExamException {
        WindowClosed() { super("The exam window is closed."); }
    }
    static class NotRegistered extends ExamException {
        NotRegistered() { super("You are not registered for this course."); }
    }
    static class FileTooLarge extends ExamException {
        FileTooLarge() { super("The answer file exceeds 500 KB."); }
    }

    static void store(boolean available, boolean keepCause) throws SubmissionStorageException {
        try {
            if (!available) throw new IOException("Storage volume unavailable");
        } catch (IOException cause) {
            throw new SubmissionStorageException("Could not save your answer. Please retry.",
                    keepCause ? cause : null);
        }
    }

    static void submit(boolean registered, long size, boolean available, boolean keepCause)
            throws ExamException {
        if (!registered) throw new NotRegistered();
        if (size > 500) throw new FileTooLarge();
        store(available, keepCause);
    }

    static void handle(String scenario, boolean open, boolean registered, long size,
                       boolean available, boolean keepCause) {
        try {
            if (!open) throw new WindowClosed();
            submit(registered, size, available, keepCause);
            System.out.println(scenario + ": submission accepted");
        } catch (ExamException e) {
            System.out.println(scenario + ": " + e.getMessage());
            if (e instanceof SubmissionStorageException) {
                System.out.println("getCause(): " + e.getCause());
                e.printStackTrace(System.out);
            }
        }
    }

    public static void main(String[] args) {
        handle("Valid", true, true, 120, true, true);
        handle("Closed", false, true, 120, true, true);
        handle("Unregistered", true, false, 120, true, true);
        handle("Oversized", true, true, 501, true, true);
        handle("Chained failure", true, true, 120, false, true);
        handle("Cause discarded", true, true, 120, false, false);
    }
}
