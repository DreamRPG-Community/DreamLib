package cn.mythicland.dreamlib.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventoryExchangeTest {
    @Test
    void bulkExchangeUsesMaximumThatFitsAndKeepsRemainder() {
        ItemStack[] source = {new ItemStack(Material.STONE, 10), new ItemStack(Material.DIRT, 64), null};
        var result = InventoryExchange.planMaximum(source, List.of(new ItemStack(Material.STONE, 2)),
                List.of(new ItemStack(Material.GOLD_INGOT, 30)), (item, cost) -> item.getType() == cost.getType());
        assertEquals(InventoryExchange.Status.SUCCESS, result.status());
        assertEquals(6, result.contents()[0].getAmount());
        assertEquals(60, result.contents()[2].getAmount());
        assertEquals(10, source[0].getAmount());
    }

    @Test
    void bulkExchangeCanFreeSpaceEvenWhenOneExchangeCannot() {
        ItemStack[] source = {new ItemStack(Material.STONE, 4)};
        var result = InventoryExchange.planMaximum(source, List.of(new ItemStack(Material.STONE, 2)),
                List.of(new ItemStack(Material.GOLD_INGOT)), (item, cost) -> item.getType() == cost.getType());
        assertEquals(InventoryExchange.Status.SUCCESS, result.status());
        assertEquals(Material.GOLD_INGOT, result.contents()[0].getType());
        assertEquals(2, result.contents()[0].getAmount());
    }

    @Test
    void bulkExchangeReportsMissingItemsAndInsufficientSpace() {
        var cost = List.of(new ItemStack(Material.STONE, 2));
        var reward = List.of(new ItemStack(Material.GOLD_INGOT, 65));
        assertEquals(InventoryExchange.Status.MISSING_ITEMS, InventoryExchange.planMaximum(
                new ItemStack[]{new ItemStack(Material.STONE)}, cost, reward,
                (item, requirement) -> item.getType() == requirement.getType()).status());
        assertEquals(InventoryExchange.Status.NO_SPACE, InventoryExchange.planMaximum(
                new ItemStack[]{new ItemStack(Material.STONE, 2)}, cost, reward,
                (item, requirement) -> item.getType() == requirement.getType()).status());
    }

    @Test
    void bulkExchangeAccountsForRequirementsThatMatchTheSameItems() {
        var result = InventoryExchange.planMaximum(new ItemStack[]{new ItemStack(Material.STONE, 10), null},
                List.of(new ItemStack(Material.STONE, 2), new ItemStack(Material.STONE, 3)),
                List.of(new ItemStack(Material.GOLD_INGOT)), (item, cost) -> item.getType() == cost.getType());
        assertEquals(InventoryExchange.Status.SUCCESS, result.status());
        assertEquals(2, result.contents()[0].getAmount());
        assertEquals(Material.GOLD_INGOT, result.contents()[0].getType());
    }
    @Test
    void consumesAcrossSlotsAndUsesTheFreedSpaceWithoutMutatingInputs() {
        ItemStack[] source = {new ItemStack(Material.STONE, 3), new ItemStack(Material.STONE, 4)};
        var result = InventoryExchange.plan(source, List.of(new ItemStack(Material.STONE, 7)),
                List.of(new ItemStack(Material.GOLD_INGOT, 2)), (item, cost) -> item.getType() == cost.getType());
        assertEquals(InventoryExchange.Status.SUCCESS, result.status());
        assertEquals(2, result.contents()[0].getAmount());
        assertNull(result.contents()[1]);
        assertEquals(3, source[0].getAmount());
        assertEquals(4, source[1].getAmount());
        result.contents()[0].setAmount(12);
        assertEquals(2, result.contents()[0].getAmount());
    }

    @Test
    void missingSecondRequirementDoesNotConsumeTheFirst() {
        ItemStack[] source = {new ItemStack(Material.STONE, 5)};
        var result = InventoryExchange.plan(source,
                List.of(new ItemStack(Material.STONE, 3), new ItemStack(Material.DIAMOND)),
                List.of(new ItemStack(Material.GOLD_INGOT)), (item, cost) -> item.getType() == cost.getType());
        assertEquals(InventoryExchange.Status.MISSING_ITEMS, result.status());
        assertEquals(5, result.contents()[0].getAmount());
    }

    @Test
    void insufficientRewardSpaceReturnsTheOriginalContents() {
        ItemStack[] source = {new ItemStack(Material.STONE, 5)};
        var result = InventoryExchange.plan(source, List.of(new ItemStack(Material.STONE, 1)),
                List.of(new ItemStack(Material.GOLD_INGOT)), (item, cost) -> item.getType() == cost.getType());
        assertEquals(InventoryExchange.Status.NO_SPACE, result.status());
        assertEquals(5, result.contents()[0].getAmount());
    }

    @Test
    void splitsRewardsAtTheirMaterialStackLimitAndRejectsFreeExchanges() {
        var result = InventoryExchange.plan(new ItemStack[]{new ItemStack(Material.STONE), null},
                List.of(new ItemStack(Material.STONE)), List.of(new ItemStack(Material.GOLD_INGOT, 70)),
                (item, cost) -> item.getType() == cost.getType());
        assertEquals(64, result.contents()[0].getAmount());
        assertEquals(6, result.contents()[1].getAmount());
        assertThrows(IllegalArgumentException.class, () -> InventoryExchange.plan(new ItemStack[1], List.of(),
                List.of(new ItemStack(Material.DIAMOND)), (item, cost) -> true));
    }
}
