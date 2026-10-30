package cn.mythicland.dreamlib.database;

import cn.mythicland.dreamlib.library.LibrarySpec;

/**
 * Fixed runtime driver specifications shared by DreamLib consumers.
 */
public final class DatabaseDrivers {
    /**
     * Verified SQLite JDBC driver used by the DreamLib plugins.
     */
    public static final LibrarySpec SQLITE = new LibrarySpec(
            "org.xerial:sqlite-jdbc:3.46.1.3",
            "sqlite-jdbc-3.46.1.3.jar",
            "4a4832720a65eaf7f4d6fd7ede52087b994dc5633c076f9e994dc0c8b4b0b4fa"
    );

    /**
     * JDBC driver class for {@link #SQLITE}.
     */
    public static final String SQLITE_CLASS = "org.sqlite.JDBC";

    private DatabaseDrivers() {
    }
}
