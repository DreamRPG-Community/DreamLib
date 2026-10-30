package cn.mythicland.dreamlib.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;

/**
 * Serializes fixed-size Bukkit item arrays into database-safe Base64 strings.
 */
public final class ItemStackArrayCodec {

    private ItemStackArrayCodec() {
    }

    /**
     * Serializes an item array using Bukkit's object stream compatibility layer.
     *
     * @param items item array; individual entries may be empty
     * @return Base64 payload
     */
    public static String serialize(ItemStack[] items) {
        Objects.requireNonNull(items, "items");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeInt(items.length);
            for (ItemStack item : items) output.writeObject(item);
            output.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize Bukkit item array", exception);
        }
    }

    /**
     * Deserializes an item array and enforces the expected slot count.
     *
     * @param encoded        Base64 payload
     * @param expectedLength required number of slots
     * @return a detached item array
     */
    public static ItemStack[] deserialize(String encoded, int expectedLength) {
        String payload = Objects.requireNonNull(encoded, "encoded").trim();
        if (payload.isBlank()) throw new IllegalArgumentException("Encoded item array cannot be blank");
        if (expectedLength < 0) throw new IllegalArgumentException("expectedLength cannot be negative");
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Encoded item array is not valid Base64", exception);
        }
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            int actualLength = input.readInt();
            if (actualLength != expectedLength) {
                throw new IllegalArgumentException(
                        "Encoded item array length " + actualLength + " does not match " + expectedLength
                );
            }
            ItemStack[] items = new ItemStack[actualLength];
            for (int index = 0; index < actualLength; index++) {
                Object item = input.readObject();
                if (item != null && !(item instanceof ItemStack)) {
                    throw new IllegalArgumentException("Encoded item array contains a non-ItemStack value");
                }
                items[index] = (ItemStack) item;
            }
            return items;
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalArgumentException("Failed to deserialize Bukkit item array", exception);
        }
    }
}
