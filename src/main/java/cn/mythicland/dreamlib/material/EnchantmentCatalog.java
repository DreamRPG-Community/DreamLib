package cn.mythicland.dreamlib.material;

import org.bukkit.enchantments.Enchantment;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Shared legacy Bukkit enchantment catalog with Chinese display names.
 */
public final class EnchantmentCatalog {

    private final List<EnchantmentEntry> entries;

    private EnchantmentCatalog(List<EnchantmentEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    /**
     * Builds a catalog from the runtime Bukkit 1.12 registry.
     */
    public static EnchantmentCatalog runtime() {
        return new EnchantmentCatalog(Arrays.stream(Enchantment.values())
                .filter(enchantment -> enchantment != null && enchantment.getName() != null)
                .map(enchantment -> new EnchantmentEntry(
                        enchantment.getName().toUpperCase(Locale.ROOT),
                        displayName(enchantment.getName()),
                        enchantment.getStartLevel(),
                        enchantment.getMaxLevel()
                ))
                .sorted(Comparator.comparing(EnchantmentEntry::displayName)
                        .thenComparing(EnchantmentEntry::key))
                .toList());
    }

    private static String displayName(String raw) {
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "PROTECTION_ENVIRONMENTAL" -> "保护";
            case "PROTECTION_FIRE" -> "火焰保护";
            case "PROTECTION_FALL" -> "摔落保护";
            case "PROTECTION_EXPLOSIONS" -> "爆炸保护";
            case "PROTECTION_PROJECTILE" -> "弹射物保护";
            case "OXYGEN" -> "水下呼吸";
            case "WATER_WORKER" -> "水下速掘";
            case "DAMAGE_ALL" -> "锋利";
            case "DAMAGE_UNDEAD" -> "亡灵杀手";
            case "DAMAGE_ARTHROPODS" -> "节肢杀手";
            case "KNOCKBACK" -> "击退";
            case "FIRE_ASPECT" -> "火焰附加";
            case "LOOT_BONUS_MOBS" -> "抢夺";
            case "DIG_SPEED" -> "效率";
            case "SILK_TOUCH" -> "精准采集";
            case "DURABILITY" -> "耐久";
            case "LOOT_BONUS_BLOCKS" -> "时运";
            case "ARROW_DAMAGE" -> "力量";
            case "ARROW_KNOCKBACK" -> "冲击";
            case "ARROW_FIRE" -> "火矢";
            case "ARROW_INFINITE" -> "无限";
            case "BINDING_CURSE" -> "绑定诅咒";
            case "DEPTH_STRIDER" -> "深海探索者";
            case "FROST_WALKER" -> "冰霜行者";
            case "MENDING" -> "经验修补";
            case "SWEEPING_EDGE" -> "横扫之刃";
            case "THORNS" -> "荆棘";
            case "VANISHING_CURSE" -> "消失诅咒";
            case "LUCK" -> "海之眷顾";
            case "LURE" -> "饵钓";
            default -> raw;
        };
    }

    public List<EnchantmentEntry> entries() {
        return entries;
    }
}
