package cn.mythicland.dreamlib.database;

import java.sql.SQLException;

/**
 * Small JDBC database boundary shared by self-written plugins.
 */
public interface SqlDatabase extends AutoCloseable {

    /**
     * Runs read-only or non-transactional work with a fresh connection.
     *
     * @param work database work
     * @param <T>  result type
     * @return work result
     * @throws SQLException when the database operation fails
     */
    <T> T query(SqlWork<T> work) throws SQLException;

    /**
     * Runs work in a commit-or-rollback transaction.
     *
     * @param work database work
     * @param <T>  result type
     * @return work result
     * @throws SQLException when the database operation or rollback fails
     */
    <T> T transaction(SqlWork<T> work) throws SQLException;

    /**
     * Closes the database driver's registration.
     *
     * @throws SQLException when JDBC cannot deregister the driver
     */
    @Override
    void close() throws SQLException;
}
