package cn.mythicland.dreamlib.database;

import cn.mythicland.framework.database.*;
import cn.mythicland.framework.runtime.LibraryLoadResult;
import cn.mythicland.framework.runtime.LibraryService;
import cn.mythicland.framework.runtime.LibrarySpec;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Owns the database shared by DreamLib consumers.
 */
public final class SharedDatabase implements AutoCloseable {
    private static final LibrarySpec POSTGRES = new LibrarySpec(
            "org.postgresql:postgresql:42.7.4", "postgresql-42.7.4.jar",
            "188976721ead8e8627eb6d8389d500dccc0c9bebd885268a3047180274a6031e"
    );
    private static final LibrarySpec HIKARI = new LibrarySpec(
            "com.zaxxer:HikariCP:4.0.3", "HikariCP-4.0.3.jar",
            "7c024aeff1c1063576d74453513f9de6447d8e624d17f8e27f30a2e97688c6c9"
    );

    private final LibraryLoadResult libraries;
    private final SqlDatabase database;
    private final DataSource dataSource;
    private final String mode;

    /**
     * Opens the shared database and its runtime dependencies.
     *
     * @param owner          plugin owning the database configuration and dependencies
     * @param libraryService verified runtime library loader
     * @param factory        database factory
     */
    public SharedDatabase(
            JavaPlugin owner,
            LibraryService libraryService,
            DatabaseService factory
    ) {
        File configFile = new File(owner.getDataFolder(), "database.yml");
        if (!configFile.exists()) owner.saveResource("database.yml", false);
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Cannot read database.yml", exception);
        }
        mode = config.getString("mode", "sqlite").toLowerCase(Locale.ROOT);
        if (!mode.equals("sqlite") && !mode.equals("postgres")) {
            throw new IllegalArgumentException("database.yml mode must be sqlite or postgres");
        }
        libraries = libraryService.load(
                owner.getDataFolder().toPath(), owner.getClass().getClassLoader(), owner.getLogger(),
                mode.equals("sqlite") ? List.of(DatabaseDrivers.SQLITE) : List.of(POSTGRES, HIKARI),
                LibraryService.DEFAULT_REPOSITORY
        );
        try {
            database = mode.equals("sqlite")
                    ? openSqlite(owner, factory, config)
                    : openPostgres(config);
            dataSource = database instanceof DataSourceDatabase postgres
                    ? postgres.source()
                    : new SqliteDataSource(((SqliteDatabase) database).databaseFile());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            try {
                libraries.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw new IllegalStateException("Failed to initialize shared database", exception);
        }
    }

    private SqlDatabase openSqlite(JavaPlugin owner, DatabaseService factory, YamlConfiguration config) {
        Path root = owner.getDataFolder().toPath().toAbsolutePath().normalize();
        Path path = root.resolve(config.getString("sqlite.file", "data/dreamrpg.db")).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IllegalArgumentException("Invalid sqlite.file");
        return factory.openSqlite(owner.getLogger(), path, DatabaseDrivers.SQLITE_CLASS, libraries.classLoader());
    }

    private SqlDatabase openPostgres(YamlConfiguration config) throws ReflectiveOperationException {
        String host = config.getString("postgresql.host", "127.0.0.1");
        int port = config.getInt("postgresql.port", 5432);
        String name = config.getString("postgresql.name", "dreamrpg");
        ManagedDataSource source = this.createPostgresDataSource(
                host,
                port,
                name,
                config.getString("postgresql.username", "dreamrpg"),
                config.getString("postgresql.password", ""),
                config.getInt("poolSize", 8),
                config.getInt("minimumIdle", 2),
                config.getLong("connectionTimeoutMs", 5000),
                config.getLong("maxLifetimeMs", 1800000),
                config.getLong("keepaliveTimeMs", 300000)
        );
        return new DataSourceDatabase(source.dataSource(), source);
    }

