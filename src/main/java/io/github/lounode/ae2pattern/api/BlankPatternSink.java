package io.github.lounode.ae2pattern.api;

/**
 * The ME network side of writing a pattern to a disk: writing one owes the network a blank pattern back,
 * and a caller that has no network to charge can leave this {@code null}.
 *
 * <p>This is the accounting half of {@link PatternDiskApi#insert}. The rule is asked <em>before</em>
 * anything is written - {@link #hasRoomForBlankPatterns} - so a network that cannot take a blank pattern
 * back makes the write fail with the pattern still in the caller's hands, instead of letting one
 * evaporate.</p>
 *
 * <p>Implementations are supplied by whoever owns the network: this mod's own provider view passes one,
 * and a machine from another mod that keeps disks in its own slots passes its own. Only
 * {@link #drawBlankPatterns} has to be implemented; the three checks default to permissive so a sink
 * without a pre-check or a restore path stays usable.</p>
 */
public interface BlankPatternSink {

    /** Draws {@code count} blank patterns from the attached ME network, all-or-nothing. */
    boolean drawBlankPatterns(int count);

    /**
     * Read-only check: whether the attached ME network currently holds at least {@code count}
     * blank patterns ({@code = "can we draw?"}). Defaults to {@code true} (compatible with sinks
     * without a pre-check).
     *
     * @see #hasRoomForBlankPatterns(int) the opposite question - can the network *take one back*
     */
    default boolean hasBlankPatterns(int count) {
        return true;
    }

    /**
     * Returns {@code count} blank patterns to the attached ME network (undo of
     * {@link #drawBlankPatterns}). Called when a swap restore re-inserts a just-taken pattern.
     * Defaults to a no-op for sinks without a restore path.
     */
    default boolean returnBlankPatterns(int count) {
        return true;
    }

    /**
     * Read-only check: whether the attached ME network could take {@code count} blank patterns right
     * now ({@code = "could we return one?"}). Write paths that owe the network a blank pattern ask this
     * <em>before</em> mutating anything, so a network that cannot take it back makes the write fail
     * instead of quietly eating the pattern. Defaults to {@code true} (compatible with sinks without a
     * pre-check).
     *
     * @see #hasBlankPatterns(int) the opposite question - does the network *hold* one to draw
     */
    default boolean hasRoomForBlankPatterns(int count) {
        return true;
    }
}
