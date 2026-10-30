package cn.mythicland.dreamlib.location;

import org.bukkit.Location;

/**
 * Snaps administrator-defined locations to the server's standard block-center and view-angle
 * representation.
 */
public final class LocationSnapper {

    private static final float VIEW_STEP = 45.0F;

    private LocationSnapper() {
    }

    /**
     * Snaps X/Z to block centers, floors standing Y, rounds yaw to 45 degrees and clears pitch.
     * The returned location is a new object and never mutates the input.
     *
     * @param location source location
     * @return snapped defensive copy
     * @throws IllegalArgumentException if the location or its world is missing
     */
    public static Location snapBlockAndView(Location location) {
        return snapBlockAndHorizontalView(location);
    }

    /**
     * Snaps X and Z to the center of their blocks while preserving the source view angles.
     * This is useful when a relative direction must be calculated from stable block centers.
     * Y remains unchanged.
     *
     * @param location source location
     * @return snapped defensive copy
     * @throws IllegalArgumentException if the location or its world is missing
     */
    public static Location snapBlockCenter(Location location) {
        if (location == null) throw new IllegalArgumentException("location must not be null");
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("location world must not be null");
        }

        return new Location(
                location.getWorld(),
                Math.floor(location.getX()) + 0.5D,
                location.getY(),
                Math.floor(location.getZ()) + 0.5D,
                location.getYaw(),
                location.getPitch()
        );
    }

    /**
     * Snaps X and Z to the center of their blocks, rounds yaw to a 45-degree step, and clears
     * pitch. This is useful for NPCs that should face a player horizontally without looking up
     * or down. Standing Y is floored to a block boundary.
     *
     * @param location source location
     * @return snapped defensive copy with a zero pitch
     * @throws IllegalArgumentException if the location or its world is missing
     */
    public static Location snapBlockAndHorizontalView(Location location) {
        if (location == null) throw new IllegalArgumentException("location must not be null");
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("location world must not be null");
        }

        if (!Float.isFinite(location.getPitch())) throw new IllegalArgumentException("pitch must be finite");
        return new Location(
                location.getWorld(),
                snapHorizontalCoordinate(location.getX()),
                snapStandingHeight(location.getY()),
                snapHorizontalCoordinate(location.getZ()),
                snapYaw(location.getYaw()),
                0.0F
        );
    }

    /**
     * Centers a finite horizontal coordinate, including negative coordinates.
     * This pure operation is safe on any thread and retains no state.
     * @param coordinate finite X or Z
     * @return containing block center
     * @throws IllegalArgumentException for non-finite input
     */
    public static double snapHorizontalCoordinate(double coordinate) {
        if (!Double.isFinite(coordinate)) throw new IllegalArgumentException("coordinate must be finite");
        return Math.floor(coordinate) + 0.5D;
    }

    /**
     * Floors a finite standing height to a block boundary, keeping feet above the floor.
     * This pure operation is safe on any thread and retains no state.
     * @param height finite standing Y
     * @return integer standing height
     * @throws IllegalArgumentException for non-finite input
     */
    public static double snapStandingHeight(double height) {
        if (!Double.isFinite(height)) throw new IllegalArgumentException("height must be finite");
        return Math.floor(height);
    }

    /**
     * Rounds a finite yaw to the nearest 45-degree step in (-180, 180].
     * This pure operation is safe on any thread and retains no state.
     * @param yaw finite horizontal view angle
     * @return snapped yaw
     * @throws IllegalArgumentException for non-finite input
     */
    public static float snapYaw(float yaw) {
        if (!Float.isFinite(yaw)) throw new IllegalArgumentException("yaw must be finite");
        return normalizeYaw(snapToStep(yaw % 360.0F));
    }

    private static float snapToStep(float angle) {
        return Math.round(angle / VIEW_STEP) * VIEW_STEP;
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized <= -180.0F) normalized += 360.0F;
        else if (normalized > 180.0F) normalized -= 360.0F;
        return normalized;
    }

}
