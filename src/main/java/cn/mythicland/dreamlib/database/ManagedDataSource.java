package cn.mythicland.dreamlib.database;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * A data source handle whose pool is owned by the caller.
 */
public final class ManagedDataSource implements AutoCloseable {

    private final DataSource dataSource;
    private final AutoCloseable owner;

    ManagedDataSource(DataSource dataSource, AutoCloseable owner) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * Returns the pooled data source.
     *
     * @return data source owned by this handle
     */
    public DataSource dataSource() {
        return this.dataSource;
    }

    @Override
    public void close() {
        try {
            this.owner.close();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to close managed data source", exception);
        }
    }
}
