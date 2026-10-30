package cn.mythicland.dreamlib.integration;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Reflective Vault economy bridge used by plugins that must not bundle Vault.
 */
public final class VaultEconomyBridge implements PlayerBalanceService {

    private static final String VAULT_PLUGIN = "Vault";
    private static final String ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private final JavaPlugin owner;
    private Object economy;
    private Method getBalance;
    private boolean lookupFailed;
    private boolean failureLogged;

    /**
     * Creates a lazy Vault bridge.
     *
     * @param owner plugin receiving diagnostics
     */
    public VaultEconomyBridge(JavaPlugin owner) {
        this.owner = owner;
    }

    @Override
    public boolean isAvailable() {
        return ensureProvider();
    }

    @Override
    public double balance(Player player) {
        if (!ensureProvider()) throw new IllegalStateException("Vault economy provider is unavailable");
        try {
            Object result = getBalance.invoke(economy, player);
            if (!(result instanceof Number number)) {
                throw new IllegalStateException("Vault economy returned a non-numeric balance");
            }
            return number.doubleValue();
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Vault economy balance lookup failed", exception);
        }
    }

    private boolean ensureProvider() {
        if (economy != null && getBalance != null) return true;
        if (lookupFailed) return false;
        Plugin vault = owner.getServer().getPluginManager().getPlugin(VAULT_PLUGIN);
        if (vault == null || !vault.isEnabled()) return false;
        try {
            Class<?> economyType = Class.forName(
                    ECONOMY_CLASS,
                    true,
                    vault.getClass().getClassLoader()
            );
            RegisteredServiceProvider<?> registration = getRegistration(economyType);
            if (registration == null || registration.getProvider() == null) return false;
            economy = registration.getProvider();
            getBalance = economyType.getMethod("getBalance", OfflinePlayer.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            lookupFailed = true;
            if (!failureLogged) {
                failureLogged = true;
                owner.getLogger().log(
                        Level.WARNING,
                        "Vault is enabled but its economy service could not be used.",
                        exception
                );
            }
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private RegisteredServiceProvider<?> getRegistration(Class<?> economyType) {
        return owner.getServer().getServicesManager().getRegistration((Class<Object>) economyType);
    }
}
