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

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li><b>Decoding what is on a disk.</b> {@link #contents(ItemStack)} returns encoded pattern stacks;
 *       {@link #decodePattern} turns one into its inputs, outputs and how it is performed, and
 *       {@link #patternType} names the pattern's kind. Reading a disk and understanding a pattern on it
 *       are separate questions, and this is the second one. When walking a whole disk - repeatedly, or in
 *       a per-tick path - use {@link #decodePatterns}, which memoizes per contents snapshot.</li>
 *   <li><b>Checking a write before offering it.</b> {@link #canAccept} folds capacity, the disk's locked
 *       type and the same-result exclusion into one answer, so it never disagrees with a real write.</li>
 *   <li><b>Writing a pattern to a disk.</b> {@link #insert} is the counterpart of {@link #canAccept}: a
 *       caller told yes writes here and gets the same answer, plus whatever the disk refused. Writing owes
 *       the ME network a blank pattern, which is what {@link BlankPatternSink} accounts for; pass
 *       {@code null} when the caller has no network to charge.</li>
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
     *
     * <p>4 added the decode entry points - {@link #decodePattern}, {@link #patternType} in both overloads,
     * {@link #isBlankPattern} and {@link #isEncodedPattern} - and moved {@code PatternClassifier} into
     * this package to back them. Its members outside this class are not versioned surface; reach the
     * decode through here.</p>
     *
     * <p>5 added {@link #decodePatterns}, the memoized form of {@link #decodePattern}.</p>
     *
     * <p>6 added the write half - {@link #insert} and the {@link BlankPatternSink} it charges - so a caller
     * that was told {@link #canAccept} can do the write instead of reaching past the api for it. Also in 6,
     * this mod's own provider view answers {@code isItemValid} consistently with its write path rather than
     * always refusing.</p>
     */
    public static final int API_VERSION = 6;

    private static final Logger LOGGER = LoggerFactory.getLogger("ae2_pattern_disk.api");

    private PatternDiskApi() {
    }

    /**
     * @return whether {@code stack} is a pattern disk, without exposing its item class. This asks whether the
     *         item implements {@link IPatternDisk}, which today only this mod's own disk item does - it is not
     *         the same question as "does this fit this mod's disk slots", which the slot filters answer by
     *         naming that item directly
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
     * Writes one encoded pattern onto {@code disk}, reporting what the disk did not take. This is the
     * write half of the api and the counterpart of {@link #canAccept}: a caller told "yes" writes here and
     * gets the same answer. The disk is mutated in place, as any other {@code ItemStack} write.
     *
     * <p>Writing onto a disk owes the ME network one blank pattern back. Pass a {@link BlankPatternSink} to
     * have that accounted for, or {@code null} when the caller has no network to charge - the write is then
     * purely the disk's business. The sink is asked <em>before</em> anything is written, so a network that
     * cannot take a blank pattern back fails this with {@code pattern} still in the caller's hands.</p>
     *
     * <p>This does not look at {@link #setExternalUploadPolicy}: that policy guards this mod's own provider
     * view, which only serves the uploader it was built for. A machine from another mod keeps its own disks
     * in its own slots and writes through here directly.</p>
     *
     * @param disk    the disk to write to, mutated in place on success
     * @param pattern the encoded pattern to write; a stack of more than one is refused, not trimmed
     * @param level   the level to resolve items in; {@code null} refuses the write rather than guessing
     * @param sink    the ME network to charge one blank pattern, or {@code null} to charge nobody
     * @return {@link ItemStack#EMPTY} when the pattern landed, otherwise {@code pattern} unchanged - so test
     *         with {@code == ItemStack#EMPTY} rather than null, and never pass an empty stack as
     *         {@code pattern}, which would be reported as a successful write
     */
    public static ItemStack insert(ItemStack disk, ItemStack pattern, Level level, @Nullable BlankPatternSink sink) {
        if (pattern == null) {
            return ItemStack.EMPTY; // nothing to write, and returning null would break the contract below
        }
        return writeAndCharge(disk, pattern, level, sink) ? ItemStack.EMPTY : pattern;
    }

    /**
     * The write path {@link #insert} and this mod's own provider view share, so the rules - single item,
     * room, type lock, primary-output exclusion and the blank-pattern accounting - exist once.
     *
     * @return whether {@code disk} took the pattern and was updated; when false it is untouched
     */
    static boolean writeAndCharge(ItemStack disk, ItemStack pattern, Level level, @Nullable BlankPatternSink sink) {
        if (disk == null || disk.isEmpty() || !(disk.getItem() instanceof IPatternDisk item)) {
            return false;
        }
        // Encoded patterns are single items; a stack of them is not something a disk can store, and taking
        // it would drop the surplus.
        if (pattern == null || pattern.isEmpty() || pattern.getCount() != 1 || level == null) {
            return false;
        }
        // Ask before writing: a network that cannot take the blank pattern back makes this fail with the
        // pattern still in the caller's hands, rather than letting one evaporate.
        if (sink != null && !sink.hasRoomForBlankPatterns(1)) {
            return false;
        }
        if (!item.canInsert(disk, pattern, level)) {
            return false;
        }
        if (!item.tryInsert(disk, pattern, level)) {
            return false;
        }
        if (sink != null && !sink.returnBlankPatterns(1)) {
            // Best effort by design: whatever freed the pattern is already done and is not rolled back,
            // since the caller offers no fallback destination for it.
            LOGGER.warn("A blank pattern owed to the ME network could not be returned on a disk write; "
                    + "one blank pattern is lost");
        }
        return true;
    }

    /**
     * @return whether {@code stack} is an AE2 blank pattern - the thing a terminal charges from the
     *         network for every pattern taken off a disk. {@code null} and empty stacks answer
     *         {@code false}
     */
    public static boolean isBlankPattern(ItemStack stack) {
        return PatternClassifier.isBlankPattern(stack);
    }

    /**
     * @return whether {@code stack} is an encoded pattern, i.e. something {@link #decodePattern} can
     *         turn into details; an unencoded or blank stack answers {@code false}
     */
    public static boolean isEncodedPattern(ItemStack stack) {
        return PatternClassifier.isEncodedPatternStack(stack);
    }

    /**
     * Decodes an encoded pattern into what it does: the inputs it consumes, the outputs it produces and
     * how it is performed. This is the "parsing" half of a disk - {@link #contents(ItemStack)} says what
     * patterns are <em>on</em> a disk, this says what one of them means.
     *
     * <p>Not memoized: each call builds the details afresh. A caller that walks the same disk repeatedly -
     * a slot filter, a per-tick scan - should use {@link #decodePatterns(PatternDiskContents, Level)}
     * instead, which reuses a memo keyed by the contents snapshot.</p>
     *
     * @param pattern the stack to decode, typically an element of {@link PatternDiskContents#patterns()}
     * @param level   the level to resolve items in; {@code null} answers {@code null} rather than guessing
     * @return the decoded details; {@code null} when {@code pattern} is not an encoded pattern, and also
     *         when it is encoded but cannot be decoded (a pattern naming a recipe that no longer exists)
     */
    @Nullable
    public static IPatternDetails decodePattern(ItemStack pattern, Level level) {
        return PatternClassifier.decode(pattern, level);
    }

    /**
     * Decodes every pattern on {@code contents}. The result is memoized per (contents snapshot, level)
     * pair and dropped when server data reloads, so walking the same disk in a loop is cheap - the disks
     * themselves are what change, and a changed disk is a different snapshot.
     *
     * <p>A pattern that cannot be decoded is skipped rather than failing the batch, so the returned list
     * can be shorter than {@link PatternDiskContents#patterns()}.</p>
     *
     * @param contents the contents to decode; {@code null} or a {@code null} level yields an empty list
     * @return the decoded patterns, in disk order, each at most once per memo entry
     */
    public static List<IPatternDetails> decodePatterns(PatternDiskContents contents, Level level) {
        if (contents == null) {
            return List.of();
        }
        return PatternClassifier.decodedStored(contents, level);
    }

    /**
     * @return the resource id of the encoded pattern item {@code pattern} is (for example
     *         {@code ae2:crafting_pattern}), or {@code null} when it is not an encoded pattern
     */
    @Nullable
    public static String patternType(ItemStack pattern, Level level) {
        return PatternClassifier.typeOf(pattern, level);
    }

    /**
     * @return the resource id of the encoded pattern item {@code details} was decoded from - the same
     *         value {@link PatternDiskContents#type()} holds once a disk is locked to it; {@code null}
     *         only if {@code details} violates AE2's contract and reports no definition
     */
    @Nullable
    public static String patternType(IPatternDetails details) {
        return PatternClassifier.typeOf(details);
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
