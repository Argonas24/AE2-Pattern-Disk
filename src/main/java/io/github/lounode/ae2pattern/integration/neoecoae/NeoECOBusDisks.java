package io.github.lounode.ae2pattern.integration.neoecoae;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.core.definitions.AEItems;
import appeng.api.stacks.AEItemKey;

import io.github.lounode.ae2pattern.api.PatternDiskApi;
import io.github.lounode.ae2pattern.common.item.PatternDiskItem;

/**
 * Disk operations on a bus's pattern inventory.
 *
 * <p>The bus hands out its pattern slots as an {@code InternalInventory}; the disks are simply the
 * slots holding one of this mod's disk items. Every question the auxiliary-store API asks - can this disk
 * take a pattern, does any disk still have room, what patterns do the disks carry - is answered by one
 * scan here, so the callbacks cannot drift apart from each other.</p>
 *
 * <p>Everything it asks <em>about</em> a disk goes through {@link PatternDiskApi}, the same facade an addon
 * would consume - this integration is a real caller of it, not a special case. Slot admission is the one
 * exception: {@link #ownsDisk} names the disk item, because "one of ours" is a narrower question than "a
 * pattern disk".</p>
 *
 * <p>Writes go back through that same inventory rather than mutating a detached copy, so a disk update
 * reaches the bus through its ordinary inventory notification and the bus can re-index what changed.</p>
 */
final class NeoECOBusDisks {

    private NeoECOBusDisks() {
    }

    /** A pattern disk found in a bus slot, kept together with the slot it must be written back to. */
    private record BusDisk(int slot, ItemStack stack) {
    }

    /**
     * @return whether {@code stack} is one of this mod's pattern disks. Naming the item is deliberate: the
     *         bus must not treat a look-alike implementation as one of ours, and the slots it hands out are
     *         ours to interpret.
     */
    static boolean ownsDisk(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PatternDiskItem;
    }

    /** @return {@code true} when at least one disk on the bus would accept {@code pattern}. */
    static boolean anyDiskAccepts(NeoECOBusAccess.BusHandles handles, Object bus, ItemStack pattern) {
        Level level = NeoECOBusAccess.levelOf(handles, bus);
        InternalInventory inventory = NeoECOBusAccess.patternInventory(handles, bus);
        if (level == null || inventory == null) {
            return false;
        }
        for (BusDisk disk : disksIn(inventory)) {
            // Room, locked type and same-result exclusion all live in canAccept, so this probe cannot
            // disagree with what insert would actually do.
            if (PatternDiskApi.canAccept(disk.stack(), pattern, level)) {
                return true;
            }
        }
        return false;
    }

    /** @return {@code true} when some disk is not at capacity yet, whatever the pattern would be. */
    static boolean anyDiskHasRoom(NeoECOBusAccess.BusHandles handles, Object bus) {
        InternalInventory inventory = NeoECOBusAccess.patternInventory(handles, bus);
        if (inventory == null) {
            return false;
        }
        for (BusDisk disk : disksIn(inventory)) {
            var contents = PatternDiskApi.contents(disk.stack());
            if (contents != null && !contents.isFull()) {
                return true;
            }
        }
        return false;
    }

    /** @return {@code true} when {@code pattern} reached a disk; the disk stack is written back in place. */
    static boolean insertIntoDisk(NeoECOBusAccess.BusHandles handles, Object bus, ItemStack pattern) {
        Level level = NeoECOBusAccess.levelOf(handles, bus);
        InternalInventory inventory = NeoECOBusAccess.patternInventory(handles, bus);
        if (level == null || inventory == null) {
            return false;
        }
        for (BusDisk disk : disksThatAccept(inventory, pattern, level)) {
            // insert mutates the stack's contents component rather than returning a new stack. A null sink asks it
            // to charge nobody: this is the bus's own auxiliary store, and the blank accounting for a pattern
            // having reached the network is done by whoever put it there. It also re-runs the acceptance check
            // disksThatAccept already made, so the candidate pattern is decoded twice for every disk tried.
            if (PatternDiskApi.insert(disk.stack(), pattern, level, null) != ItemStack.EMPTY) {
                continue;
            }
            inventory.setItemDirect(disk.slot(), disk.stack());
            return true;
        }
        return false;
    }

    /**
     * Every disk that would take {@code pattern}, with the already-claimed ones first.
     *
     * <p>An empty disk locks to whatever class reaches it first, so handing every new class to the first
     * free disk spreads one class per disk and leaves nothing for the classes that arrive later, even
     * though room remains. Filling a disk that already holds the class first keeps the free disks free for
     * classes that have not shown up yet. Disks locked to another class are left out entirely.</p>
     */
    private static List<BusDisk> disksThatAccept(InternalInventory inventory, ItemStack pattern, Level level) {
        List<BusDisk> claimed = new ArrayList<>();
        List<BusDisk> empty = new ArrayList<>();
        for (BusDisk disk : disksIn(inventory)) {
            // Room, locked type and same-result exclusion all live in canAccept, so this probe cannot
            // disagree with what insert would actually do.
            if (!PatternDiskApi.canAccept(disk.stack(), pattern, level)) {
                continue;
            }
            var contents = PatternDiskApi.contents(disk.stack());
            if (contents == null || contents.type() == null) {
                empty.add(disk);
            } else {
                claimed.add(disk);
            }
        }
        claimed.addAll(empty);
        return claimed;
    }

