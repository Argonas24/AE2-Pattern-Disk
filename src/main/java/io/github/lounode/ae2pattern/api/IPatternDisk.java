package io.github.lounode.ae2pattern.api;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A pattern disk, as seen by code that must not name the concrete item - this mod's own disk
 * inventory, and any addon that wants to treat a slot as "something that holds patterns".
 *
 * <p>Implementing this is not required to <em>use</em> a disk: {@link PatternDiskApi#contents(ItemStack)}
 * answers the same questions for any stack. This interface exists so a caller can hold a disk-typed
 * reference without depending on {@code common.item.PatternDiskItem}.</p>
 *
 * <p>It is a <em>reader</em>-side type. Implementing it on some other item does not make that item fit this
 * mod's disk slots - the slot filters ask for the disk item itself - so an implementation outside this mod
 * only affects callers that were handed the item anyway.</p>
 *
 * <p>{@link PatternDiskContents} is an immutable snapshot; every write returns a new record rather than
 * editing one in place. A disk that refuses a write returns {@code false} from {@link #tryInsert} without
 * having changed anything.</p>
 */
public interface IPatternDisk {

    /**
     * @return what {@code stack} holds; an empty contents sized to this disk's capacity when it holds
     *         nothing yet - never {@code null}
     */
    PatternDiskContents contents(ItemStack stack);

    /**
     * Whether {@code pattern} could be written to {@code disk}: capacity, the disk's locked type and the
     * duplicate-output rule all have to pass. Answering {@code true} does not reserve the slot.
     */
    boolean canInsert(ItemStack disk, ItemStack pattern, Level level);

    /**
     * Writes {@code pattern} to {@code disk}, exactly as {@link #canInsert} would allow.
     *
     * @return whether anything was written; {@code false} leaves {@code disk} untouched
     */
    boolean tryInsert(ItemStack disk, ItemStack pattern, Level level);
}
