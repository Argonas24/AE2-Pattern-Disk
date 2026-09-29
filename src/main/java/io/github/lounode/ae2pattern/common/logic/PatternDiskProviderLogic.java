package io.github.lounode.ae2pattern.common.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;

import io.github.lounode.ae2pattern.common.item.PatternDiskItem;
import io.github.lounode.ae2pattern.common.pattern.PatternDiskTier;
import io.github.lounode.ae2pattern.api.PatternDiskContents;

/**
 * A {@link PatternProviderLogic} whose available patterns are expanded from the contents of inserted
 * pattern disks, instead of a fixed slot-per-pattern inventory.
 *
 * <p>We do NOT touch AE2's private {@code patterns}/{@code patternInputs} fields. Instead we write the
 * disk patterns into the parent's own {@code patternInventory} (via {@link #getPatternInv()}) and let the
 * parent's {@link #updatePatterns()} decode and register them. This stays within public API.</p>
 *
 * <p>The disk inventory is supplied lazily via a {@link Supplier} so this logic may be constructed during
 * the block-entity's parent constructor before the disk inventory field is initialized.</p>
 */
public class PatternDiskProviderLogic extends PatternProviderLogic {

    private final Supplier<AppEngInternalInventory> diskInventorySupplier;

    /**
     * Disk state as of the last rebuild: one entry per slot, {@code null} for an empty slot and the disk's
     * {@link PatternDiskContents} instance otherwise.
     *
     * <p>Skipping an unchanged rebuild matters because the rebuild rewrites the whole mirror, but the check
     * itself must not cost as much as what it skips. A {@code PatternDiskContents} is replaced whenever the
     * disk is written - every write path returns a new record - so the instance is a content version, and
     * comparing states is a reference check per slot instead of a hash over every pattern's components.</p>
     */
    private List<Object> lastDiskState;

