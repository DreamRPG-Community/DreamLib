package cn.mythicland.dreamlib.database;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Exposes DreamLib's registered SQLite driver through the JDBC DataSource contract.
 */
final class SqliteDataSource implements DataSource {

    private static final int BUSY_TIMEOUT_MILLISECONDS = 5_000;
    private final String jdbcUrl;
    private volatile PrintWriter logWriter;

    SqliteDataSource(Path databaseFile) {
        this.jdbcUrl = "jdbc:sqlite:" + Objects.requireNonNull(databaseFile, "databaseFile");
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(this.jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MILLISECONDS);
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        if (username != null || password != null) {
            throw new SQLException("SQLite data source does not accept credentials");
        }
        return this.getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
        return this.logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        if (seconds != 0) throw new SQLFeatureNotSupportedException("SQLite login timeout is not configurable");
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("SQLite data source has no parent logger");
    }

    @Override
    public <T> T unwrap(Class<T> interfaceType) throws SQLException {
        if (interfaceType.isInstance(this)) return interfaceType.cast(this);
        throw new SQLException("SQLite data source does not wrap " + interfaceType.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> interfaceType) {
        return interfaceType.isInstance(this);
    }
}
