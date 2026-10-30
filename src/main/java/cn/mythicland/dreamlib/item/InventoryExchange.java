package cn.mythicland.dreamlib.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Plans an all-or-nothing exchange on detached inventory contents.
 * This does not change a live inventory or persist player data. Callers must commit a successful
 * result on the Bukkit primary thread without yielding between the snapshot and commit.
 */
public final class InventoryExchange {
    private InventoryExchange() {
    }

    public enum Status {
        SUCCESS, MISSING_ITEMS, NO_SPACE
    }

    /**
     * A detached outcome. Failed outcomes contain the unchanged source contents.
     *
     * @param status   planning outcome
     * @param contents complete inventory contents after a successful exchange
     */
    public record Result(Status status, ItemStack[] contents) {
        public Result {
            contents = copy(contents);
        }

        @Override
        public ItemStack[] contents() {
            return copy(contents);
        }
    }

    /**
     * Plans the greatest number of complete exchanges that fits in the source inventory.
     * Only the original source items are available as requirements; rewards are not reused.
     *
     * @param source       inventory slots available for the exchange
     * @param requirements item prototypes and quantities consumed per exchange
     * @param rewards      item stacks awarded per exchange
     * @param matches      comparison of an inventory item with a requirement prototype
     * @return detached plan, or the unchanged source with the reason no exchange fits
     * @throws IllegalArgumentException if requirements or rewards are empty or invalid
     * @throws NullPointerException     if source, requirements, rewards or matches is null
     */
    public static Result planMaximum(
            ItemStack[] source,
            List<ItemStack> requirements,
            List<ItemStack> rewards,
            BiPredicate<ItemStack, ItemStack> matches
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(matches, "matches");
        if (requirements.isEmpty() || rewards.isEmpty())
            throw new IllegalArgumentException("Exchange requires costs and rewards");
        requirements.forEach(InventoryExchange::validate);
        rewards.forEach(InventoryExchange::validate);
        int maximum = Integer.MAX_VALUE;
        for (ItemStack requirement : requirements) {
            long available = 0;
            for (ItemStack item : source) {
                if (!empty(item) && matches.test(item, requirement)) available += item.getAmount();
            }
            maximum = (int) Math.min(maximum, available / requirement.getAmount());
            maximum = Math.min(maximum, Integer.MAX_VALUE / requirement.getAmount());
        }
        for (ItemStack reward : rewards) maximum = Math.min(maximum, Integer.MAX_VALUE / reward.getAmount());
        boolean noSpace = false;
        for (int count = maximum; count > 0; count--) {
            Result result = plan(source, scale(requirements, count), scale(rewards, count), matches);
            if (result.status() == Status.SUCCESS) return result;
            if (result.status() == Status.NO_SPACE) noSpace = true;
        }
        return new Result(noSpace ? Status.NO_SPACE : Status.MISSING_ITEMS, source);
    }

    private static List<ItemStack> scale(List<ItemStack> items, int count) {
        return items.stream().map(item -> {
            ItemStack scaled = item.clone();
            scaled.setAmount(item.getAmount() * count);
            return scaled;
        }).toList();
    }

    /**
     * Consumes requirements across slots and inserts rewards using their real stack limits.
     * Requirements use the supplied identity rule; rewards stack only when Bukkit considers them similar.
     * No input item or array is mutated, including on failure.
     *
     * @param source       inventory slots available for the exchange
     * @param requirements item prototypes whose amounts are the quantities to consume
     * @param rewards      item stacks whose entire quantities must fit
     * @param matches      comparison of an inventory item with a requirement prototype
     * @return detached plan, including an explicit missing-material or full-inventory outcome
     * @throws IllegalArgumentException if a requirement or reward is empty or has an invalid amount
     */
    public static Result plan(
            ItemStack[] source,
            List<ItemStack> requirements,
            List<ItemStack> rewards,
            BiPredicate<ItemStack, ItemStack> matches
    ) {
        Objects.requireNonNull(matches, "matches");
        if (requirements.isEmpty() || rewards.isEmpty())
            throw new IllegalArgumentException("Exchange requires costs and rewards");
        requirements.forEach(InventoryExchange::validate);
        rewards.forEach(InventoryExchange::validate);
        ItemStack[] contents = copy(source);
        for (ItemStack requirement : requirements) {
            int remaining = requirement.getAmount();
            for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                ItemStack item = contents[slot];
                if (empty(item) || !matches.test(item, requirement)) continue;
                int removed = Math.min(remaining, item.getAmount());
                remaining -= removed;
                if (removed == item.getAmount()) contents[slot] = null;
                else item.setAmount(item.getAmount() - removed);
            }
            if (remaining > 0) return new Result(Status.MISSING_ITEMS, source);
        }
        for (ItemStack reward : rewards) {
            int remaining = reward.getAmount();
            int stackSize = Math.min(64, reward.getMaxStackSize());
            for (ItemStack item : contents) {
                if (empty(item) || !item.isSimilar(reward)) continue;
                int added = Math.min(remaining, Math.max(0, stackSize - item.getAmount()));
                item.setAmount(item.getAmount() + added);
                remaining -= added;
                if (remaining == 0) break;
            }
            for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                if (!empty(contents[slot])) continue;
                ItemStack item = reward.clone();
                item.setAmount(Math.min(remaining, stackSize));
                remaining -= item.getAmount();
                contents[slot] = item;
            }
            if (remaining > 0) return new Result(Status.NO_SPACE, source);
        }
        return new Result(Status.SUCCESS, contents);
    }

    private static void validate(ItemStack item) {
        if (empty(item)) throw new IllegalArgumentException("Exchange item must have a material and positive amount");
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() < 1;
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = Objects.requireNonNull(source, "source").clone();
        for (int slot = 0; slot < result.length; slot++) {
            if (result[slot] != null) result[slot] = result[slot].clone();
        }
        return result;
    }
}