    /**
     * Opens another PostgreSQL pool using the verified drivers already owned by DreamLib.
     *
     * @param host database host
     * @param port database port
     * @param name database name
     * @param username database username
     * @param password database password
     * @param poolSize maximum number of pooled connections
     * @return managed data source that the caller must close
     */
    public ManagedDataSource openPostgresDataSource(
            String host,
            int port,
            String name,
            String username,
            String password,
            int poolSize
    ) {
        if (!this.mode.equals("postgres")) {
            throw new IllegalStateException("A separate PostgreSQL data source requires DreamLib PostgreSQL mode");
        }
        try {
            int minimumIdle = Math.min(1, poolSize);
            return this.createPostgresDataSource(
                    host,
                    port,
                    name,
                    username,
                    password,
                    poolSize,
                    minimumIdle,
                    5000,
                    1800000,
                    300000
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create PostgreSQL data source", exception);
        }
    }

    private ManagedDataSource createPostgresDataSource(
            String host,
            int port,
            String name,
            String username,
            String password,
            int poolSize,
            int minimumIdle,
            long connectionTimeoutMillis,
            long maxLifetimeMillis,
            long keepaliveTimeMillis
    ) throws ReflectiveOperationException {
        if (host == null || host.isBlank() || name == null || name.isBlank()
                || username == null || username.isBlank() || password == null
                || port < 1 || port > 65535 || poolSize < 1 || minimumIdle < 0 || minimumIdle > poolSize
                || host.matches(".*[/?#\\s].*") || name.matches(".*[/?#\\s].*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL connection settings");
        }
        ClassLoader loader = this.libraries.classLoader();
        Class<?> poolConfigClass = Class.forName("com.zaxxer.hikari.HikariConfig", true, loader);
        Object poolConfig = poolConfigClass.getConstructor().newInstance();
        poolConfigClass.getMethod("setUsername", String.class).invoke(poolConfig, username);
        poolConfigClass.getMethod("setPassword", String.class).invoke(poolConfig, password);
        poolConfigClass.getMethod("setMaximumPoolSize", int.class).invoke(poolConfig, poolSize);
        poolConfigClass.getMethod("setMinimumIdle", int.class).invoke(poolConfig, minimumIdle);
        poolConfigClass.getMethod("setConnectionTimeout", long.class)
                .invoke(poolConfig, connectionTimeoutMillis);
        poolConfigClass.getMethod("setMaxLifetime", long.class).invoke(poolConfig, maxLifetimeMillis);
        poolConfigClass.getMethod("setKeepaliveTime", long.class).invoke(poolConfig, keepaliveTimeMillis);

        Class<?> postgresDataSourceClass = Class.forName("org.postgresql.ds.PGSimpleDataSource", true, loader);
        Object postgresDataSource = postgresDataSourceClass.getConstructor().newInstance();
        postgresDataSourceClass.getMethod("setURL", String.class)
                .invoke(postgresDataSource, "jdbc:postgresql://" + host + ":" + port + "/" + name);
        poolConfigClass.getMethod("setDataSource", DataSource.class).invoke(poolConfig, postgresDataSource);
        Class<?> poolClass = Class.forName("com.zaxxer.hikari.HikariDataSource", true, loader);
        Object pool = poolClass.getConstructor(poolConfigClass).newInstance(poolConfig);
        return new ManagedDataSource((DataSource) pool, (AutoCloseable) pool);
    }

    /**
     * Returns the database borrowed by plugins; the Lib lifecycle owns it.
     *
     * @return shared database, which consumers must not close
     */
    public SqlDatabase database() {
        return database;
    }

    /**
     * Returns a data source borrowed by integrations that require the JDBC DataSource contract.
     * The shared database lifecycle owns the underlying pool or SQLite driver.
     *
     * @return shared database data source, which consumers must not close
     */
    public DataSource dataSource() {
        return dataSource;
    }

    /**
     * Returns the selected database mode.
     *
     * @return sqlite or postgres
     */
    public String mode() {
        return mode;
    }

    /**
     * Applies a plugin's dialect-specific migrations to its isolated history.
     *
     * @param plugin       owner of the migration resources
     * @param historyTable SQL identifier for this plugin's migration history
     * @param migrations   ordered migration versions and resource names below the dialect directory
     * @throws IOException  when a resource cannot be read
     * @throws SQLException when the migrations cannot be applied
     */
    public void migrate(
            JavaPlugin plugin,
            String historyTable,
            List<MigrationSpec> migrations
    ) throws IOException, SQLException {
        Objects.requireNonNull(migrations, "migrations");
        List<MigrationSpec> resolved = migrations.stream()
                .map(spec -> new MigrationSpec(spec.version(), "db/" + mode + "/" + spec.resourcePath()))
                .toList();
        new MigrationRunner(historyTable).migrate(database, plugin::getResource, resolved);
    }

    @Override
    public void close() throws SQLException, IOException {
        try {
            database.close();
        } finally {
            libraries.close();
        }
    }
}