    /** @return every pattern stored on the bus's disks, still encoded. */
    static List<ItemStack> collectEncodedPatterns(NeoECOBusAccess.BusHandles handles, Object bus) {
        InternalInventory inventory = NeoECOBusAccess.patternInventory(handles, bus);
        if (inventory == null) {
            return List.of();
        }
        List<ItemStack> encoded = new ArrayList<>();
        for (BusDisk disk : disksIn(inventory)) {
            var contents = PatternDiskApi.contents(disk.stack());
            if (contents != null) {
                encoded.addAll(contents.patterns());
            }
        }
        return encoded;
    }

    /**
     * A change token for the bus's disk contents.
     *
     * <p>Every mutation replaces the disk's {@code PatternDiskContents} record, so the <em>identity</em> of
     * that record changes on every write. Fingerprinting identities keeps this O(slots) instead of hashing
     * every stored pattern, which matters because the bus polls it whenever it is asked for a content
     * revision. Disks with no patterns are skipped: they contribute nothing, and a blank disk's contents
     * are freshly built on each read, so including one would invalidate on every poll.</p>
     *
     * <p>Identities are 32 bits, so the pattern count and the locked type are added as cheap
     * discriminators: an unnoticed change would have to collide on the identity hash <em>and</em> leave the
     * count and the type untouched.</p>
     */
    static long diskRevision(NeoECOBusAccess.BusHandles handles, Object bus) {
        InternalInventory inventory = NeoECOBusAccess.patternInventory(handles, bus);
        if (inventory == null) {
            return 0L;
        }
        long hash = 1L;
        for (BusDisk disk : disksIn(inventory)) {
            var contents = PatternDiskApi.contents(disk.stack());
            if (contents == null || contents.patterns().isEmpty()) {
                continue;
            }
            hash = hash * 31L + disk.slot();
            hash = hash * 31L + System.identityHashCode(contents);
            hash = hash * 31L + contents.used();
            hash = hash * 31L + Objects.hashCode(contents.type());
        }
        return hash;
    }

    /** Draws blanks from {@code grid}, all or nothing. */
    static boolean drawBlankPatterns(IGrid grid, int count) {
        if (grid == null || count <= 0) {
            return false;
        }
        // An empty source rather than one naming the bus: the bus comes from another mod and is not statically
        // an action host here. The failure direction is the safe one - a network that refuses an unattributed
        // extraction leaves the recipe where it is instead of paying for it.
        return grid.getStorageService().getInventory()
                .extract(AEItemKey.of(AEItems.BLANK_PATTERN), count, Actionable.MODULATE, IActionSource.empty())
                == count;
    }

    /**
     * Takes one pattern back off a disk, paying for it first.
     *
     * <p>The order is the contract. A pattern that reached the network's index came out of a blank, so the blank
     * is drawn before anything is removed; if it cannot be drawn the disk is left exactly as it was, since a
     * removal that half happened would lose both the pattern and the blank.</p>
     *
     * @param diskSlot which of the bus's slots holds the disk - the same pattern can sit on more than one
     * @param pattern the encoded pattern to take off, matched by item and components
     * @return whether the pattern was taken off
     */
    static boolean removeFromDisk(NeoECOBusAccess.BusHandles handles, Object bus, int diskSlot, ItemStack pattern,
            IGrid grid) {
        InternalInventory inventory = NeoECOBusAccess.patternInventory(handles, bus);
        if (inventory == null || pattern == null || pattern.isEmpty() || diskSlot < 0
                || diskSlot >= inventory.size()) {
            return false;
        }
        ItemStack stack = inventory.getStackInSlot(diskSlot);
        if (stack.isEmpty() || !ownsDisk(stack)) {
            return false;
        }
        var contents = PatternDiskApi.contents(stack);
        if (contents == null) {
            return false;
        }
        for (int index = 0; index < contents.patterns().size(); index++) {
            if (!ItemStack.isSameItemSameComponents(contents.patterns().get(index), pattern)) {
                continue;
            }
            if (!drawBlankPatterns(grid, 1)) {
                return false;
            }
            // removeAt edits the stack's contents component in place, the way insert does, so the stack has to
            // go back into the slot for the change to be visible there.
            PatternDiskApi.removeAt(stack, index);
            inventory.setItemDirect(diskSlot, stack);
            return true;
        }
        return false;
    }

    private static List<BusDisk> disksIn(InternalInventory inventory) {
        List<BusDisk> disks = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && ownsDisk(stack)) {
                disks.add(new BusDisk(slot, stack));
            }
        }
        return disks;
    }
}
