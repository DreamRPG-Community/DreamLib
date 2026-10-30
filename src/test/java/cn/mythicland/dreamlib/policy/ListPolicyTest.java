package cn.mythicland.dreamlib.policy;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ListPolicyTest {

    @Test
    void blacklistBlocksOnlyListedValues() {
        ListPolicy<String> policy = new ListPolicy<>(ListMode.BLACKLIST, Set.of("tell"));

        assertTrue(policy.blocks("tell"));
        assertFalse(policy.blocks("say"));
    }

    @Test
    void whitelistBlocksOnlyUnlistedValues() {
        ListPolicy<Integer> policy = new ListPolicy<>(ListMode.WHITELIST, Set.of(54));

        assertFalse(policy.blocks(54));
        assertTrue(policy.blocks(58));
    }

    @Test
    void disabledPolicyAllowsEveryValue() {
        ListPolicy<String> policy = new ListPolicy<>(ListMode.DISABLED, Set.of("tell"));

        assertFalse(policy.blocks("tell"));
        assertFalse(policy.blocks("say"));
    }

    @Test
    void entriesAreImmutable() {
        ListPolicy<String> policy = new ListPolicy<>(ListMode.BLACKLIST, Set.of("tell"));

        assertThrows(UnsupportedOperationException.class, () -> policy.entries().add("say"));
        assertEquals(ListMode.BLACKLIST, policy.mode());
    }
}