    public PatternDiskProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host,
            int diskSlots, Supplier<AppEngInternalInventory> diskInventorySupplier) {
        super(mainNode, host, mirrorCapacity(diskSlots));
        this.diskInventorySupplier = diskInventorySupplier;
        this.lastDiskState = null;
    }

    /**
     * How many slots the mirror needs: every disk slot the machine has, times the largest disk tier.
     *
     * <p>Derived rather than fixed, because a fixed number has to be right for every machine and every
     * tier at once. The one that used to be here - 1024 - was exactly one full disk, so a provider
     * holding nine of them exposed only the first and silently dropped the rest.</p>
     *
     * <p>The slot count is a parameter rather than a read of the disk inventory: this logic is built
     * inside the host's own {@code super()} constructor, before that inventory field exists.</p>
     *
     * <p>The upper bound comes from this mod's own tiers, which is what these machines' disks can hold -
     * every write path in the mod clamps to a tier capacity. A component edited out of band to claim more
     * than the largest tier would not be covered; covering that would mean deriving the bound from the
     * disks at runtime, and this constructor runs before the disks can be read.</p>
     */
    private static int mirrorCapacity(int diskSlots) {
        int perDisk = 0;
        for (PatternDiskTier tier : PatternDiskTier.values()) {
            perDisk = Math.max(perDisk, tier.capacity());
        }
        return diskSlots * perDisk;
    }

    /**
     * Rebinds the parent's pattern inventory to the encoded patterns on all inserted disks, then lets the
     * parent rebuild its pattern list.
     *
     * <p>Strategy 2 (static resolver): take the disk state again; if it is unchanged since the last rebuild
     * we short-circuit and skip the expensive {@code clear + rewrite + updatePatterns()} so a burst of
     * same-content invocations coalesces into a single rebuild. A content change (slot in/out or disk
     * repattern) rebuilds fresh in one pass.</p>
     */
    public boolean refreshPatternsFromDisks() {
        return refreshPatternsFromDisks(false);
    }

    /**
     * @param force rebuild even when the disks look unchanged. The mirror can be rewritten from under us
     *              while the disks stay where they are - importing a memory card clears it and then lets
     *              AE2 write into it - and the state check cannot see that, so those callers pass true.
     */
    public boolean refreshPatternsFromDisks(boolean force) {
        var diskInventory = diskInventorySupplier.get();
        if (diskInventory == null) {
            return false;
        }

        var state = diskState(diskInventory);
        if (!force && sameState(lastDiskState, state)) {
            return false; // unchanged: coalesce, skip full rebuild
        }

        InternalInventory patternInv = getPatternInv();
        if (patternInv == null) {
            return false;
        }

        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < diskInventory.size(); i++) {
            ItemStack diskStack = diskInventory.getStackInSlot(i);
            if (diskStack.isEmpty() || !(diskStack.getItem() instanceof PatternDiskItem disk)) {
                continue;
            }
            PatternDiskContents contents = disk.contents(diskStack);
            all.addAll(contents.patterns());
        }

        rebuildingMirror = true;
        try {
            patternInv.clear();
            for (int i = 0; i < all.size() && i < patternInv.size(); i++) {
                // copy(): the stacks come straight out of the disks' components, and a holder of the mirror
                // must not be able to mutate what the disk shows.
                patternInv.setItemDirect(i, all.get(i).copy());
            }
        } finally {
            rebuildingMirror = false;
        }

        // Parent decodes patternInventory into its patterns list and requests a grid update.
        updatePatterns();

        lastDiskState = state;
        return true;
    }

    /**
     * One entry per slot: {@code null} when empty, the disk's contents instance for a pattern disk, and the
     * item itself for anything else (a non-disk contributes nothing to the mirror, so the item identifies it).
     *
     * <p>An untyped disk that has never been written to stores no component, and {@code contents()} then builds
     * a fresh empty instance on every call - left as it is, such a disk would look changed on every refresh. It
     * contributes nothing to the mirror either way, so it is folded into {@code null} like an empty slot.</p>
     *
     * <p>The entries are content versions only because every write replaces the record: the list inside it is
     * immutable, but its {@code ItemStack} elements are not, so a caller that edited one in place would leave
     * this check blind to it. The {@link PatternDiskContents} contract already forbids that - read them, but do
     * not modify them in place - and every path that hands these stacks out copies them first, so the state
     * stays honest for as long as that contract holds. An in-place edit is the one thing the hash this replaced
     * would have noticed and this does not.</p>
     */
    private static List<Object> diskState(AppEngInternalInventory diskInventory) {
        var state = new ArrayList<Object>(diskInventory.size());
        for (int i = 0; i < diskInventory.size(); i++) {
            ItemStack stack = diskInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                state.add(null);
            } else if (stack.getItem() instanceof PatternDiskItem disk) {
                var contents = disk.contents(stack);
                state.add(contents.isEmpty() ? null : contents);
            } else {
                state.add(stack.getItem());
            }
        }
        return state;
    }

    /**
     * Whether two states describe the same disks, by reference. The entries are already content versions, so
     * identity is the comparison meant here - {@code equals} would walk every pattern's components, which is
     * exactly the cost this check exists to avoid. A slot whose contents were written compares unequal even
     * when the new contents happen to be equal to the old, and rebuilding is the safe direction.
     */
    private static boolean sameState(List<Object> previous, List<Object> current) {
        if (previous == null || previous.size() != current.size()) {
            return false;
        }
        for (int i = 0; i < previous.size(); i++) {
            if (previous.get(i) != current.get(i)) {
                return false;
            }
        }
        return true;
    }

    // The parent's updatePatterns re-reads patternInventory; our refresh already filled it.
    @Override
    public void updatePatterns() {
        super.updatePatterns();
    }

    /**
     * True while {@link #refreshPatternsFromDisks} is rewriting the mirror.
     *
     * <p>Every write into the mirror notifies the host, and the host's answer is a full
     * {@code updatePatterns()} - which walks every slot and decodes every pattern. Left alone, one rebuild
     * runs that once per written pattern instead of once at the end: a nine-disk mirror holds 9216, so that
     * is thousands of full passes against one. The flag collapses the burst, and the rebuild calls
     * {@code updatePatterns()} itself once the mirror is whole.</p>
     */
    private boolean rebuildingMirror;

    /**
     * Empties the mirror with a single parent update instead of one per slot.
     *
     * <p>{@code clear()} empties slot by slot and every slot notifies, so the plain call this replaces ran
     * a full {@code updatePatterns()} per slot - over the whole mirror each time. The callers only need the
     * empty end state, so the burst is collapsed the same way a rebuild collapses it. Only the mirror is
     * written while that flag is up; the disk inventory is not touched.</p>
     */
    public void clearMirror() {
        InternalInventory patternInv = getPatternInv();
        if (patternInv == null) {
            return;
        }
        rebuildingMirror = true;
        try {
            patternInv.clear();
        } finally {
            rebuildingMirror = false;
        }
        updatePatterns();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (rebuildingMirror) {
            // Keep the parent's change-is-persisted semantics, drop the redundant re-decode.
            saveChanges();
            return;
        }
        super.onChangeInventory(inv, slot);
    }
}
