package io.github.lounode.ae2pattern.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;

/**
 * Public entry points for addons that <em>carry</em> pattern disks or <em>serve</em> the patterns stored
 * on them.
 *
 * <p>Everything reachable from this class is stable surface: the methods here, together with every type that
 * appears in their signatures <em>or in this class's body</em>, change only with {@link #API_VERSION} - which
 * an addon can assert once during its own setup, to fail loudly rather than misbehave quietly.</p>
 *
 * <h2>What an addon usually needs</h2>
 *
 * <ul>
 *   <li><b>Reading a disk.</b> {@link #contents(ItemStack)} says what one holds without naming the
 *       disk's item class, so a slot filter can keep a stack opaque and still ask. {@link #isPatternDisk}
 *       answers the same question without exposing the class at all.</li>
 *   <li><b>Checking a write before offering it.</b> {@link #canAccept} folds capacity, the disk's locked
 *       type and the same-result exclusion into one answer, so it never disagrees with a real write.</li>
 *   <li><b>Serving the disks a machine holds.</b> {@link #terminalView} wraps slots that contain disks
 *       into an inventory whose rows are the patterns <em>on</em> those disks - the shape AE2's pattern
 *       access terminal reads, so the recipes show up there instead of an undecodable disk item.</li>
 *   <li><b>Listing the disks on a grid.</b> {@link #diskHosts} is the same set the encoding and management
 *       terminals build their lists from.</li>
 *   <li><b>Holding a slot as a disk.</b> {@link IPatternDisk} is the same questions as
 *       {@link #contents}/{@link #canAccept}, as an interface - for a caller that must keep a
 *       disk-typed reference without naming the item. Implementing it does <em>not</em> by itself make
 *       an item fit this mod's disk slots; only this mod's own disk item does.</li>
 *   <li><b>Letting players write to those disks.</b> {@link #registerDiskHost} hands this mod's disk
 *       encoding terminal the machines to list, so their disks can be encoded into from there.</li>
 * </ul>
 *
 * <p>{@link ExternalUploadPolicy} is not in that list on purpose: it exists for the integrations shipped
 * with this mod, and installing one from outside would switch the upload gate for everyone.</p>
 */
public final class PatternDiskApi {

    /**
     * Version of this entry point. It changes whenever a signature reachable from this class changes, and
     * also when one is added - so an addon should assert {@code API_VERSION >= <the version it was built
     * against>} rather than equality, or a merely additive release would refuse to start.
     *
     * <p>2 added {@link #diskHosts(IGrid)}.</p>
     *
     * <p>3 added {@link IPatternDisk} and {@link ExternalUploadPolicy}, and moved
     * {@link PatternDiskContents}, {@link PatternDiskTerminalView} and {@link PatternDiskRemoveInventory}
     * into this package - the old {@code common.pattern} locations are gone, so a 2-era consumer must
     * follow the move even though {@code API_VERSION} still comparing {@code >=} would let it start.</p>
     */
    public static final int API_VERSION = 3;

    private PatternDiskApi() {
    }

    /**
     * @return whether {@code stack} is one of this mod's pattern disks, without exposing its item class
     */
    public static boolean isPatternDisk(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IPatternDisk;
    }

