package org.example;

import org.mindrot.jbcrypt.BCrypt;

import java.io.FileWriter;
import java.sql.*;

public class DatabaseHandler {
    private static final String DB_URL = "jdbc:sqlite:users.db";
    static double balance = 0.0;
    static Connection conn;

    static {
        try {
            conn = DriverManager.getConnection(DB_URL);
            Statement stmt = conn.createStatement();

            String sql = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "email TEXT NOT NULL UNIQUE, " +
                    "password TEXT NOT NULL)";
            stmt.executeUpdate(sql);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,
                    amount REAL NOT NULL,
                    description TEXT NOT NULL,
                    user_email TEXT NOT NULL,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_email) REFERENCES users(email)
                );
                """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS loans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    email TEXT NOT NULL,
                    amount REAL NOT NULL,
                    due_date TEXT NOT NULL,
                    status TEXT NOT NULL
                );
                """);

            ResultSet rs = stmt.executeQuery("""
                SELECT SUM(CASE WHEN type='Credit' THEN amount ELSE -amount END) AS balance 
                FROM transactions
                """);
            if (rs.next()) {
                balance = rs.getDouble("balance");
            }

            System.out.println("Database table created successfully.");
        } catch (SQLException e) {
            System.out.println("Error creating user table: " + e.getMessage());
        }
    }

    public boolean userExists(String email) {
        String sql = "SELECT email FROM users WHERE email = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.out.println("Error checking user: " + e.getMessage());
            return false;
        }
    }

    public void insertUser(String name, String email, String password) {
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt()); // 🔐 Hashing password
        String sql = "INSERT INTO users(name, email, password) VALUES(?,?,?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            pstmt.setString(2, email);
            pstmt.setString(3, hashedPassword);
            pstmt.executeUpdate();
            System.out.println("User inserted successfully.");
        } catch (SQLException e) {
            System.out.println("Error inserting user: " + e.getMessage());
        }
    }

    public boolean validateUser(String email, String password) {
        String sql = "SELECT password FROM users WHERE email = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password");
                return BCrypt.checkpw(password, storedHash); // ✅ Check bcrypt hash
            }
        } catch (SQLException e) {
            System.out.println("Error validating user: " + e.getMessage());
        }
        return false;
    }

    public static void showHistory() {
        System.out.println("==Transaction History==");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM transactions ORDER BY id DESC")) {

            System.out.println("ID | Type   | Amount  | Description");
            System.out.println("-----------------------------------");
            while (rs.next()) {
                System.out.printf("%-2d | %-6s | %7.2f | %s\n",
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getDouble("amount"),
                        rs.getString("description"));
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving transaction history:");
            e.printStackTrace();
        }
    }

//    public static void connectDatabase() throws SQLException {
////        conn = DriverManager.getConnection("jdbc:sqlite:transactions.db");
//
//        try (Statement stmt = conn.createStatement()) {
//            stmt.executeUpdate("""
//                CREATE TABLE IF NOT EXISTS transactions (
//                    id INTEGER PRIMARY KEY AUTOINCREMENT,
//                    type TEXT NOT NULL,
//                    amount REAL NOT NULL,
//                    description TEXT NOT NULL,
//                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
//                );
//                """);
//
//            ResultSet rs = stmt.executeQuery("""
//                SELECT SUM(CASE WHEN type='Credit' THEN amount ELSE -amount END) AS balance
//                FROM transactions
//                """);
//            if (rs.next()) {
//                balance = rs.getDouble("balance");
//            }
//        }
//    }

    public static void saveTransaction(String type, double amount, String description, String userEmail) {
        String sql = "INSERT INTO transactions(type, amount, description, user_email) VALUES(?,?,?,?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setDouble(2, amount);
            ps.setString(3, description);
            ps.setString(4, userEmail);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving transaction:");
            e.printStackTrace();
        }
    }

    public static void checkLoanReminders(String email) {
        String query = "SELECT due_date, amount FROM loans WHERE email = ? AND status = 'active'";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

            boolean hasReminder = false;

            while (rs.next()) {
                String dueStr = rs.getString("due_date");
                java.time.LocalDate dueDate = java.time.LocalDate.parse(dueStr, fmt);
                long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);

                if (daysLeft >= 0 && daysLeft <= 7) {
                    System.out.printf("Reminder: You have a loan of RM %.2f due on %s (in %d days).\n",
                            rs.getDouble("amount"), dueDate, daysLeft);
                    hasReminder = true;
                }
            }

            if (!hasReminder) {
                System.out.println("No loan repayments due within the next 7 days.");
            }

        } catch (Exception e) {
            System.out.println("Error checking loan reminders: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void exportToCSV (String email) {
        String outputFile = "transaction_history.csv";

        String sql = "SELECT timestamp, description, type, amount FROM transactions";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            try (FileWriter fw = new FileWriter(outputFile)) {
                // Write CSV headers
                fw.write("Date,Description,Type,Amount\n");

                while (rs.next()) {
                    String row = String.format("%s,%s,%s,%.2f\n",
                            rs.getString("timestamp"),
                            rs.getString("description").replace(",", ";"),  // Handle commas in description
                            rs.getString("type"),
                            rs.getDouble("amount"));
                    fw.write(row);
                }

                System.out.println("Successfully exported transactions to " + outputFile);
            }
        } catch (Exception e) {
            System.out.println("Error exporting to CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public static void disconnectDatabase() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
            System.out.println("Database connection closed.");
        }
    }
}
