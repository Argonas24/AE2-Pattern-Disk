package io.github.lounode.ae2pattern.api;

import java.util.Objects;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

/**
 * Access to the data component a disk uses to store its {@link PatternDiskContents}.
 *
 * <p>The component itself is registered with the game, so its holder lives in the registration class
 * rather than here; that class binds it once during setup. Keeping the indirection in this direction
 * (registration depends on the api, not the other way round) is what lets the disk inventory move into
 * this package without dragging the registries along.</p>
 */
final class PatternDiskComponents {

    private static @Nullable Supplier<DataComponentType<PatternDiskContents>> type;

    private PatternDiskComponents() {
    }

    /** Called once during setup; the holder is a {@code DeferredHolder}, whose value only exists later. */
    static void bind(Supplier<DataComponentType<PatternDiskContents>> componentType) {
        Objects.requireNonNull(componentType, "componentType");
        // First write wins - see PatternDiskApi.bindDiskContentsComponent for why rebinding is refused.
        if (type == null) {
            type = componentType;
        }
    }

    static void write(ItemStack stack, PatternDiskContents contents) {
        stack.set(requireType(), contents);
    }

    private static DataComponentType<PatternDiskContents> requireType() {
        var bound = type;
        if (bound == null) {
            // Setup order is a mod-loading concern, so this reports the missing step instead of guessing.
            throw new IllegalStateException(
                    "Pattern disk component is not bound yet; the registration class must call bind() during setup");
        }
        return bound.get();
    }
}
