package io.github.lounode.ae2pattern.api;

import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;

import net.minecraft.world.item.ItemStack;

/**
 * A host's terminal view, built from the host's own rows followed by the rows of its pattern disks.
 *
 * <p>A host that keeps both slot patterns and pattern disks cannot hand the terminal
 * {@link PatternDiskTerminalView}'s rows alone: those are the disk rows only, so the host's own patterns
 * would vanish from the terminal. It cannot hand its own slots alone either, for the opposite reason. This
 * composes the two, and exists so that every host does not have to write that composition itself - the
 * routing has one non-obvious rule (see {@link #insertItem}) that is easy to get wrong, and getting it wrong
 * destroys patterns rather than failing loudly.</p>
 *
 * <p>Row layout: {@code [0, hostRows.size())} are the host's own rows, {@code [hostRows.size(), size())}
 * are the disk rows. That order is what the host's own row indices already mean, so a host can pass its
 * usual slot view unchanged.</p>
 *
 * <p><b>The host's rows are never written to from here.</b> A write aimed at a disk row is handed to the
 * disk view, which is the only party that knows what a write there costs - taking a recipe out of a disk
 * draws a blank pattern from the network, and putting one on draws nothing. Writes aimed at the host's own
 * rows go through the host view as usual.</p>
 *
 *
 */
public final class PatternDiskHostView extends BaseInternalInventory {

    private final InternalInventory hostRows;
    private final InternalInventory diskRows;

    private PatternDiskHostView(InternalInventory hostRows, InternalInventory diskRows) {
        this.hostRows = hostRows;
        this.diskRows = diskRows;
    }

    /**
     * @param hostRows the host's own terminal rows; its size is read once here and must not change while a
     *                 terminal session is open, the same rule the disk rows follow
     * @param disks    the disk rows, over the slots that hold this host's disks
     * @return the composed view, to be returned from
     *         {@link appeng.helpers.patternprovider.PatternContainer#getTerminalPatternInventory()}
     */
    public static PatternDiskHostView of(InternalInventory hostRows, PatternDiskTerminalView disks) {
        return new PatternDiskHostView(hostRows, disks.view());
    }

    /** @return the index in {@link #diskRows} for {@code slot}, or {@code -1} when it is a host row */
    private int diskIndex(int slot) {
        int index = slot - hostRows.size();
        return index >= 0 && index < diskRows.size() ? index : -1;
    }

    @Override
    public int size() {
        return hostRows.size() + diskRows.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        int disk = slot >= 0 ? diskIndex(slot) : -1;
        if (disk >= 0) {
            return diskRows.getStackInSlot(disk);
        }
        return slot >= 0 && slot < hostRows.size() ? hostRows.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setItemDirect(int slot, ItemStack stack) {
        int disk = diskIndex(slot);
        if (disk >= 0) {
            diskRows.setItemDirect(disk, stack);
        } else if (slot >= 0 && slot < hostRows.size()) {
            hostRows.setItemDirect(slot, stack);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Routed per row rather than delegated wholesale: the terminal takes a row out through the single-slot
     * view it gets here, and for a disk row that is where the blank pattern is drawn and the recipe is removed
     * from its disk.</p>
     */
    @Override
    public InternalInventory getSlotInv(int slot) {
        int disk = diskIndex(slot);
        if (disk >= 0) {
            return diskRows.getSlotInv(disk);
        }
        return slot >= 0 && slot < hostRows.size() ? hostRows.getSlotInv(slot) : InternalInventory.empty();
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int disk = diskIndex(slot);
        if (disk >= 0) {
            return diskRows.extractItem(disk, amount, simulate);
        }
        return slot >= 0 && slot < hostRows.size()
                ? hostRows.extractItem(slot, amount, simulate)
                : ItemStack.EMPTY;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overridden because the inherited implementation reports a write as done once {@link #setItemDirect}
     * returns, and a disk row does not take a write that way. Handing the write to the disk view keeps the
     * answer honest: it either lands on a disk, or comes back as the stack that was passed in.</p>
     */
    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        int disk = diskIndex(slot);
        if (disk >= 0) {
            return diskRows.insertItem(disk, stack, simulate);
        }
        return slot >= 0 && slot < hostRows.size() ? hostRows.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        int disk = diskIndex(slot);
        if (disk >= 0) {
            return diskRows.isItemValid(disk, stack);
        }
        return slot >= 0 && slot < hostRows.size() && hostRows.isItemValid(slot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        int disk = diskIndex(slot);
        if (disk >= 0) {
            return diskRows.getSlotLimit(disk);
        }
        return slot >= 0 && slot < hostRows.size() ? hostRows.getSlotLimit(slot) : 0;
    }

    /**
     * @return the view this one composes its disk rows from; {@code null} when there is none. Exposed so a
     *         host can tell whether a change it is looking at was a disk change, without keeping a second
     *         reference to the same rows.
     */
    public InternalInventory diskRows() {
        return diskRows;
    }
}
