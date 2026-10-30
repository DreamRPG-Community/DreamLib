package cn.mythicland.dreamlib.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Applies plugin-owned SQL migrations using a shared database transaction boundary.
 */
public final class MigrationRunner {

    private static final String HISTORY_TABLE = "lib_schema_history";

    private static List<Migration> resolveMigrations(
            JavaPlugin plugin,
            Collection<MigrationSpec> migrationSpecs
    ) throws IOException {
        Objects.requireNonNull(migrationSpecs, "migrationSpecs");
        List<MigrationSpec> specifications = migrationSpecs.stream()
                .sorted(Comparator.comparingInt(MigrationSpec::version))
                .toList();
        Set<Integer> versions = new HashSet<>();
        List<Migration> migrations = new ArrayList<>();
        for (MigrationSpec specification : specifications) {
            if (!versions.add(specification.version())) {
                throw new IllegalArgumentException(
                        "Duplicate migration version: " + specification.version()
                );
            }
            try (InputStream input = plugin.getResource(specification.resourcePath())) {
                if (input == null) {
                    throw new IOException(
                            "Missing migration resource: " + specification.resourcePath()
                    );
                }
                String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (sql.isBlank()) {
                    throw new IOException(
                            "Empty migration resource: " + specification.resourcePath()
                    );
                }
                migrations.add(new Migration(specification, sql));
            }
        }
        return List.copyOf(migrations);
    }

    private static void createHistoryTable(java.sql.Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + HISTORY_TABLE + " ("
                            + "version INTEGER PRIMARY KEY, "
                            + "resource_path TEXT NOT NULL, "
                            + "applied_at BIGINT NOT NULL"
                            + ")"
            );
        }
    }

    private static Set<Integer> appliedVersions(java.sql.Connection connection) throws SQLException {
        Set<Integer> versions = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version FROM " + HISTORY_TABLE)) {
            while (resultSet.next()) versions.add(resultSet.getInt(1));
        }
        return versions;
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    private static void applyMigration(
            java.sql.Connection connection,
            Migration migration
    ) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(migration.sql());
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + HISTORY_TABLE + " (version, resource_path, applied_at) VALUES (?, ?, ?)"
        )) {
            statement.setInt(1, migration.specification().version());
            statement.setString(2, migration.specification().resourcePath());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * Applies every migration not already recorded in the database.
     *
     * @param database       target database
     * @param plugin         plugin owning migration resources
     * @param migrationSpecs ordered migration descriptors
     * @throws IOException  when a migration resource cannot be read
     * @throws SQLException when migration SQL fails
     */
    public void migrate(
            SqlDatabase database,
            JavaPlugin plugin,
            Collection<MigrationSpec> migrationSpecs
    ) throws IOException, SQLException {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(plugin, "plugin");
        List<Migration> migrations = resolveMigrations(plugin, migrationSpecs);
        database.transaction(connection -> {
            createHistoryTable(connection);
            Set<Integer> appliedVersions = appliedVersions(connection);
            for (Migration migration : migrations) {
                if (appliedVersions.contains(migration.specification().version())) continue;
                applyMigration(connection, migration);
            }
            return null;
        });
    }

    private record Migration(MigrationSpec specification, String sql) {
    }
}
