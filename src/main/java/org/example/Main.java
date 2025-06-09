package org.example;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;
import java.util.regex.Pattern;

import static org.example.DatabaseHandler.balance;

public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private static final DatabaseHandler db = new DatabaseHandler();
    private static String currentUserEmail;

    public static void main(String[] args) {
        while (true) {
            System.out.println("\n== Ledger System ==");
            System.out.println("Login or Register:");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.print("> ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    loginUser();
                    break;
                case "2":
                    registerUser();
                    break;
                case "3":
                    
                default:
                    System.out.println("Invalid choice.\n");
            }
        }
    }

    private static void registerUser() {
        System.out.println("\n== Please fill in the form ==");

        System.out.print("Name: "); // Renamed to match DB column
        String name = scanner.nextLine();

        String email;
        while (true) {
            System.out.print("Email: ");
            email = scanner.nextLine();
            if (isValidEmail(email)) break;
            System.out.println("Invalid email format. Please try again.");
        }

        String password;
        while (true) {
            System.out.print("Password: ");
            password = scanner.nextLine();

            System.out.print("Confirm Password: ");
            String confirmPassword = scanner.nextLine();

            if (!password.equals(confirmPassword)) {
                System.out.println("Passwords do not match. Try again.");
            } else {
                break;
            }
        }

        if (db.userExists(email)) {
            System.out.println("Email already registered!\n");
        } else {
            db.insertUser(name, email, password); // Use "name" not "username"
            System.out.println("\nRegister Successful!!!\n");
        }
    }

    private static void loginUser() {
        System.out.println("\n== Please enter your email and password ==");

        String email;
        while (true) {
            System.out.print("Email: ");
            email = scanner.nextLine();
            if (isValidEmail(email)) break;
            System.out.println("Invalid email format. Please try again.");
        }

        System.out.print("Password: ");
        String password = scanner.nextLine();

        if (!db.userExists(email)) {
            System.out.println("Email not registered!\n");
        } else if (db.validateUser(email, password)) {
            System.out.println("\nLogin Successful!!!\n");
            currentUserEmail = email;
            db.checkLoanReminders(email);
            transactionMenu();
        } else {
            System.out.println("Incorrect password!\n");
        }
    }

    private static boolean isValidEmail(String email) {
        String regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return Pattern.matches(regex, email);
    }

    public static void transactionMenu() {
        try {
            Scanner input = new Scanner(System.in);
            int choice;

            while (true) {
                System.out.println("\n==Transaction Menu==");
                System.out.println("== Transaction ==");
                System.out.println("1.Debit");
                System.out.println("2.Credit");
                System.out.println("3.History Menu");
                System.out.println("4.Savings");
                System.out.println("5.Credit Loan");
                System.out.println("6.Deposit Interest Predictor");
                System.out.println("7.Logout");
                System.out.print("> ");
                choice = input.nextInt();
                input.nextLine();

                switch (choice) {
                    case 1 -> handleDebit(input);
                    case 2 -> handleCredit(input);
                    case 3 -> {
                        System.out.println("\n== History Menu ==");
                        System.out.println("1.View Transaction History");
                        System.out.println("2.Filter and Sort");
                        System.out.println("3.Export to CSV");
                        System.out.print("> ");
                        int historyChoice = scanner.nextInt();
                        scanner.nextLine();

                        switch (historyChoice) {
                            case 1 -> db.showHistory();
                            case 2 -> filterHistory();
                            case 3 -> db.exportToCSV(currentUserEmail);
                            default -> System.out.println("Invalid.");
                        }
                    }
                    case 4 -> savings();
                    case 5 -> creditLoan();
                    case 6 -> depositInterestPredictor();
                    case 7 -> {
                        System.out.println("Logging out...");
                        db.disconnectDatabase();
                        return;
                    }
                    default -> System.out.println("Feature under development or invalid choice.");
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error occurred.");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred.");
            e.printStackTrace();
        }
    }

    public static void handleDebit(Scanner input) {
        System.out.println("==Debit==");
        System.out.print("Enter Debit Amount: ");
        double amount = input.nextDouble();
        input.nextLine();
        System.out.print("Enter description: ");
        String desc = input.nextLine();

        if (amount <= 0 || amount > 1000000 || desc.length() > 100) {
            System.out.println("Invalid input.");
            return;
        }

        if (amount > DatabaseHandler.balance) {
            System.out.println("Insufficient balance for this debit.");
            return;
        }

        balance -= amount;
        DatabaseHandler.balance -= amount;
        System.out.println("Debit successfully recorded! Current balance: " + DatabaseHandler.balance);

    }

    public static void handleCredit(Scanner input) {
        System.out.println("==Credit==");
        System.out.print("Enter Credit Amount: ");
        double amount = input.nextDouble();
        input.nextLine();
        System.out.print("Enter description: ");
        String desc = input.nextLine();

        if (amount <= 0 || desc.length() > 100) {
            System.out.println("Invalid input.");
            return;
        }
        DatabaseHandler.balance += amount;
        System.out.println("Credit successfully recorded! Current balance: " + DatabaseHandler.balance);
    }

    public static void savings() {
        System.out.println("\n== Savings ==");

        System.out.print("Are you sure you want to activate it? (Y/N) : ");
        String answer = scanner.nextLine().trim().toUpperCase();

        if (!answer.equals("Y")) {
            System.out.println("Savings activation cancelled.");
            return;
        }

        System.out.print("Please enter the percentage you wish to deduct from the next debit: ");
        String percInput = scanner.nextLine().trim();

        double percentage;
        try {
            percentage = Double.parseDouble(percInput);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a valid number.");
            return;
        }

        if (percentage <= 0 || percentage >= 100) {
            System.out.println("Please enter a valid percentage between 0 and 100.");
            return;
        }

        // Call the DatabaseHandler's method that does insert/update
        db.activateSavings(currentUserEmail, percentage);
    }

    public static void creditLoan() {
    System.out.println("\n== Credit Loan ==");
    System.out.println("1. Apply for Loan");
    System.out.println("2. Make Loan Payment");
    System.out.print("> ");
    int choice = scanner.nextInt();
    scanner.nextLine(); // consume newline

    switch (choice) {
        case 1 -> applyForLoan();
        case 2 -> makeLoanPayment();
        default -> System.out.println("Invalid choice.");
    }
}

    private static void applyForLoan() {
        System.out.println("\n== Apply for Loan ==");
        
        // Get user ID first
        int userId = db.getUserId(currentUserEmail);
        if (userId == -1) {
            System.out.println("Error: Could not find user ID");
            return;
        }

        System.out.print("Enter principal amount: ");
        double principal = scanner.nextDouble();
        scanner.nextLine();

        System.out.print("Enter interest rate (e.g. 0.05 for 5%): ");
        double interestRate = scanner.nextDouble();
        scanner.nextLine();

        System.out.print("Enter repayment period in months: ");
        int period = scanner.nextInt();
        scanner.nextLine();

        db.applyLoan(scanner, userId); // Now passing correct parameter types
    }

    private static void makeLoanPayment() {
        // Get user ID from email
        int userId = db.getUserId(currentUserEmail);
        if (userId == -1) {
            System.out.println("Error: User not found.");
            return;
        }

        System.out.println("\n== Make Loan Payment ==");
        db.repayLoan(scanner, userId);
    }

    public static void depositInterestPredictor() {
        System.out.println("==Deposit Interest Predictor==");
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter deposit amount: ");
        double deposit = scanner.nextDouble();

        if (deposit <= 0) {
            System.out.println("Deposit amount must be positive.");
            return;
        }

        System.out.println("Choose Bank: ");
        System.out.println("1. RHB (2.6%)");
        System.out.println("2. Maybank (2.5%)");
        System.out.println("3. Hong Leong (2.3%)");
        System.out.println("4. Alliance (2.85%)");
        System.out.println("5. AmBank (2.55%)");
        System.out.println("6. Standard Chartered (2.65%)");
        int bank = scanner.nextInt();
        double rate;

        switch (bank) {
            case 1:
                rate = 2.6;
                break;
            case 2:
                rate = 2.5;
                break;
            case 3:
                rate = 2.3;
                break;
            case 4:
                rate = 2.85;
                break;
            case 5:
                rate = 2.55;
                break;
            case 6:
                rate = 2.65;
                break;
            default:
                System.out.println("Invalid");
                return;
        }

        double interestRate = (deposit * rate) / 12 / 100;
        System.out.printf("Monthly interest earned: %.2f\n", interestRate);
    }

    static void filterHistory() throws SQLException {
        try {
            StringBuilder query = new StringBuilder("SELECT * FROM transactions WHERE 1=1");
            boolean hasFilter = false;

            // Filter: Date Range
            System.out.print("Filter by date range? (Y/N): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("Y")) {
                System.out.print("Start date (YYYY-MM-DD): ");
                String startDate = scanner.nextLine().trim();
                System.out.print("End date (YYYY-MM-DD): ");
                String endDate = scanner.nextLine().trim();
                query.append(" AND DATE(timestamp) BETWEEN '").append(startDate).append("' AND '").append(endDate).append("'");
                hasFilter = true;
            }

            // Filter: Transaction Type
            System.out.print("Filter by transaction type (debit/credit)? (Y/N): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("Y")) {
                System.out.print("Enter type (Debit/Credit): ");
                String type = scanner.nextLine().trim();
                query.append(" AND type = '").append(type).append("'");
                hasFilter = true;
            }

            // Filter: Amount Range
            System.out.print("Filter by amount range? (Y/N): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("Y")) {
                System.out.print("Minimum amount: ");
                double min = Double.parseDouble(scanner.nextLine().trim());
                System.out.print("Maximum amount: ");
                double max = Double.parseDouble(scanner.nextLine().trim());
                query.append(" AND amount BETWEEN ").append(min).append(" AND ").append(max);
                hasFilter = true;
            }

            // Sorting
            System.out.print("Sort results? (Y/N): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("Y")) {
                System.out.print("Sort by (date/amount): ");
                String field = scanner.nextLine().trim().toLowerCase();
                System.out.print("Order (asc/desc): ");
                String order = scanner.nextLine().trim().toUpperCase();
                if (field.equals("date")) {
                    query.append(" ORDER BY timestamp ").append(order);
                } else if (field.equals("amount")) {
                    query.append(" ORDER BY amount ").append(order);
                }
            }

            Statement stmt = DatabaseHandler.conn.createStatement();
            ResultSet rs = stmt.executeQuery(query.toString());

            System.out.println("ID | Type   | Amount  | Description | Date");
            System.out.println("------------------------------------------------------------");
            while (rs.next()) {
                System.out.printf("%-2d | %-6s | %7.2f | %-12s | %s%n",
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getDouble("amount"),
                        rs.getString("description"),
                        rs.getString("timestamp"));
            }

            rs.close();
            stmt.close();
        } catch (Exception e) {
            System.out.println("Error retrieving filtered history: " + e.getMessage());
            e.printStackTrace();
        }
    }
}




