package io.github.lounode.ae2pattern.api;

/**
 * How this mod's disk views cooperate with a mod that uploads patterns through them.
 *
 * <p>Some mods push patterns into a provider by scanning for a free row first and writing through that
 * row; this mod's provider view can be that provider. Whether such a mod is around, and what shape of
 * view it needs, is not something this mod can decide on its own - so it is injected, and defaults to
 * {@link #NONE}, which keeps the view entirely to itself.</p>
 *
 * <p>This gates <em>this mod's own provider view</em> only. A machine that keeps disks in its own slots
 * needs nothing from it: writing onto a disk directly is {@link PatternDiskApi#insert}, which is not
 * gated, and the accounting it owes the network is {@link BlankPatternSink}.</p>
 *
 * @see PatternDiskApi#setExternalUploadPolicy(ExternalUploadPolicy)
 */
public interface ExternalUploadPolicy {

    /** Nothing uploads through this mod's views - the default until an integration says otherwise. */
    ExternalUploadPolicy NONE = new ExternalUploadPolicy() {
        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean wantsFreeRow(boolean hasLevelSupplier, int freeCapacity) {
            return false;
        }
    };

    /**
     * Whether an outside uploader is present at all. When false, a view refuses writes through the row
     * scan rather than accepting a pattern it has nowhere to put.
     */
    boolean isActive();

    /**
     * Whether a view should advertise a free row to that uploader's row scan.
     *
     * @param hasLevelSupplier whether the view can resolve a level at all; a view that cannot can never
     *                         accept an upload, because decoding a pattern needs one
     * @param freeCapacity     total free pattern slots across the view's disks
     * @return whether a free row should be advertised
     */
    boolean wantsFreeRow(boolean hasLevelSupplier, int freeCapacity);
}
