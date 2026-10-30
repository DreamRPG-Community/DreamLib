package cn.mythicland.dreamlib.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Generic JDBC database backed by a verified runtime driver.
 */
public final class JdbcDatabase implements SqlDatabase {

    private final String jdbcUrl;
    private final Properties properties;
    private final DriverRegistration registration;
    private boolean closed;

    private JdbcDatabase(
            String jdbcUrl,
            Properties properties,
            DriverRegistration registration
    ) {
        this.jdbcUrl = jdbcUrl;
        this.properties = properties;
        this.registration = registration;
    }

    /**
     * Opens a JDBC database and registers its driver through a standard JDBC shim.
     *
     * @param owner             plugin receiving startup diagnostics
     * @param jdbcUrl           JDBC URL
     * @param properties        connection properties
     * @param driverClassName   driver class name
     * @param driverClassLoader class loader containing the driver
     * @return open database
     */
    public static JdbcDatabase open(
            JavaPlugin owner,
            String jdbcUrl,
            Properties properties,
            String driverClassName,
            ClassLoader driverClassLoader
    ) {
        Objects.requireNonNull(owner, "owner");
        String url = requireUrl(jdbcUrl);
        Properties connectionProperties = copyProperties(properties);
        Objects.requireNonNull(driverClassName, "driverClassName");
        Objects.requireNonNull(driverClassLoader, "driverClassLoader");

        try {
            Class<?> driverType = Class.forName(driverClassName, true, driverClassLoader);
            Object driverObject = driverType.getDeclaredConstructor().newInstance();
            if (!(driverObject instanceof Driver driver)) {
                throw new IllegalStateException("JDBC driver is not a java.sql.Driver: " + driverClassName);
            }
            DriverRegistration registration = new DriverRegistration(driver);
            DriverManager.registerDriver(registration);
            owner.getLogger().info("JDBC database opened: " + url);
            return new JdbcDatabase(url, connectionProperties, registration);
        } catch (ReflectiveOperationException | SQLException exception) {
            throw new IllegalStateException("Failed to initialize JDBC driver: " + driverClassName, exception);
        }
    }

    private static String requireUrl(String jdbcUrl) {
        String value = Objects.requireNonNull(jdbcUrl, "jdbcUrl").trim();
        if (value.isBlank()) throw new IllegalArgumentException("jdbcUrl cannot be blank");
        return value;
    }

    private static Properties copyProperties(Properties source) {
        Properties copy = new Properties();
        copy.putAll(Objects.requireNonNull(source, "properties"));
        return copy;
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    @Override
    public <T> T query(SqlWork<T> work) throws SQLException {
        return execute(work, false);
    }

    @Override
    public <T> T transaction(SqlWork<T> work) throws SQLException {
        return execute(work, true);
    }

    @Override
    public void close() throws SQLException {
        if (closed) return;
        closed = true;
        DriverManager.deregisterDriver(registration);
    }

    private <T> T execute(SqlWork<T> work, boolean transactional) throws SQLException {
        Objects.requireNonNull(work, "work");
        if (closed) throw new IllegalStateException("JDBC database is closed: " + jdbcUrl);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, properties)) {
            connection.setAutoCommit(!transactional);
            if (!transactional) return work.execute(connection);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
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
