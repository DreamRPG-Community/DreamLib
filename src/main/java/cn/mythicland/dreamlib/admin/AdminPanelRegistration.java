package cn.mythicland.dreamlib.admin;

/**
 * Handle for unregistering one provider from Lib's administrator panel registry.
 */
@FunctionalInterface
public interface AdminPanelRegistration extends AutoCloseable {

    /**
     * Unregisters the provider. Calling this method more than once is safe.
     */
    @Override
    void close();
}
