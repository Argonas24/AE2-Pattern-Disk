/**
 * The stable surface of AE2 Pattern Disk, for addons that <em>carry</em> pattern disks or <em>serve</em>
 * the patterns stored on them.
 *
 * <p>Reach everything through {@link io.github.lounode.ae2pattern.api.PatternDiskApi}. What that facade
 * exposes - its methods and the types appearing in their signatures - is the versioned surface, and what
 * {@code API_VERSION} tracks, package moves included. Everything else here, including the members marked
 * {@link org.jetbrains.annotations.ApiStatus.Internal}, is this mod's own wiring and may change without
 * notice.</p>
 *
 * <p>Nothing here depends on the rest of this mod: everything is expressed in terms of the JDK, Minecraft
 * and AE2 types, so an addon can compile against this package alone.</p>
 */
package io.github.lounode.ae2pattern.api;
