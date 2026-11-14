import java.io.IOException;
import java.nio.file.*;
import java.util.Scanner;

public class Atm {
    static final class InsufficientFunds extends Exception {
        InsufficientFunds() { super("Withdrawal exceeds balance"); }
    }

    static void withdraw(Scanner input, Path card) throws IOException, InsufficientFunds {
        int accountOnCard = Integer.parseInt(Files.readString(card).trim());
        System.out.println("Enter account number and withdrawal amount:");
        if (!input.hasNext()) throw new IllegalArgumentException("Account is required");
        int account = Integer.parseInt(input.next());
        if (!input.hasNext()) throw new IllegalArgumentException("Amount is required");
        int amount = Integer.parseInt(input.next());
        if (account != accountOnCard) throw new IllegalArgumentException("Account does not match card");
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        int balance = 5000;
        if (amount > balance) throw new InsufficientFunds();
        System.out.printf("Dispensed %d. Remaining balance: %d%n", amount, balance - amount);
    }

    public static void main(String[] args) throws IOException, InsufficientFunds {
        Path card = Path.of(args.length == 0 ? "card.txt" : args[0]);
        try (Scanner input = new Scanner(System.in)) {
            if (args.length == 2 && args[1].equals("uncaught")) {
                try { withdraw(input, card); }
                finally { System.out.println("finally: session closed"); }
                return;
            }
            try {
                withdraw(input, card);
            } catch (NumberFormatException e) {
                System.out.println("NumberFormatException: enter whole numbers in integer range");
            } catch (IllegalArgumentException e) {
                System.out.println("IllegalArgumentException: " + e.getMessage());
            } catch (IOException | InsufficientFunds e) {
                System.out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                System.out.println("finally: session closed");
            }
        }
    }
}
