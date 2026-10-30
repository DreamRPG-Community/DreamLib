package cn.mythicland.dreamlib.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * SQLite implementation backed by a driver loaded at runtime from a plugin-owned library.
 */
public final class SqliteDatabase implements SqlDatabase {

    private static final int BUSY_TIMEOUT_MILLISECONDS = 5_000;

    private final Path databaseFile;
    private final String jdbcUrl;
    private final DriverRegistration registration;
    private final Object transactionMonitor = new Object();
    private boolean closed;

    private SqliteDatabase(
            Path databaseFile,
            String jdbcUrl,
            DriverRegistration registration
    ) {
        this.databaseFile = databaseFile;
        this.jdbcUrl = jdbcUrl;
        this.registration = registration;
    }

    /**
     * Opens an SQLite database and registers its driver through a standard JDBC shim.
     *
     * @param owner             plugin receiving startup diagnostics
     * @param databaseFile      plugin-owned database path
     * @param driverClassName   driver implementation class, normally {@code org.sqlite.JDBC}
     * @param driverClassLoader class loader containing the verified driver JAR
     * @return open SQLite database
     * @throws IllegalStateException if the path, driver, or JDBC registration is invalid
     */
    public static SqliteDatabase open(
            JavaPlugin owner,
            Path databaseFile,
            String driverClassName,
            ClassLoader driverClassLoader
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(databaseFile, "databaseFile");
        Objects.requireNonNull(driverClassName, "driverClassName");
        Objects.requireNonNull(driverClassLoader, "driverClassLoader");

        Path normalizedFile = databaseFile.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalizedFile)) {
            throw new IllegalStateException("SQLite database cannot be a symbolic link: " + normalizedFile);
        }
        Path parent = normalizedFile.getParent();
        try {
            if (parent != null) Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create SQLite database directory: " + parent, exception);
        }

        try {
            Class<?> driverType = Class.forName(driverClassName, true, driverClassLoader);
            Object driverObject = driverType.getDeclaredConstructor().newInstance();
            if (!(driverObject instanceof Driver driver)) {
                throw new IllegalStateException("SQLite driver is not a java.sql.Driver: " + driverClassName);
            }
            DriverRegistration registration = new DriverRegistration(driver);
            DriverManager.registerDriver(registration);
            owner.getLogger().info("SQLite database opened: " + normalizedFile.getFileName());
            return new SqliteDatabase(
                    normalizedFile,
                    "jdbc:sqlite:" + normalizedFile,
                    registration
            );
        } catch (ReflectiveOperationException | SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite driver: " + driverClassName, exception);
        }
    }

    private static void configureConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MILLISECONDS);
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    /**
     * Returns the database file.
     *
     * @return normalized database path
     */
    public Path databaseFile() {
        return databaseFile;
    }

    /**
     * Executes work with a fresh auto-commit connection.
     *
     * @param work database work
     * @param <T>  result type
     * @return work result
     * @throws SQLException when the operation fails
     */
    @Override
    public <T> T query(SqlWork<T> work) throws SQLException {
        return this.execute(work, false);
    }

    /**
     * Executes work inside one transaction.
     *
     * @param work database work
     * @param <T>  result type
     * @return work result
     * @throws SQLException when the operation or rollback fails
     */
    @Override
    public <T> T transaction(SqlWork<T> work) throws SQLException {
        return this.execute(work, true);
    }

    /**
     * Deregisters the runtime driver.
     *
     * @throws SQLException when JDBC refuses deregistration
     */
    @Override
    public void close() throws SQLException {
        if (closed) return;
        closed = true;
        DriverManager.deregisterDriver(registration);
    }

    private <T> T execute(SqlWork<T> work, boolean transactional) throws SQLException {
        Objects.requireNonNull(work, "work");
        if (closed) throw new IllegalStateException("SQLite database is closed: " + databaseFile);

        if (transactional) {
            synchronized (transactionMonitor) {
                return executeWithConnection(work, true);
            }
        }
        return executeWithConnection(work, false);
    }

    private <T> T executeWithConnection(SqlWork<T> work, boolean transactional) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            configureConnection(connection);
            connection.setAutoCommit(!transactional);
            if (transactional) {
                try {
                    T result = work.execute(connection);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            }
            return work.execute(connection);
        }
    }

    private record DriverRegistration(Driver delegate) implements Driver {

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }
}
