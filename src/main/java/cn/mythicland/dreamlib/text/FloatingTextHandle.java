package cn.mythicland.dreamlib.text;

import org.bukkit.Location;

/**
 * Lifecycle handle for one client-side floating-text display.
 */
public interface FloatingTextHandle extends AutoCloseable {

    /**
     * Replaces the text for every current and future viewer without recreating the display when
     * the line geometry is unchanged.
     *
     * @param specification replacement specification
     * @throws NullPointerException  if {@code specification} is null
     * @throws IllegalStateException if called off the Bukkit primary thread
     */
    void update(FloatingTextSpec specification);

    /**
     * Moves the display anchor and all of its lines without recreating the display.
     *
     * @param location replacement anchor location
     * @throws NullPointerException     if {@code location} is null
     * @throws IllegalArgumentException if the location has no world
     * @throws IllegalStateException    if called off the Bukkit primary thread
     */
    void move(Location location);

    /**
     * Removes the fake client-side entities from every viewer.
     *
     * <p>Closing an already closed handle is safe and has no effect.</p>
     *
     * @throws IllegalStateException if called off the Bukkit primary thread
     */
    @Override
    void close();
}
