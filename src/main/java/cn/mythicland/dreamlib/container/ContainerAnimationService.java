package cn.mythicland.dreamlib.container;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Sends version-specific client container animations without exposing NMS types to consumers.
 */
public interface ContainerAnimationService extends AutoCloseable {

    /**
     * Verifies that the running server exposes the supported NMS packet bridge.
     *
     * @throws IllegalStateException when the running server is not supported
     */
    void verifyCompatibility();

    /**
     * Opens a client-side animation for a real block and one viewer.
     *
     * @param sourceBlock   source container block
     * @param viewer        player receiving the interaction sound
     * @param specification animation parameters
     * @return handle that must be closed when the container is closed
     */
    ContainerAnimationHandle open(
            Block sourceBlock,
            Player viewer,
            ContainerAnimationSpec specification
    );

    /**
     * Releases all tracked animation sessions.
     */
    @Override
    void close();
}
