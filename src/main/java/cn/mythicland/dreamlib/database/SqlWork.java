package cn.mythicland.dreamlib.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * One checked operation executed against a JDBC connection.
 *
 * @param <T> operation result type
 */
@FunctionalInterface
public interface SqlWork<T> {

    /**
     * Executes the operation.
     *
     * @param connection open JDBC connection
     * @return operation result
     * @throws SQLException when the database rejects the operation
     */
    @SuppressWarnings("SameReturnValue")
    T execute(Connection connection) throws SQLException;
}
