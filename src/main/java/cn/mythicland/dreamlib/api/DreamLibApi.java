package cn.mythicland.dreamlib.api;

import cn.mythicland.dreamlib.admin.AdminPanelService;
import cn.mythicland.dreamlib.bootstrap.PluginBootstrap;
import cn.mythicland.dreamlib.container.ContainerAnimationService;
import cn.mythicland.dreamlib.container.PacketContainerAnimationService;
import cn.mythicland.dreamlib.database.DatabaseService;
import cn.mythicland.dreamlib.integration.*;
import cn.mythicland.dreamlib.item.ItemMarkerCodec;
import cn.mythicland.dreamlib.item.ReflectiveItemMarkerCodec;
import cn.mythicland.dreamlib.library.LibraryService;
import cn.mythicland.dreamlib.loading.PlayerLoadingGate;
import cn.mythicland.dreamlib.book.BookDisplayService;
import cn.mythicland.dreamlib.book.LegacyBookDisplayService;
import cn.mythicland.dreamlib.material.EnchantmentCatalog;
import cn.mythicland.dreamlib.material.MaterialCatalog;
import cn.mythicland.dreamlib.material.MaterialIconCatalog;
import cn.mythicland.dreamlib.menu.MenuService;
import cn.mythicland.dreamlib.path.PathService;
import cn.mythicland.dreamlib.scoreboard.ScoreboardService;
import cn.mythicland.dreamlib.storage.StorageService;
import cn.mythicland.dreamlib.text.DecentFloatingTextService;
import cn.mythicland.dreamlib.text.FloatingTextService;
import cn.mythicland.dreamlib.web.EmbeddedHttpServer;
import cn.mythicland.dreamlib.web.WebService;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Shared lifecycle, scheduling, asynchronous execution, and command infrastructure for plugins.
 *
 * <p>The Lib plugin owns the service lifecycle. Dependent plugins may use the service while Lib is
 * enabled, but must not close it.</p>
 */
public final class DreamLibApi implements AutoCloseable {

    private final JavaPlugin owner;
    private final MenuService menuService;
    private final AdminPanelService adminPanelService;
    private final ContainerAnimationService containerAnimationService;
    private final FloatingTextService floatingTextService;
    private final PlayerLoadingGate playerLoadingGate;
    private final BookDisplayService bookDisplayService;
    private final LibraryService libraryService;
    private final DatabaseService databaseService;
    private final ScoreboardService scoreboardService;
    private final MaterialCatalog materialCatalog;
    private final MaterialIconCatalog materialIconCatalog;
    private final EnchantmentCatalog enchantmentCatalog;
    private final ItemMarkerCodec itemMarkerCodec;
    private final WebService webService;
    private final StorageService storageService;
    private final PathService pathService;
    private final PlayerBalanceService balanceService;
    private final PlayerPointsService playerPointsService;
    private final PlaceholderService placeholderService;
    private final ExecutorService asyncExecutor;
    private final Set<BukkitTask> scheduledTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile boolean closed;

    /**
     * Creates a Lib service for its owning plugin.
     *
     * @param owner    the plugin that owns the service lifecycle and scheduler tasks
     * @param poolSize the number of asynchronous worker threads
     * @throws NullPointerException     if {@code owner} is null
     * @throws IllegalArgumentException if {@code poolSize} is less than one
     */
    public DreamLibApi(JavaPlugin owner, int poolSize) {
        this(owner, poolSize, new PlayerLoadingGate(owner), new MenuService(owner));
    }

    /**
     * Creates a Lib service with a shared menu service.
     *
     * @param owner       the plugin that owns the service lifecycle and scheduler tasks
     * @param poolSize    the number of asynchronous worker threads
     * @param menuService the shared menu service
     */
    public DreamLibApi(JavaPlugin owner, int poolSize, MenuService menuService) {
        this(owner, poolSize, new PlayerLoadingGate(owner), menuService);
    }

    private DreamLibApi(
            JavaPlugin owner,
            int poolSize,
            PlayerLoadingGate playerLoadingGate,
            MenuService menuService
    ) {
        if (poolSize < 1) throw new IllegalArgumentException("poolSize must be positive");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.containerAnimationService = new PacketContainerAnimationService(owner);
        this.adminPanelService = new AdminPanelService(owner, menuService, containerAnimationService);
        this.floatingTextService = new DecentFloatingTextService(owner);
        this.playerLoadingGate = Objects.requireNonNull(playerLoadingGate, "playerLoadingGate");
        this.bookDisplayService = new LegacyBookDisplayService();
        this.libraryService = new LibraryService();
        this.databaseService = new DatabaseService();
        this.scoreboardService = new ScoreboardService();
        this.materialCatalog = MaterialCatalog.bundled();
        this.materialIconCatalog = new MaterialIconCatalog();
        this.enchantmentCatalog = EnchantmentCatalog.runtime();
        this.itemMarkerCodec = new ReflectiveItemMarkerCodec(owner.getLogger());
        this.asyncExecutor = Executors.newFixedThreadPool(poolSize, namedThreadFactory());
        this.webService = new WebService(asyncExecutor);
        this.storageService = new StorageService();
        this.pathService = new PathService();
        this.balanceService = new VaultEconomyBridge(owner);
        this.playerPointsService = new PlayerPointsBridge(owner);
        this.placeholderService = new PlaceholderApiBridge(owner);
    }

