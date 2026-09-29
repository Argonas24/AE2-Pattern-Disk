package io.github.lounode.ae2pattern.common.block.entity;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import io.github.lounode.ae2pattern.MeteoritePatternProviderRegistrations;
import io.github.lounode.ae2pattern.api.PatternDiskApi;
import io.github.lounode.ae2pattern.api.PatternDiskTerminalView;
import io.github.lounode.ae2pattern.common.logic.PatternDiskProviderLogic;
import io.github.lounode.ae2pattern.common.logic.SelfAssemblingPatternDiskProviderLogic;

/**
 * 自装配样板磁盘供应器的方块实体：磁盘槽 + 自装配逻辑。
 *
 * <p>
 * 磁盘那半与 {@link PatternDiskProviderBlockEntity} 完全同构（9 张磁盘、镜像式样板栏、掉落与内存卡的剥离
 * 语义），差别只在逻辑类型：本设备的供应器逻辑会把磁盘展开出的配方当场自完成，而不是推给外部机器。所以这里
 * 的契约照抄——尤其是「patternInventory 只是磁盘的镜像」这条，掉落、内存卡导入导出都必须按它处理，否则会
 * 复制出磁盘里已有的样板。
 * </p>
 */
public class MeteoritePatternProviderBlockEntity extends PatternProviderBlockEntity
        implements MeteoritePatternProviderHost, InternalInventoryHost, ServerTickingBlockEntity {

    public static final int DISK_SLOT_COUNT = 9;

    private final AppEngInternalInventory diskInventory = new AppEngInternalInventory(this, DISK_SLOT_COUNT);

    /**
     * 终端视图（见 {@link PatternDiskTerminalView}）。缓存是因为已经打开的样板访问终端会一直用它开屏时拿到
     * 的那个实例，所以磁盘一变就作废，下一次打开重建。
     */
    private final PatternDiskTerminalView terminalView = PatternDiskApi.terminalView(diskInventory,
            () -> getMainNode().getGrid(), this, this::markTerminalChanged, this::getLevel);

    public MeteoritePatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(MeteoritePatternProviderRegistrations.BE.get(), pos, blockState);
    }

    @Override
    protected PatternProviderLogic createLogic() {
        // 懒取磁盘栏：方块实体的父构造会在这条路径上先跑 createLogic()，那时磁盘栏字段还没赋值。
        return new SelfAssemblingPatternDiskProviderLogic(getMainNode(), this, DISK_SLOT_COUNT, this::getDiskInventory);
    }

    public AppEngInternalInventory getDiskInventory() {
        return diskInventory;
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return terminalView.view();
    }

    /** 磁盘真的变过之后重建样板列表并作废终端视图。 */
    private void markTerminalChanged() {
        refreshFromDisks();
        saveChanges();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshFromDisks();
    }

    /**
     * 服务端 tick：驱动待回送产物。不用 AE2 供应器自己那份 tick（它里面那两个方法是私有的，外面接管不了），
     * 也不覆盖它——推送那半仍归它管，这里只多跑一件它不做的事。
     */
    @Override
    public void serverTick() {
        if (getLogic() instanceof SelfAssemblingPatternDiskProviderLogic logic) {
            logic.tickCraftedContents();
        }
    }

    /**
     * 按当前磁盘内容重建样板列表，并作废缓存视图，好让下一次打开的终端拿到紧凑的行。已经开着的终端继续用它
     * 自己那份（行数冻结），这里不重新同步它——重同步会把行数顶到终端客户端的固定槽数之上而崩服务端。
     */
    public void refreshFromDisks() {
        refreshFromDisks(false);
    }

    /**
     * @param force 磁盘没动也强制重建——内存卡导入会改写镜像而磁盘原地不动，状态比较看不出这一点
     */
    public void refreshFromDisks(boolean force) {
        if (getLogic() instanceof SelfAssemblingPatternDiskProviderLogic diskLogic) {
            diskLogic.refreshPatternsFromDisks(force);
        }
        terminalView.invalidate();
    }

    /**
     * 清空镜像而不触发逐槽通知——plain {@code clear()} 会每槽一次全量重解析，这里只需要空终态。
     */
    private void clearMirror() {
        if (getLogic() instanceof PatternDiskProviderLogic diskLogic) {
            diskLogic.clearMirror();
        } else {
            // 今天不可达；但万一失配，后果不是变慢而是复制样板——AE2 的 addDrops 会把镜像掉给玩家，
            // 所以回退到普通清空，保住改动前的硬保证，而不是把镜像留在原地。
            getLogic().getPatternInv().clear();
        }
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == diskInventory) {
            refreshFromDisks();
            saveChanges();
        }
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public boolean isClientSide() {
        return level != null && level.isClientSide();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        diskInventory.writeToNBT(tag, "disks", registries);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        diskInventory.readFromNBT(tag, "disks", registries);
        refreshFromDisks();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        // 不调 super：父类会把镜像式样板栏当成真实内容掉出来。真身只有磁盘。
        for (int i = 0; i < diskInventory.size(); i++) {
            if (!diskInventory.getStackInSlot(i).isEmpty()) {
                drops.add(diskInventory.getStackInSlot(i));
            }
        }
        clearMirror();
        getLogic().addDrops(drops);
        clearContent();
    }

    /**
     * 内存卡导入时保住磁盘镜像：AE2 的默认实现会清掉供应器的样板栏并把里面的样板交给玩家，而这里那栏只是
     * 磁盘的镜像，照做会把磁盘上已有的样板复制一份。
     */
    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        var cleanInput = withoutPatterns(input);
        if (mode == SettingsFrom.MEMORY_CARD) {
            // 镜像马上会被 super.importSettings 改写，而磁盘没动——状态比较看不出这种变化。
            clearMirror();
        }
        super.importSettings(mode, cleanInput, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            // 必须强制重建：磁盘没动，状态检查会跳过，镜像就停在 super.importSettings 留下的样子。
            refreshFromDisks(true);
        }
    }

    /**
     * 导出前把内存卡里的样板段清空。别处写过的卡（旧版本、或 AE2 自己的供应器）仍带着样板，导入时那些会被
     * 当作真实内容、为磁盘上已有的配方扣空白样板，然后在下次刷新时被丢掉。
     */
    private static DataComponentMap withoutPatterns(DataComponentMap input) {
        var sanitized = DataComponentMap.builder().addAll(input);
        sanitized.set(AEComponents.EXPORTED_PATTERNS, ItemContainerContents.EMPTY);
        return sanitized.build();
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            builder.set(AEComponents.EXPORTED_PATTERNS, ItemContainerContents.EMPTY);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        diskInventory.clear();
        terminalView.invalidate();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(MeteoritePatternProviderRegistrations.ITEM.get());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(MeteoritePatternProviderRegistrations.ITEM.get());
    }
}
