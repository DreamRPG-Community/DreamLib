package cn.mythicland.dreamlib.container;

import org.bukkit.Sound;

import java.util.Objects;

/**
 * Immutable parameters for one Bukkit block-action container animation.
 *
 * @param blockAction       NMS block-action identifier
 * @param openSound         sound played to the opening viewer
 * @param closeSound        sound played to the closing viewer
 * @param openVolume        opening sound volume
 * @param openPitch         opening sound pitch
 * @param closeVolume       closing sound volume
 * @param closePitch        closing sound pitch
 * @param broadcastDistance maximum packet broadcast distance in blocks
 */
public record ContainerAnimationSpec(
        int blockAction,
        Sound openSound,
        Sound closeSound,
        float openVolume,
        float openPitch,
        float closeVolume,
        float closePitch,
        double broadcastDistance
) {

    /**
     * Validates animation parameters.
     */
    @SuppressWarnings("DataFlowIssue")
    public ContainerAnimationSpec {
        if (blockAction < 0) throw new IllegalArgumentException("blockAction cannot be negative");
        openSound = Objects.requireNonNull(openSound, "openSound");
        closeSound = Objects.requireNonNull(closeSound, "closeSound");
        validateSoundValue(openVolume, "openVolume");
        validateSoundValue(openPitch, "openPitch");
        validateSoundValue(closeVolume, "closeVolume");
        validateSoundValue(closePitch, "closePitch");
        if (!Double.isFinite(broadcastDistance) || broadcastDistance <= 0.0D) {
            throw new IllegalArgumentException("broadcastDistance must be finite and positive");
        }
    }

    /**
     * Returns the 1.12.2 vanilla ender-chest animation parameters.
     *
     * @return vanilla ender-chest animation
     */
    public static ContainerAnimationSpec enderChest() {
        return new ContainerAnimationSpec(
                1,
                Sound.BLOCK_ENDERCHEST_OPEN,
                Sound.BLOCK_ENDERCHEST_CLOSE,
                1.0F,
                1.0F,
                1.0F,
                0.8F,
                64.0D
        );
    }

    private static void validateSoundValue(float value, String fieldName) {
        if (!Float.isFinite(value) || value < 0.0F) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
    }
}
