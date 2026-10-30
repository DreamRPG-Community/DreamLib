package cn.mythicland.dreamlib.container;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Paper 1.12.2 implementation of client-side block-action container animations.
 *
 * <p>NMS is intentionally isolated behind reflection so dependent plugin archives only depend on
 * the Bukkit API. The bridge is verified by DreamRPG before its data components are enabled.</p>
 */
public final class PacketContainerAnimationService implements ContainerAnimationService, Listener {

    private final Map<BlockKey, Map<UUID, AnimationSession>> sessionsByBlock = new HashMap<>();
    private final Map<UUID, Set<AnimationSession>> sessionsByViewer = new HashMap<>();
    private NmsBridge bridge;
    private boolean closed;

    /**
     * Creates the Lib-owned animation service.
     *
     * @param owner Lib plugin owning the listener lifecycle
     */
    public PacketContainerAnimationService(JavaPlugin owner) {
        Objects.requireNonNull(owner, "owner");
        owner.getServer().getPluginManager().registerEvents(this, owner);
    }

    private static void ensurePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Container animation must be opened on the Bukkit primary thread");
        }
    }

    @Override
    public void verifyCompatibility() {
        ensureBridge();
    }

    @Override
    @SuppressWarnings("resource")
    public ContainerAnimationHandle open(
            Block sourceBlock,
            Player viewer,
            ContainerAnimationSpec specification
    ) {
        ensurePrimaryThread();
        if (closed) throw new IllegalStateException("Container animation service is closed");
        Block block = Objects.requireNonNull(sourceBlock, "sourceBlock");
        Player target = Objects.requireNonNull(viewer, "viewer");
        ContainerAnimationSpec animation = Objects.requireNonNull(specification, "specification");
        ensureBridge();
        BlockKey key = BlockKey.from(block);
        Map<UUID, AnimationSession> blockSessions = sessionsByBlock.computeIfAbsent(
                key,
                ignored -> new HashMap<>()
        );
        if (blockSessions.containsKey(target.getUniqueId())) {
            throw new IllegalStateException(
                    "Container animation is already open for player " + target.getUniqueId()
            );
        }
        AnimationSession session = new AnimationSession(this, key, block, target, animation);
        blockSessions.put(target.getUniqueId(), session);
        sessionsByViewer.computeIfAbsent(target.getUniqueId(), ignored -> new HashSet<>()).add(session);
        try {
            sendAnimation(session, blockSessions.size());
            target.playSound(
                    block.getLocation(),
                    animation.openSound(),
                    animation.openVolume(),
                    animation.openPitch()
            );
        } catch (RuntimeException exception) {
            blockSessions.remove(target.getUniqueId());
            removeViewerSession(session);
            if (blockSessions.isEmpty()) sessionsByBlock.remove(key);
            throw exception;
        }
        return session;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        closeForViewer(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        closeForViewer(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        closeForViewer(event.getPlayer().getUniqueId(), true);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (AnimationSession session : List.copyOf(allSessions())) {
            closeSession(session, false);
        }
        sessionsByBlock.clear();
        sessionsByViewer.clear();
    }

    private Set<AnimationSession> allSessions() {
        Set<AnimationSession> sessions = new HashSet<>();
        for (Map<UUID, AnimationSession> blockSessions : sessionsByBlock.values()) {
            sessions.addAll(blockSessions.values());
        }
        return sessions;
    }

    private void closeForViewer(UUID uniqueId, boolean playSound) {
        Set<AnimationSession> viewerSessions = sessionsByViewer.get(uniqueId);
        if (viewerSessions == null) return;
        for (AnimationSession session : List.copyOf(viewerSessions)) {
            closeSession(session, playSound);
        }
    }

    @SuppressWarnings("resource")
    private void closeSession(AnimationSession session, boolean playSound) {
        if (session.closed) return;
        session.closed = true;
        Map<UUID, AnimationSession> blockSessions = sessionsByBlock.get(session.blockKey);
        if (blockSessions != null) {
            blockSessions.remove(session.viewer.getUniqueId());
            int remaining = blockSessions.size();
            if (blockSessions.isEmpty()) sessionsByBlock.remove(session.blockKey);
            sendAnimation(session, remaining);
        }
        removeViewerSession(session);
        if (playSound && session.viewer.isOnline()) {
            ContainerAnimationSpec animation = session.specification;
            session.viewer.playSound(
                    session.block.getLocation(),
                    animation.closeSound(),
                    animation.closeVolume(),
                    animation.closePitch()
            );
        }
    }

    private void removeViewerSession(AnimationSession session) {
        Set<AnimationSession> viewerSessions = sessionsByViewer.get(session.viewer.getUniqueId());
        if (viewerSessions == null) return;
        viewerSessions.remove(session);
        if (viewerSessions.isEmpty()) sessionsByViewer.remove(session.viewer.getUniqueId());
    }

    private void sendAnimation(AnimationSession session, int viewerCount) {
        if (viewerCount < 0) throw new IllegalArgumentException("viewerCount cannot be negative");
        ContainerAnimationSpec animation = session.specification;
        List<Player> viewers = nearbyPlayers(session.block, animation.broadcastDistance());
        Object packet = bridge.createPacket(session.block, animation.blockAction(), viewerCount);
        for (Player player : viewers) bridge.sendPacket(player, packet);
    }

    private List<Player> nearbyPlayers(Block block, double distance) {
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        double distanceSquared = distance * distance;
        List<Player> viewers = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= distanceSquared) viewers.add(player);
        }
        return viewers;
    }

    private NmsBridge ensureBridge() {
        if (bridge != null) return bridge;
        bridge = NmsBridge.create();
        return bridge;
    }

    private static final class AnimationSession implements ContainerAnimationHandle {

        private final PacketContainerAnimationService service;
        private final BlockKey blockKey;
        private final Block block;
        private final Player viewer;
        private final ContainerAnimationSpec specification;
        private boolean closed;

        private AnimationSession(
                PacketContainerAnimationService service,
                BlockKey blockKey,
                Block block,
                Player viewer,
                ContainerAnimationSpec specification
        ) {
            this.service = service;
            this.blockKey = blockKey;
            this.block = block;
            this.viewer = viewer;
            this.specification = specification;
        }

        @Override
        public void close() {
            if (closed) return;
            service.closeSession(this, true);
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {

        private static BlockKey from(Block block) {
            World world = Objects.requireNonNull(block.getWorld(), "block.world");
            return new BlockKey(world.getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private record NmsBridge(Method craftWorldGetHandle, Method worldGetType, Method blockDataGetBlock,
                             Constructor<?> blockPositionConstructor, Constructor<?> packetConstructor,
                             Method craftPlayerGetHandle, Field playerConnectionField, Method sendPacket) {

        private static NmsBridge create() {
            try {
                String craftVersion = Bukkit.getServer().getClass().getPackage().getName();
                String version = craftVersion.substring(craftVersion.lastIndexOf('.') + 1);
                String nmsPackage = "net.minecraft.server." + version;
                Class<?> craftWorldType = Class.forName(craftVersion + ".CraftWorld");
                Class<?> craftPlayerType = Class.forName(craftVersion + ".entity.CraftPlayer");
                Class<?> blockPositionType = Class.forName(nmsPackage + ".BlockPosition");
                Class<?> blockType = Class.forName(nmsPackage + ".Block");
                Class<?> packetType = Class.forName(nmsPackage + ".Packet");
                Class<?> blockActionPacketType = Class.forName(nmsPackage + ".PacketPlayOutBlockAction");
                Method craftWorldGetHandle = craftWorldType.getMethod("getHandle");
                Class<?> worldServerType = craftWorldGetHandle.getReturnType();
                Method worldGetType = worldServerType.getMethod("getType", blockPositionType);
                Method blockDataGetBlock = worldGetType.getReturnType().getMethod("getBlock");
                Constructor<?> blockPositionConstructor = blockPositionType.getConstructor(
                        int.class,
                        int.class,
                        int.class
                );
                Constructor<?> packetConstructor = blockActionPacketType.getConstructor(
                        blockPositionType,
                        blockType,
                        int.class,
                        int.class
                );
                Method craftPlayerGetHandle = craftPlayerType.getMethod("getHandle");
                Field playerConnectionField = craftPlayerGetHandle.getReturnType().getField("playerConnection");
                Method sendPacket = playerConnectionField.getType().getMethod("sendPacket", packetType);
                return new NmsBridge(
                        craftWorldGetHandle,
                        worldGetType,
                        blockDataGetBlock,
                        blockPositionConstructor,
                        packetConstructor,
                        craftPlayerGetHandle,
                        playerConnectionField,
                        sendPacket
                );
            } catch (ReflectiveOperationException | RuntimeException exception) {
                throw new IllegalStateException(
                        "Paper 1.12.2 NMS container animation bridge is unavailable",
                        exception
                );
            }
        }

        private Object createPacket(Block block, int action, int viewerCount) {
            try {
                Object worldServer = craftWorldGetHandle.invoke(block.getWorld());
                Object position = blockPositionConstructor.newInstance(
                        block.getX(),
                        block.getY(),
                        block.getZ()
                );
                Object blockData = worldGetType.invoke(worldServer, position);
                Object nmsBlock = blockDataGetBlock.invoke(blockData);
                return packetConstructor.newInstance(position, nmsBlock, action, viewerCount);
            } catch (IllegalAccessException | InvocationTargetException | InstantiationException exception) {
                throw new IllegalStateException(
                        "Failed to construct the NMS container animation packet",
                        exception
                );
            }
        }

        private void sendPacket(Player player, Object packet) {
            try {
                Object handle = craftPlayerGetHandle.invoke(player);
                Object connection = playerConnectionField.get(handle);
                sendPacket.invoke(connection, packet);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException(
                        "Failed to send the NMS container animation packet to " + player.getName(),
                        exception
                );
            }
        }
    }
}
