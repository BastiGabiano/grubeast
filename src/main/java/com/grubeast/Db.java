package com.grubeast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** MySQL connection, table setup and small query helpers. */
public final class Db {
    private Db() {}

    public static class DbException extends RuntimeException {
        public DbException(String message, Throwable cause) { super(message, cause); }
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static final Path CONFIG = Path.of(System.getProperty("user.home"), ".grubeast", "config.properties");
    private static Properties current;
    private static Connection conn;

    // ---------- settings ----------

    public static Properties loadConfig() {
        Properties p = new Properties();
        p.setProperty("host", "localhost");
        p.setProperty("port", "3306");
        p.setProperty("database", "grubeast");
        p.setProperty("user", "root");
        p.setProperty("password", "");
        if (Files.exists(CONFIG)) {
            try (InputStream in = Files.newInputStream(CONFIG)) {
                p.load(in);
            } catch (IOException ignored) {
            }
        }
        return p;
    }

    public static void saveConfig(Properties p) {
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG)) {
                p.store(out, "Grub East database settings");
            }
        } catch (IOException e) {
            throw new DbException("Could not save the settings file.", e);
        }
    }

    // ---------- connecting ----------

    private static final String OPTIONS =
        "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=LOCAL";

    private static Connection open(Properties p, boolean withDatabase) throws SQLException {
        String url = "jdbc:mysql://" + p.getProperty("host") + ":" + p.getProperty("port") + "/"
                   + (withDatabase ? p.getProperty("database") : "") + OPTIONS;
        return DriverManager.getConnection(url, p.getProperty("user"), p.getProperty("password", ""));
    }

    /** Connects, creates the database and tables if they are missing, and makes sure an admin exists. */
    public static synchronized void connect(Properties p) throws SQLException {
        String db = p.getProperty("database", "");
        if (!db.matches("[A-Za-z0-9_]{1,64}")) {
            throw new SQLException("The database name may only use letters, numbers and underscores.");
        }
        try (Connection c = open(p, false); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + db
                           + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) { }
        }
        conn = open(p, true);
        current = p;
        createTables();
        seedAdmin();
    }

    public static synchronized Connection connection() throws SQLException {
        if (conn == null || !conn.isValid(2)) {
            if (current == null) throw new SQLException("Not connected to the database.");
            conn = open(current, true);
        }
        return conn;
    }

    private static void createTables() throws SQLException {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS images (id BIGINT AUTO_INCREMENT PRIMARY KEY, data MEDIUMBLOB NOT NULL)",

            """
            CREATE TABLE IF NOT EXISTS users (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              name VARCHAR(120) NOT NULL,
              email VARCHAR(190) NOT NULL UNIQUE,
              password VARCHAR(255) NOT NULL,
              role VARCHAR(20) NOT NULL,
              approved TINYINT(1) NOT NULL DEFAULT 1,
              rejected TINYINT(1) NOT NULL DEFAULT 0,
              rejection_reason VARCHAR(300),
              bio VARCHAR(1000),
              contact VARCHAR(200)
            )""",

            """
            CREATE TABLE IF NOT EXISTS shops (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              owner_id BIGINT NOT NULL UNIQUE,
              description VARCHAR(1000),
              location VARCHAR(200),
              available_days VARCHAR(200),
              open_time VARCHAR(10),
              close_time VARCHAR(10),
              allow_preorder TINYINT(1) NOT NULL DEFAULT 0,
              logo_id BIGINT NULL,
              FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
              FOREIGN KEY (logo_id) REFERENCES images(id) ON DELETE SET NULL
            )""",

            """
            CREATE TABLE IF NOT EXISTS products (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              shop_id BIGINT NOT NULL,
              name VARCHAR(150) NOT NULL,
              price DECIMAL(10,2) NOT NULL,
              available TINYINT(1) NOT NULL DEFAULT 1,
              description VARCHAR(500),
              image_id BIGINT NULL,
              FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
              FOREIGN KEY (image_id) REFERENCES images(id) ON DELETE SET NULL
            )""",

            """
            CREATE TABLE IF NOT EXISTS reviews (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              shop_id BIGINT NOT NULL,
              author_id BIGINT NOT NULL,
              rating INT NOT NULL,
              body VARCHAR(500),
              posted_at DATETIME NOT NULL,
              UNIQUE (shop_id, author_id),
              FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
              FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
            )""",

            """
            CREATE TABLE IF NOT EXISTS events (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              organizer_id BIGINT NOT NULL,
              title VARCHAR(150) NOT NULL,
              description VARCHAR(1000),
              event_date DATE NOT NULL,
              start_time VARCHAR(10),
              end_time VARCHAR(10),
              location VARCHAR(200),
              seeking_sponsors TINYINT(1) NOT NULL DEFAULT 0,
              image_id BIGINT NULL,
              FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE CASCADE,
              FOREIGN KEY (image_id) REFERENCES images(id) ON DELETE SET NULL
            )""",

            """
            CREATE TABLE IF NOT EXISTS sponsor_applications (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              event_id BIGINT NOT NULL,
              business_id BIGINT NOT NULL,
              message VARCHAR(500),
              status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
              UNIQUE (event_id, business_id),
              FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
              FOREIGN KEY (business_id) REFERENCES users(id) ON DELETE CASCADE
            )""",

            """
            CREATE TABLE IF NOT EXISTS messages (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              sender_id BIGINT NOT NULL,
              recipient_id BIGINT NOT NULL,
              body VARCHAR(1000) NOT NULL,
              sent_at DATETIME NOT NULL,
              seen TINYINT(1) NOT NULL DEFAULT 0,
              FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
              FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
            )""",

            """
            CREATE TABLE IF NOT EXISTS cart_items (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              product_id BIGINT NOT NULL,
              quantity INT NOT NULL,
              UNIQUE (user_id, product_id),
              FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
              FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
            )""",

            """
            CREATE TABLE IF NOT EXISTS preorders (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              shop_id BIGINT NOT NULL,
              customer_id BIGINT NOT NULL,
              event_id BIGINT NULL,
              status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
              note VARCHAR(300),
              created_at DATETIME NOT NULL,
              FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
              FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE CASCADE,
              FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE SET NULL
            )""",

            """
            CREATE TABLE IF NOT EXISTS preorder_items (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              preorder_id BIGINT NOT NULL,
              product_name VARCHAR(150) NOT NULL,
              unit_price DECIMAL(10,2) NOT NULL,
              quantity INT NOT NULL,
              FOREIGN KEY (preorder_id) REFERENCES preorders(id) ON DELETE CASCADE
            )"""
        };
        try (Statement st = conn.createStatement()) {
            for (String sql : ddl) st.executeUpdate(sql);
        }
    }

    /** First run: create the admin account (admin@grubeast.com / admin123). Change the password in My profile. */
    private static void seedAdmin() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")) {
            rs.next();
            if (rs.getLong(1) > 0) return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (name, email, password, role, approved, rejected) VALUES (?, ?, ?, 'ADMIN', 1, 0)")) {
            ps.setString(1, "Admin");
            ps.setString(2, "admin@grubeast.com");
            ps.setString(3, Passwords.hash("admin123"));
            ps.executeUpdate();
        }
    }

    // ---------- query helpers ----------

    private static PreparedStatement prepare(String sql, boolean keys, Object... args) throws SQLException {
        PreparedStatement ps = keys
            ? connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
            : connection().prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof LocalDateTime t) a = Timestamp.valueOf(t);
            else if (a instanceof LocalDate d) a = java.sql.Date.valueOf(d);
            ps.setObject(i + 1, a);
        }
        return ps;
    }

    public static synchronized <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        try (PreparedStatement ps = prepare(sql, false, args); ResultSet rs = ps.executeQuery()) {
            List<T> list = new ArrayList<>();
            while (rs.next()) list.add(mapper.map(rs));
            return list;
        } catch (SQLException e) {
            throw new DbException("Database error: " + e.getMessage(), e);
        }
    }

    public static <T> T one(String sql, RowMapper<T> mapper, Object... args) {
        List<T> list = query(sql, mapper, args);
        return list.isEmpty() ? null : list.get(0);
    }

    public static synchronized int update(String sql, Object... args) {
        try (PreparedStatement ps = prepare(sql, false, args)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("Database error: " + e.getMessage(), e);
        }
    }

    /** Runs an INSERT and returns the new row's id. */
    public static synchronized long insert(String sql, Object... args) {
        try (PreparedStatement ps = prepare(sql, true, args)) {
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DbException("Database error: " + e.getMessage(), e);
        }
    }

    public static synchronized void begin() {
        try { connection().setAutoCommit(false); }
        catch (SQLException e) { throw new DbException("Database error: " + e.getMessage(), e); }
    }

    public static synchronized void commit() {
        try { conn.commit(); conn.setAutoCommit(true); }
        catch (SQLException e) { throw new DbException("Database error: " + e.getMessage(), e); }
    }

    public static synchronized void rollback() {
        try { conn.rollback(); conn.setAutoCommit(true); }
        catch (SQLException ignored) { }
    }

    /** Reads a BIGINT column that may be NULL. */
    public static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    public static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toLocalDateTime();
    }
}
