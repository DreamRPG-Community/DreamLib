package cn.mythicland.dreamlib.book;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Paper 1.12.2 client-side book display using temporary inventory packets.
 */
public final class LegacyBookDisplayService implements BookDisplayService {
    @Override
    public void open(Player player, ItemStack book) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Book display requires the main thread");
        Player target = Objects.requireNonNull(player, "player");
        ItemStack written = Objects.requireNonNull(book, "book");
        if (written.getType() != Material.WRITTEN_BOOK) throw new IllegalArgumentException("Expected a written book");
        if (!target.isOnline()) throw new IllegalStateException("Player is offline");
        try {
            String serverPackage = Bukkit.getServer().getClass().getPackage().getName();
            String version = serverPackage.substring(serverPackage.lastIndexOf('.') + 1);
            if (!version.equals("v1_12_R1"))
                throw new IllegalStateException("Unsupported book display server: " + version);
            String nms = "net.minecraft.server." + version + ".";
            Class<?> stackClass = Class.forName(nms + "ItemStack");
            Class<?> packetClass = Class.forName(nms + "Packet");
            Class<?> handClass = Class.forName(nms + "EnumHand");
            Class<?> entityClass = Class.forName(nms + "EntityPlayer");
            Class<?> connectionClass = Class.forName(nms + "PlayerConnection");
            Class<?> craftStack = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Method copy = craftStack.getMethod("asNMSCopy", ItemStack.class);
            Object handle = target.getClass().getMethod("getHandle").invoke(target);
            Object connection = entityClass.getField("playerConnection").get(handle);
            Method send = connectionClass.getMethod("sendPacket", packetClass);
            Constructor<?> slotPacket = Class.forName(nms + "PacketPlayOutSetSlot")
                    .getConstructor(int.class, int.class, stackClass);
            Object temporary = copy.invoke(null, written);
            Object original = copy.invoke(null, target.getInventory().getItemInMainHand());
            int slot = 36 + target.getInventory().getHeldItemSlot();
            send.invoke(connection, slotPacket.newInstance(0, slot, temporary));
            try {
                entityClass.getMethod("a", stackClass, handClass)
                        .invoke(handle, temporary, handClass.getField("MAIN_HAND").get(null));
            } finally {
                send.invoke(connection, slotPacket.newInstance(0, slot, original));
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot open a client book on Paper 1.12.2", exception);
        }
    }
}
