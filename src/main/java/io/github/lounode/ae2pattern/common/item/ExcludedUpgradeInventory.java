package io.github.lounode.ae2pattern.common.item;

import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import appeng.api.config.FuzzyMode;
import appeng.api.upgrades.IUpgradeInventory;

/**
 * 按物品 id 剔除升级卡的包装层：除了「这些卡不许装」，其余判定与行为全部转发给 AE2 的真实实现。
 *（类名用 excluded 而不是 whitelist：这里做的是减法，白名单是反过来的语义。）
 *
 * <p>做这层包装的原因：限制清单只能加不能减——{@code Upgrades} 里那张表是 private 静态 map 且只有
 * {@code add}；判定闸门 {@code UpgradeInventory#getMaxInstalled} 又来自一个包私有抽象类，无法继承。
 * 需要「减」的情形来自 blanket 挂卡：模组可以用 {@code UpgradeHelper.addUpgradeToAllTerminals} 把一张卡
 * 挂到当时已登记的全部无线终端上（wtlib 自己就是这么挂量子桥卡的），而本终端只想要其中一部分。</p>
 *
 * <p>用 id 而不是物品对象来判定：本模组编译期只依赖 {@code ae2wtlib_api}，wtlib 的物品常量（
 * {@code AE2wtlibItems}）在编译期不可见；而且 id 比较对"上游改动物品归属"也更宽容。</p>
 */
public class ExcludedUpgradeInventory implements IUpgradeInventory {

    private final IUpgradeInventory delegate;
    private final Set<ResourceLocation> excluded;

    public ExcludedUpgradeInventory(IUpgradeInventory delegate, Set<ResourceLocation> excluded) {
        this.delegate = delegate;
        this.excluded = excluded;
    }

    /** 被剔除的卡返回 0：AE2 由此认定不可安装，界面上也放不进去；其余照旧。 */
    @Override
    public int getMaxInstalled(ItemLike upgradeCard) {
        var id = BuiltInRegistries.ITEM.getKey(upgradeCard.asItem());
        if (id != null && this.excluded.contains(id)) {
            return 0;
        }
        return this.delegate.getMaxInstalled(upgradeCard);
    }

    // ---- 以下全部原样转发，行为与 AE2 的实现一致 ----

    @Override
    public ItemLike getUpgradableItem() {
        return this.delegate.getUpgradableItem();
    }

    @Override
    public int getInstalledUpgrades(ItemLike upgradeCard) {
        return this.delegate.getInstalledUpgrades(upgradeCard);
    }

    @Override
    public void readFromNBT(CompoundTag tag, String name, HolderLookup.Provider registries) {
        this.delegate.readFromNBT(tag, name, registries);
    }

    @Override
    public void writeToNBT(CompoundTag tag, String name, HolderLookup.Provider registries) {
        this.delegate.writeToNBT(tag, name, registries);
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.delegate.getStackInSlot(slot);
    }

    @Override
    public void setItemDirect(int slot, ItemStack stack) {
        this.delegate.setItemDirect(slot, stack);
    }

    @Override
    public ItemStack removeItems(int amount, ItemStack filter, Predicate<ItemStack> predicate) {
        return this.delegate.removeItems(amount, filter, predicate);
    }

    @Override
    public ItemStack simulateRemove(int amount, ItemStack filter, Predicate<ItemStack> predicate) {
        return this.delegate.simulateRemove(amount, filter, predicate);
    }

    @Override
    public ItemStack removeSimilarItems(int amount, ItemStack filter, FuzzyMode mode,
            Predicate<ItemStack> predicate) {
        return this.delegate.removeSimilarItems(amount, filter, mode, predicate);
    }

    @Override
    public ItemStack simulateSimilarRemove(int amount, ItemStack filter, FuzzyMode mode,
            Predicate<ItemStack> predicate) {
        return this.delegate.simulateSimilarRemove(amount, filter, mode, predicate);
    }

    @Override
    public ItemStack addItems(ItemStack stack, boolean simulate) {
        return this.delegate.addItems(stack, simulate);
    }
}
