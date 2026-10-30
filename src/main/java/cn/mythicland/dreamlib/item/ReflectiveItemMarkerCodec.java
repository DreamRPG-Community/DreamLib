package cn.mythicland.dreamlib.item;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Paper 1.12-compatible NBT marker adapter. Version-specific NMS names are isolated here so
 * domain plugins can use hidden item identity without depending on CraftBukkit classes.
 */
public final class ReflectiveItemMarkerCodec implements ItemMarkerCodec {

    private static final String SCHEMA_KEY = "schema";

    private final Logger logger;
    private volatile NmsAccess cachedAccess;
    private volatile boolean unavailableLogged;

    /**
     * Creates a marker codec using the supplied logger for bridge failures.
     *
     * @param logger owning plugin logger
     */
    public ReflectiveItemMarkerCodec(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private static String requireNamespace(String value) {
        String namespace = Objects.requireNonNull(value, "namespace").trim();
        if (namespace.isBlank()) throw new IllegalArgumentException("namespace cannot be blank");
        return namespace;
    }

    @Override
    public ItemStack write(ItemStack source, ItemMarker marker) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(marker, "marker");
        try {
            NmsAccess access = access();
            Object nmsStack = access.asNmsCopy().invoke(null, source);
            Object root = access.getTag().invoke(nmsStack);
            if (root == null) root = access.compoundConstructor().newInstance();
            Object markerCompound = access.compoundConstructor().newInstance();
            access.setInt().invoke(markerCompound, SCHEMA_KEY, marker.schema());
            for (Map.Entry<String, String> entry : marker.values().entrySet()) {
                access.setString().invoke(markerCompound, entry.getKey(), entry.getValue());
            }
            access.set().invoke(root, marker.namespace(), markerCompound);
            access.setTag().invoke(nmsStack, root);
            return (ItemStack) access.asBukkitCopy().invoke(null, nmsStack);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logUnavailable(exception);
            throw new IllegalStateException("Hidden item marker NBT is unavailable", exception);
        }
    }

    @Override
    public Optional<ItemMarker> read(ItemStack source, String namespace) {
        if (source == null) return Optional.empty();
        String normalizedNamespace = requireNamespace(namespace);
        try {
            NmsAccess access = access();
            Object nmsStack = access.asNmsCopy().invoke(null, source);
            Object root = access.getTag().invoke(nmsStack);
            if (root == null || !(boolean) access.hasKey().invoke(root, normalizedNamespace)) {
                return Optional.empty();
            }
            Object markerCompound = access.getCompound().invoke(root, normalizedNamespace);
            int schema = (int) access.getInt().invoke(markerCompound, SCHEMA_KEY);
            if (schema < 1) return Optional.empty();
            Map<String, String> values = new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Set<String> keys = (Set<String>) access.keys().invoke(markerCompound);
            for (String key : keys) {
                if (!SCHEMA_KEY.equals(key)) values.put(key, (String) access.getString().invoke(markerCompound, key));
            }
            return Optional.of(new ItemMarker(normalizedNamespace, schema, values));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logUnavailable(exception);
            throw new IllegalStateException("Hidden item marker NBT is unavailable", exception);
        }
    }

    private NmsAccess access() throws ReflectiveOperationException {
        NmsAccess cached = cachedAccess;
        if (cached != null) return cached;
        String serverPackage = Bukkit.getServer().getClass().getPackage().getName();
        String craftPackage = serverPackage.substring(serverPackage.lastIndexOf('.') + 1);
        Class<?> craftItemStack = Class.forName(
                "org.bukkit.craftbukkit." + craftPackage + ".inventory.CraftItemStack"
        );
        Class<?> nmsStack = Class.forName("net.minecraft.server." + craftPackage + ".ItemStack");
        Class<?> compound = Class.forName("net.minecraft.server." + craftPackage + ".NBTTagCompound");
        Method asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
        Method asBukkitCopy = craftItemStack.getMethod("asBukkitCopy", nmsStack);
        Method getTag = findMethod(nmsStack, "getTag", 0);
        Method setTag = findMethod(nmsStack, "setTag", 1);
        Method hasKey = findMethod(compound, "hasKey", 1);
        Method getCompound = findMethod(compound, "getCompound", 1);
        Method getInt = findMethod(compound, "getInt", 1);
        Method getString = findMethod(compound, "getString", 1);
        Method keys = findMethod(compound, "c", 0);
        Method setString = findMethod(compound, "setString", 2);
        Method setInt = findMethod(compound, "setInt", 2);
        Method set = Arrays.stream(compound.getMethods())
                .filter(method -> method.getName().equals("set"))
                .filter(method -> method.getParameterCount() == 2)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("NBTTagCompound#set"));
        Constructor<?> constructor = compound.getConstructor();
        NmsAccess resolved = new NmsAccess(
                asNmsCopy,
                asBukkitCopy,
                getTag,
                setTag,
                hasKey,
                getCompound,
                getInt,
                getString,
                keys,
                setString,
                setInt,
                set,
                constructor
        );
        cachedAccess = resolved;
        return resolved;
    }

    private Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        return Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> method.getParameterCount() == parameterCount)
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(type.getName() + "#" + name));
    }

    private void logUnavailable(Exception exception) {
        if (unavailableLogged) return;
        unavailableLogged = true;
        logger.log(Level.WARNING, "Hidden item marker NBT is unavailable; item synchronization cannot run", exception);
    }

    private record NmsAccess(
            Method asNmsCopy,
            Method asBukkitCopy,
            Method getTag,
            Method setTag,
            Method hasKey,
            Method getCompound,
            Method getInt,
            Method getString,
            Method keys,
            Method setString,
            Method setInt,
            Method set,
            Constructor<?> compoundConstructor
    ) {
    }
}
