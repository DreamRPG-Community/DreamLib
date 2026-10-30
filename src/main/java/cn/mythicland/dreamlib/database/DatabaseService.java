package cn.mythicland.dreamlib.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Factory for databases whose third-party drivers are loaded by Lib.
 */
public final class DatabaseService {

    /**
     * Opens a generic JDBC database using a driver already loaded into the supplied class loader.
     *
     * @param owner             plugin receiving diagnostics
     * @param jdbcUrl           JDBC connection URL
     * @param properties        JDBC connection properties
     * @param driverClassName   driver implementation class
     * @param driverClassLoader class loader containing the driver
     * @return open generic JDBC database
     */
    public JdbcDatabase openJdbc(
            JavaPlugin owner,
            String jdbcUrl,
            Properties properties,
            String driverClassName,
            ClassLoader driverClassLoader
    ) {
        return JdbcDatabase.open(
                Objects.requireNonNull(owner, "owner"),
                Objects.requireNonNull(jdbcUrl, "jdbcUrl"),
                Objects.requireNonNull(properties, "properties"),
                Objects.requireNonNull(driverClassName, "driverClassName"),
                Objects.requireNonNull(driverClassLoader, "driverClassLoader")
        );
    }

    /**
     * Opens an SQLite database using a driver already loaded into the supplied class loader.
     *
     * @param owner             plugin receiving diagnostics
     * @param databaseFile      plugin-owned database path
     * @param driverClassName   driver implementation class
     * @param driverClassLoader class loader containing the driver
     * @return open SQLite database
     */
    public SqliteDatabase openSqlite(
            JavaPlugin owner,
            Path databaseFile,
            String driverClassName,
            ClassLoader driverClassLoader
    ) {
        Objects.requireNonNull(owner, "owner");
        return SqliteDatabase.open(owner, databaseFile, driverClassName, driverClassLoader);
    }
}
