package cn.mythicland.dreamlib.bootstrap;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.ConfigComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.ServiceComponent;
import cn.mythicland.dreamlib.command.CommandRouter;
import cn.mythicland.dreamlib.config.ConfigSupport;
import cn.mythicland.dreamlib.config.ConfigView;
import cn.mythicland.dreamlib.config.ConfigurableComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

/**
 * Discovers, constructs, registers, reloads, and closes one plugin's Lib components.
 */
public final class PluginBootstrap implements AutoCloseable {

    private final JavaPlugin plugin;
    private final ComponentContainer components;
    private final List<DreamLibPluginLifecycle> lifecycles = new ArrayList<>();
    private final List<ConfigurableComponent> configurations = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final List<RegisteredService> services = new ArrayList<>();
    private final List<GlobalCommandTakeover> commandTakeovers = new ArrayList<>();
    private PluginTaskScope taskScope;
    private boolean enabled;

    /**
     * Creates a package-scoped plugin bootstrap.
     *
     * @param plugin      plugin being bootstrapped
     * @param lib         shared Lib service
     * @param basePackage package to scan for Lib annotations
     */
    public PluginBootstrap(JavaPlugin plugin, DreamLibApi lib, String basePackage) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.components = new ComponentContainer(plugin, lib, basePackage);
        this.taskScope = components.resolve(PluginTaskScope.class);
    }

    /**
     * Enables all discovered components.
     *
     * <p>Service providers are constructed first, lifecycle components are enabled in dependency
     * order, and only then are services, listeners, and commands published. The method must run
     * on the Bukkit primary thread during plugin startup.</p>
     *
     * @throws IllegalStateException if a component cannot be constructed or registered
     */
    public void enable() {
        if (enabled) throw new IllegalStateException("Plugin bootstrap is already enabled");
        taskScope = components.resolve(PluginTaskScope.class);
        try {
            configurations.addAll(prepareConfigurations());
            ConfigView configuration = ConfigSupport.loadDefaultView(plugin);
            for (ConfigurableComponent component : configurations) component.reload(configuration);
            List<PendingService> pendingServices = prepareServices();
            lifecycles.addAll(components.resolveAllOrdered(DreamLibPluginLifecycle.class));
            for (DreamLibPluginLifecycle lifecycle : lifecycles) lifecycle.enable();
            registerServices(pendingServices);
            registerListeners();
            registerCommands();
            enabled = true;
        } catch (RuntimeException exception) {
            disableInternal();
            throw exception;
        }
    }

    /**
     * Reloads every lifecycle component in dependency order.
     *
     * <p>Reload is a Bukkit lifecycle operation and must run on the primary thread. Components
     * decide which mutable resources are reloadable.</p>
     *
     * @throws IllegalStateException if this bootstrap is not enabled
     */
    public void reload() {
        if (!enabled) throw new IllegalStateException("Plugin bootstrap is not enabled");
        ConfigView configuration = ConfigSupport.reloadView(plugin);
        for (ConfigurableComponent component : configurations) component.reload(configuration);
        for (DreamLibPluginLifecycle lifecycle : lifecycles) lifecycle.reload();
    }

    /**
     * Disables listeners, command overrides, services, and lifecycle components.
     */
    @Override
    public void close() {
        disable();
    }

    /**
     * Disables the bootstrap. The operation is idempotent for Bukkit shutdown handling.
     *
     * <p>This method must run on the Bukkit primary thread because component cleanup may touch
     * Bukkit listeners, commands, services, and scoreboards.</p>
     */
    public void disable() {
        if (!enabled && lifecycles.isEmpty() && services.isEmpty() && commandTakeovers.isEmpty()) {
            taskScope.close();
            components.clear();
            return;
        }
        disableInternal();
    }

    /**
     * Resolves a component for a plugin-owned facade or test.
     *
     * @param type component type
     * @param <T>  component type
     * @return cached component
     */
    public <T> T resolve(Class<T> type) {
        return components.resolve(type);
    }

    private List<PendingService> prepareServices() {
        Set<Class<?>> contracts = new HashSet<>();
        List<PendingService> pending = new ArrayList<>();
        for (Class<?> componentType : components.annotatedTypes(ServiceComponent.class)) {
            ServiceComponent annotation = componentType.getAnnotation(ServiceComponent.class);
            Object provider = components.resolve(componentType);
            Class<?> contract = annotation.value();
            if (!contract.isInstance(provider)) {
                throw new IllegalStateException(
                        "Service component does not implement " + contract.getName() + ": " + componentType.getName()
                );
            }
            if (!contracts.add(contract)) {
                throw new IllegalStateException("Multiple Lib service components implement: " + contract.getName());
            }
            pending.add(new PendingService(contract, provider, annotation.priority()));
        }
        return List.copyOf(pending);
    }

    private List<ConfigurableComponent> prepareConfigurations() {
        List<ConfigurableComponent> result = new ArrayList<>();
        for (Class<?> componentType : components.annotatedTypes(ConfigComponent.class)) {
            Object component = components.resolve(componentType);
            if (!(component instanceof ConfigurableComponent configurable)) {
                throw new IllegalStateException(
                        "Config component must implement ConfigurableComponent: " + componentType.getName()
                );
            }
            result.add(configurable);
        }
        return List.copyOf(result);
    }

    private void registerServices(List<PendingService> pendingServices) {
        for (PendingService pending : pendingServices) {
            registerService(pending.contract(), pending.provider(), pending.priority());
            services.add(new RegisteredService(pending.contract(), pending.provider()));
        }
    }

    private void registerListeners() {
        for (Class<?> componentType : components.annotatedTypes(ListenerComponent.class)) {
            Object component = components.resolve(componentType);
            if (!(component instanceof Listener listener)) {
                throw new IllegalStateException("Listener component is not a Bukkit Listener: " + componentType.getName());
            }
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            listeners.add(listener);
        }
    }

    private void registerCommands() {
        Set<String> commandNames = new HashSet<>();
        for (Class<?> componentType : components.annotatedTypes(CommandComponent.class)) {
            Object value = components.resolve(componentType);
            CommandComponent annotation = componentType.getAnnotation(CommandComponent.class);
            String commandName = annotation == null ? null : annotation.value().trim();
            String commandKey = commandName == null ? null : commandName.toLowerCase(Locale.ROOT);
            if (commandName == null || commandName.isBlank() || !commandNames.add(commandKey)) {
                throw new IllegalStateException("Invalid or duplicate Lib command component: " + commandName);
            }
            PluginCommand command = plugin.getCommand(commandName);
            if (command == null) throw new IllegalStateException("Command is missing from plugin.yml: " + commandName);
            CommandRouter router = new CommandRouter(plugin, commandName);
            router.registerAnnotated(value);
            command.setExecutor(router);
            command.setTabCompleter(router);
            if (annotation.takeOverGlobalMapping()) {
                GlobalCommandTakeover globalTakeover = new GlobalCommandTakeover(plugin, commandName);
                globalTakeover.install(command);
                commandTakeovers.add(globalTakeover);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerService(Class<?> contract, Object provider, ServicePriority priority) {
        plugin.getServer().getServicesManager().register(
                (Class) contract,
                provider,
                plugin,
                priority
        );
    }

    private void disableInternal() {
        taskScope.close();
        for (Listener listener : listeners) HandlerList.unregisterAll(listener);
        listeners.clear();
        for (int index = commandTakeovers.size() - 1; index >= 0; index--) {
            commandTakeovers.get(index).restore();
        }
        commandTakeovers.clear();
        for (int index = services.size() - 1; index >= 0; index--) {
            RegisteredService service = services.get(index);
            unregisterService(service.contract(), service.provider());
        }
        services.clear();
        for (int index = lifecycles.size() - 1; index >= 0; index--) {
            DreamLibPluginLifecycle lifecycle = lifecycles.get(index);
            try {
                lifecycle.disable();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(
                        Level.SEVERE,
                        "Lib component shutdown failed: " + lifecycle.getClass().getName(),
                        exception
                );
            }
        }
        lifecycles.clear();
        configurations.clear();
        enabled = false;
        components.clear();
    }

    private void unregisterService(Class<?> contract, Object provider) {
        plugin.getServer().getServicesManager().unregister(contract, provider);
    }

    private record RegisteredService(Class<?> contract, Object provider) {
    }

    private record PendingService(Class<?> contract, Object provider, ServicePriority priority) {
    }
}