    /**
     * Returns the default asynchronous worker count bounded for a small Bukkit plugin runtime.
     *
     * @return a worker count between one and four
     */
    public static int defaultPoolSize() {
        return Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 4);
    }

    /**
     * Resolves the shared Lib service required by a dependent plugin.
     *
     * @param plugin the dependent plugin requesting the service
     * @return the registered Lib service
     * @throws NullPointerException  if {@code plugin} is null
     * @throws IllegalStateException if Lib is not registered or has no provider
     */
    public static DreamLibApi require(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        RegisteredServiceProvider<DreamLibApi> registration = plugin.getServer()
                .getServicesManager()
                .getRegistration(DreamLibApi.class);
        if (registration == null || registration.getProvider() == null) {
            throw new IllegalStateException(
                    "Lib service is unavailable; " + plugin.getDescription().getName() + " cannot start"
            );
        }
        return registration.getProvider();
    }

    /**
     * Returns the most useful message from a nested exception chain.
     *
     * @param throwable the exception whose root message should be returned
     * @return the deepest non-blank exception message, or the deepest exception type when no
     * message is available
     * @throws NullPointerException if {@code throwable} is null
     */
    public static String rootCauseMessage(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static <T> void completeOnMainThread(CompletableFuture<T> future, Supplier<T> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private static ThreadFactory namedThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "Lib-Async-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Runs an action on Lib's asynchronous executor.
     *
     * @param action the action to execute
     * @return a future completed when the action finishes
     * @throws NullPointerException if {@code action} is null
     */
    public CompletableFuture<Void> runAsync(Runnable action) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Lib API is closed"));
        return CompletableFuture.runAsync(action, asyncExecutor);
    }

    /**
     * Supplies a value on Lib's asynchronous executor.
     *
     * @param supplier the asynchronous value supplier
     * @param <T>      the supplied value type
     * @return a future completed with the supplied value
     * @throws NullPointerException if {@code supplier} is null
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Lib API is closed"));
        return CompletableFuture.supplyAsync(supplier, asyncExecutor);
    }

    /**
     * Runs an action on the Bukkit primary thread.
     *
     * @param action the action to execute
     * @return a future completed after the action finishes
     * @throws NullPointerException if {@code action} is null
     */
    public CompletableFuture<Void> runOnMain(Runnable action) {
        return scheduleOnMain(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Supplies a value on the Bukkit primary thread.
     *
     * @param supplier the primary-thread value supplier
     * @param <T>      the supplied value type
     * @return a future completed with the supplied value
     * @throws NullPointerException if {@code supplier} is null
     */
    public <T> CompletableFuture<T> supplyOnMain(Supplier<T> supplier) {
        return scheduleOnMain(supplier);
    }

    /**
     * Schedules an action on the Bukkit primary thread after a delay.
     *
     * @param delayTicks the scheduler delay in server ticks
     * @param action     the action to execute
     * @return the scheduled Bukkit task
     * @throws IllegalStateException if this service has been closed
     * @throws NullPointerException  if {@code action} is null
     */
    @SuppressWarnings("UnusedReturnValue")
    public BukkitTask runLater(long delayTicks, Runnable action) {
        if (closed) throw new IllegalStateException("Lib API is closed");

        BukkitTask task = owner.getServer().getScheduler().runTaskLater(owner, action, delayTicks);
        scheduledTasks.add(task);
        return task;
    }

    /**
     * Schedules a primary-thread repeating action owned by Lib.
     *
     * @param delayTicks  initial delay in server ticks
     * @param periodTicks repeat period in server ticks
     * @param action      the action to execute
     * @return the scheduled Bukkit task
     * @throws IllegalArgumentException if the delay is negative or the period is less than one
     * @throws IllegalStateException    if this service has been closed
     * @throws NullPointerException     if {@code action} is null
     */
    @SuppressWarnings("UnusedReturnValue")
    public BukkitTask runTimer(long delayTicks, long periodTicks, Runnable action) {
        if (delayTicks < 0L) throw new IllegalArgumentException("delayTicks cannot be negative");
        if (periodTicks < 1L) throw new IllegalArgumentException("periodTicks must be positive");
        if (closed) throw new IllegalStateException("Lib API is closed");

        BukkitTask task = owner.getServer().getScheduler().runTaskTimer(
                owner,
                action,
                delayTicks,
                periodTicks
        );
        scheduledTasks.add(task);
        return task;
    }

    /**
     * Creates a package-scoped annotation bootstrap for a dependent plugin.
     *
     * @param plugin      plugin being bootstrapped
     * @param basePackage package containing the plugin's Lib components
     * @return an unopened plugin bootstrap
     */
    public PluginBootstrap createPluginBootstrap(JavaPlugin plugin, String basePackage) {
        if (closed) throw new IllegalStateException("Lib API is closed");
        return new PluginBootstrap(plugin, this, basePackage);
    }

    /**
     * Returns the shared runtime library loader.
     *
     * @return runtime library service
     */
    public LibraryService libraryService() {
        return libraryService;
    }

    /**
     * Returns the shared JDBC database factory.
     *
     * @return database service
     */
    public DatabaseService databaseService() {
        return databaseService;
    }

    /**
     * Returns the shared scoreboard session lifecycle.
     *
     * @return scoreboard service
     */
    public ScoreboardService scoreboardService() {
        return scoreboardService;
    }

    /**
     * Returns the shared asynchronous executor.
     *
     * @return the executor owned by this service
     */
    public ExecutorService asyncExecutor() {
        return asyncExecutor;
    }

    /**
     * Starts a plugin-owned HTTP server using Lib's asynchronous executor.
     *
     * @param bindAddress address to bind
     * @param port        TCP port, or zero to select a free port
     * @param handler     request handler
     * @return a running HTTP server that the dependent plugin must close
     * @throws IOException if the listener cannot bind
     */
    public EmbeddedHttpServer startHttpServer(
            String bindAddress,
            int port,
            HttpHandler handler
    ) throws IOException {
        if (closed) throw new IllegalStateException("Lib API is closed");
        return EmbeddedHttpServer.start(bindAddress, port, handler, asyncExecutor);
    }

    /**
     * Returns the shared web infrastructure facade.
     *
     * @return web service
     */
    public WebService webService() {
        return webService;
    }

    /**
     * Returns the shared atomic storage facade.
     *
     * @return storage service
     */
    public StorageService storageService() {
        return storageService;
    }

    /**
     * Returns the shared safe path facade.
     *
     * @return path service
     */
    public PathService pathService() {
        return pathService;
    }

    /**
     * Returns the reflective Vault economy bridge.
     *
     * @return optional player balance service
     */
    public PlayerBalanceService balanceService() {
        return balanceService;
    }

    /**
     * Returns the reflective PlayerPoints bridge.
     *
     * @return optional player points service
     */
    public PlayerPointsService playerPointsService() {
        return playerPointsService;
    }

    /**
     * Returns the reflective PlaceholderAPI bridge.
     *
     * @return optional placeholder service
     */
    public PlaceholderService placeholderService() {
        return placeholderService;
    }

    /**
     * Returns the shared menu lifecycle service.
     *
     * @return the menu service owned by Lib
     */
    public MenuService menuService() {
        return menuService;
    }

    /**
     * Returns the shared administrator panel registry.
     *
     * @return administrator panel service
     */
    public AdminPanelService adminPanelService() {
        return adminPanelService;
    }

    /**
     * Returns the generic client-side container animation service.
     *
     * @return container animation service
     */
    public ContainerAnimationService containerAnimationService() {
        return containerAnimationService;
    }

    /**
     * Returns the shared DecentHolograms-backed floating-text service.
     *
     * @return floating-text service
     */
    public FloatingTextService floatingTextService() {
        return floatingTextService;
    }

    /**
     * Returns the shared player loading restriction service.
     *
     * @return loading gate
     */
    public PlayerLoadingGate playerLoadingGate() {
        return playerLoadingGate;
    }

    /**
     * Returns the shared client-side written book display service.
     */
    public BookDisplayService bookDisplayService() {
        return bookDisplayService;
    }

    /**
     * Returns the shared localized legacy Bukkit material catalog.
     *
     * @return the immutable material catalog bundled with Lib
     */
    public MaterialCatalog materialCatalog() {
        return materialCatalog;
    }

    /**
     * Returns the shared legacy material icon resolver.
     */
    public MaterialIconCatalog materialIconCatalog() {
        return materialIconCatalog;
    }

    /**
     * Returns the shared runtime Bukkit enchantment catalog.
     */
    public EnchantmentCatalog enchantmentCatalog() {
        return enchantmentCatalog;
    }

    /**
     * Returns the shared hidden item marker codec.
     *
     * @return version-isolated item marker codec
     */
    public ItemMarkerCodec itemMarkerCodec() {
        return itemMarkerCodec;
    }

    /**
     * Cancels tracked Bukkit tasks and shuts down the asynchronous executor.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;

        scheduledTasks.forEach(BukkitTask::cancel);
        scheduledTasks.clear();
        scoreboardService.close();
        playerLoadingGate.close();
        asyncExecutor.shutdownNow();
        adminPanelService.close();
        menuService.close();
        containerAnimationService.close();
        floatingTextService.close();
    }

    private <T> CompletableFuture<T> scheduleOnMain(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (Bukkit.isPrimaryThread()) {
            completeOnMainThread(future, supplier);
            return future;
        }

        try {
            BukkitTask task = owner.getServer().getScheduler().runTask(
                    owner,
                    () -> completeOnMainThread(future, supplier)
            );
            scheduledTasks.add(task);
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }
}
