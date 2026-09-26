package io.github.lounode.ae2pattern.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.networking.IGrid;

/**
 * Storage for the collectors registered through {@link PatternDiskApi#registerDiskHost}. Package-private on
 * purpose: {@link PatternDiskApi} is the versioned entry point, and a second public door into the same list
 * would let an addon past that gate.
 *
 * <p>The pattern disk encoding terminal discovers disk slots by scanning grid machines for
 * {@link IPatternDiskHost}. Machines from other mods cannot implement that interface at compile time, so an
 * integration registers a {@link DiskHostCollector} and the terminal merges the collected hosts into its disk
 * list, exactly as if the machines implemented the interface.</p>
 *
 * <p>Collectors are called on the server thread while the terminal rebuilds its disk list; they must not
 * mutate world state. Registration is expected during an integration's setup - the list is never written
 * afterwards, and there is no unregister, in the same spirit as AE2's own registration facades: a
 * registered integration is present for the whole session, so a removal path would be dead code.</p>
 */
final class PatternDiskHostRegistry {

    private static final List<DiskHostCollector> COLLECTORS = new CopyOnWriteArrayList<>();

    private static final Logger LOGGER = LoggerFactory.getLogger("ae2_pattern_disk.integration");

    private PatternDiskHostRegistry() {
    }

    /**
     * Ignores a {@code null} collector rather than failing at collection time, and ignores a collector
     * instance that is already registered - an integration that registers the same instance from more than
     * one code path would otherwise have its hosts reported twice. A fresh lambda is a different instance
     * and is not caught by this.
     */
    static void register(@Nullable DiskHostCollector collector) {
        if (collector != null && !COLLECTORS.contains(collector)) {
            COLLECTORS.add(collector);
        }
    }

    /**
     * @return every extra disk host contributed for {@code grid}; empty when no integration is loaded
     */
    static List<IPatternDiskHost> collectExtra(@Nullable IGrid grid) {
        if (grid == null || COLLECTORS.isEmpty()) {
            return List.of();
        }
        var hosts = new ArrayList<IPatternDiskHost>();
        for (var collector : COLLECTORS) {
            try {
                var collected = collector.collect(grid);
                if (collected != null) {
                    hosts.addAll(collected);
                }
            } catch (RuntimeException e) {
                // A broken integration collector must not break the terminal's disk list.
                LOGGER.warn("Disk host collector failed; skipping its hosts", e);
            }
        }
        return hosts;
    }
}