    /**
     * <p>The pattern stacks inside the returned value are the live ones held by the disk: read them, but
     * do not modify them in place - writes belong to the disk's own insert path.</p>
     *
     * @return what {@code stack} holds, or {@code null} when it is not a pattern disk - a disk that
     *         holds nothing still answers, with an untyped empty set of its own capacity
     */
    @Nullable
    public static PatternDiskContents contents(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IPatternDisk disk)) {
            return null;
        }
        return disk.contents(stack);
    }

    /**
     * @return whether the disk would accept {@code pattern} right now. Room, the disk's locked type and
     *         the same-result exclusion all live in this answer, so it never disagrees with a write.
     */
    public static boolean canAccept(ItemStack disk, ItemStack pattern, Level level) {
        return disk != null && !disk.isEmpty() && disk.getItem() instanceof IPatternDisk item
                && item.canInsert(disk, pattern, level);
    }

    /**
     * Wraps slots that hold pattern disks into a view whose rows are the patterns on those disks.
     *
     * <p>Exposing the slots directly would show a player an undecodable disk item; this view expands it,
     * charges a blank pattern from the network for every pattern taken and removes that recipe from its
     * disk. The returned view caches its row layout, so call {@link PatternDiskTerminalView#invalidate()}
     * when the disks change.</p>
     *
     * <p>Its write path (a pattern being uploaded onto a disk) first checks that the network can take the
     * blank pattern that write frees, and <b>refuses the write</b> when it cannot - so an upload can fail
     * for a full or offline network, and the pattern stays with the caller instead of the blank pattern
     * being lost.</p>
     *
     * @param diskSlots  the slots holding pattern disks (slots holding anything else are ignored). The
     *                   view writes back through {@code setItemDirect} whenever a pattern is taken,
     *                   uploaded or rolled back, so the inventory must actually persist writes
     * @param grid       resolves the attached grid, used to draw and return blank patterns
     * @param machine    the host, used as the action source for that accounting
     * @param onChanged  invoked after a real mutation so the host can persist and rebuild
     * @param level      resolves the level, needed to decode a pattern before it is written to a disk
     *
     * @apiNote Server-side only: a grid is a server concept, so a client-side call has nothing to attach to.
     */
    public static PatternDiskTerminalView terminalView(InternalInventory diskSlots, Supplier<IGrid> grid,
            IActionHost machine, Runnable onChanged, Supplier<Level> level) {
        return new PatternDiskTerminalView(diskSlots, grid, machine, onChanged, level);
    }

    /**
     * Registers a collector of the disk hosts a machine from another mod provides, so this mod's disk
     * encoding terminal lists their disks too. Safe to call from an integration's server-side setup.
     */
    public static void registerDiskHost(DiskHostCollector collector) {
        PatternDiskHostRegistry.register(collector);
    }

    private static @Nullable ExternalUploadPolicy externalUploadPolicy;

    /**
     * Installs the policy that decides how a disk view cooperates with a mod that uploads patterns
     * through it. Called from this mod's own setup; until then {@link ExternalUploadPolicy#NONE} answers,
     * so nothing outside this mod sees a row it did not ask for.
     *
     * <p>First write wins: this is a facade for this mod's own wiring, and letting a later caller replace
     * it would switch the upload gate for every view at once.</p>
     */
    @ApiStatus.Internal
    public static void setExternalUploadPolicy(@Nullable ExternalUploadPolicy policy) {
        if (externalUploadPolicy == null && policy != null) {
            externalUploadPolicy = policy;
        }
    }

    static ExternalUploadPolicy externalUploadPolicy() {
        var installed = externalUploadPolicy;
        return installed == null ? ExternalUploadPolicy.NONE : installed;
    }

    /**
     * Binds the data component a disk uses to store its {@link PatternDiskContents}. Called once during
     * setup by this mod's own registration, which is the only layer that already names the component;
     * an addon reads and writes disks through this mod's disk item instead.
     *
     * <p>First write wins: binding a second component would silently detach the disk views from the
     * component the disks actually store, so views would report a take the disk never saw.</p>
     */
    @ApiStatus.Internal
    public static void bindDiskContentsComponent(
            Supplier<DataComponentType<PatternDiskContents>> componentType) {
        PatternDiskComponents.bind(componentType);
    }

    /**
     * Every disk host currently on {@code grid}: the machines that implement {@link IPatternDiskHost}
     * themselves, plus whatever the registered {@link DiskHostCollector}s report - the latter being how a
     * machine from another mod gets listed without depending on this one.
     *
     * <p>This is the same set the disk encoding and management terminals build their lists from, so an
     * addon that wants to mirror or extend that listing reads it here rather than re-deriving it.</p>
     *
     * @param grid the grid to scan; {@code null} yields an empty list
     * @return the hosts, each at most once (compared by object identity), in machine-then-collector order;
     *         an immutable list, empty on the client side (a grid is a server concept)
     */
    public static List<IPatternDiskHost> diskHosts(@Nullable IGrid grid) {
        if (grid == null) {
            return List.of();
        }
        var seen = Collections.newSetFromMap(new IdentityHashMap<IPatternDiskHost, Boolean>());
        var hosts = new ArrayList<IPatternDiskHost>();
        for (var machineClass : grid.getMachineClasses()) {
            if (machineClass == null || !IPatternDiskHost.class.isAssignableFrom(machineClass)) {
                continue;
            }
            for (var machine : grid.getActiveMachines(machineClass)) {
                if (machine instanceof IPatternDiskHost host && seen.add(host)) {
                    hosts.add(host);
                }
            }
        }
        for (var host : PatternDiskHostRegistry.collectExtra(grid)) {
            if (host != null && seen.add(host)) {
                hosts.add(host);
            }
        }
        return List.copyOf(hosts);
    }
}
