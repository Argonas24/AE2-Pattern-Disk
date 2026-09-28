package io.github.lounode.ae2pattern;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

import io.github.lounode.ae2pattern.network.AssemblerAnimationPayload;
import io.github.lounode.ae2pattern.network.DiskListPayload;
import io.github.lounode.ae2pattern.network.DiskContentPayload;
import io.github.lounode.ae2pattern.network.DiskHostListPayload;
import io.github.lounode.ae2pattern.network.VisibleDisksPayload;
import io.github.lounode.ae2pattern.common.menu.PatternDiskManagementTermMenu;
import io.github.lounode.ae2pattern.config.AEPDConfig;

import io.github.lounode.ae2pattern.common.block.entity.PatternDiskAssemblerBlockEntity;
import io.github.lounode.ae2pattern.common.block.entity.MeteoritePatternProviderBlockEntity;
import io.github.lounode.ae2pattern.common.block.entity.PatternDiskProviderBlockEntity;
import io.github.lounode.ae2pattern.common.block.entity.PatternTransfererBlockEntity;
import io.github.lounode.ae2pattern.integration.ae2cs.AecsSoftDep;

/**
 * Entry point for the AE2 Pattern Disk addon.
 */
@Mod(AE2PatternDisk.MOD_ID)
public class AE2PatternDisk {

    public static final String MOD_ID = "ae2_pattern_disk";

    public AE2PatternDisk(IEventBus modBus, ModContainer modContainer) {
        // 附加排序的层级表是纯客户端的视图设置：注册成 CLIENT，服务端不加载这份文件，也不随网络同步。
        modContainer.registerConfig(ModConfig.Type.CLIENT, AEPDConfig.CLIENT_SPEC);

        // Registration entry points
        AEPatternRegistries.register(modBus);

        // 自装配样板磁盘供应器：整套注册以 AE2 Crystal Science 在场为前提，不在场时这台设备在游戏里不存在。
        // 判定放在这里而不是静态字段上，是因为 ModList 要到 mod 构造期才可读。
        if (AecsSoftDep.isLoaded()) {
            MeteoritePatternProviderRegistrations.register(modBus);
        }

        modBus.addListener(this::associateBlockEntities);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::registerPartCapabilities);
        modBus.addListener(this::registerPayloads);

        // A reload can change what a stored pattern decodes to without the items changing, so the
        // item-keyed decode memo has to be dropped. Nothing else observes reloads, and the bus's own
        // decode caches are rebuilt from slots on the next change.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.AddReloadListenerEvent event) ->
                        io.github.lounode.ae2pattern.api.PatternClassifier.invalidateDecodedCache());
    }

    /**
     * Registers AE2 capabilities that AE2 itself only binds for its own BlockEntityTypes.
     * Our machines use new BlockEntityTypes, so we must re-register the same capabilities.
     * Without {@code IN_WORLD_GRID_NODE_HOST} the grid cannot reach our nodes (connection lost on
     * world reload / channel drop), and without {@code GENERIC_INTERNAL_INV}/{@code ItemHandler}
     * the molecular assembler cannot push crafts back into our return inventory.
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Grid node host: lets AE2 cables / grid find our in-world nodes (fixes lost connection).
        event.registerBlockEntity(
                appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
                AEPatternRegistries.BE_TRANSFERER.get(),
                (be, dir) -> (appeng.api.networking.IInWorldGridNodeHost) be);
        event.registerBlockEntity(
                appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
                AEPatternRegistries.BE_PROVIDER.get(),
                (be, dir) -> (appeng.api.networking.IInWorldGridNodeHost) be);

        // Generic internal inventory / item handler: exposes the return inventory so the molecular
        // assembler (and other AE machines) can push crafted results back into the provider.
        event.registerBlockEntity(
                appeng.api.AECapabilities.GENERIC_INTERNAL_INV,
                AEPatternRegistries.BE_PROVIDER.get(),
                (be, dir) -> PatternDiskProviderBlockEntity.class.cast(be).getLogic().getReturnInv());

        // 自装配样板磁盘供应器：它同样要能从网上被找到，并把返回仓露给分子装配台回送产物。
        if (MeteoritePatternProviderRegistrations.isRegistered()) {
            event.registerBlockEntity(
                    appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    MeteoritePatternProviderRegistrations.BE.get(),
                    (be, dir) -> (appeng.api.networking.IInWorldGridNodeHost) be);
            event.registerBlockEntity(
                    appeng.api.AECapabilities.GENERIC_INTERNAL_INV,
                    MeteoritePatternProviderRegistrations.BE.get(),
                    (be, dir) -> MeteoritePatternProviderBlockEntity.class.cast(be).getLogic().getReturnInv());
        }

        // Crafting machine: exposes the assembler so any AE2 provider can push patterns to it.
        event.registerBlockEntity(
                appeng.api.AECapabilities.CRAFTING_MACHINE,
                AEPatternRegistries.BE_ASSEMBLER.get(),
                (be, dir) -> (appeng.api.implementations.blockentities.ICraftingMachine) be);

        // Grid node host for the assembler (needed so cables can reach its node).
        event.registerBlockEntity(
                appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
                AEPatternRegistries.BE_ASSEMBLER.get(),
                (be, dir) -> (appeng.api.networking.IInWorldGridNodeHost) be);

        // Item handler for the assembler's output slots: pipes, hoppers and AE2 storage buses can
        // recover a product that neither the neighbouring return node nor the ME network would take.
        // AE2 binds the same capability for its own molecular assembler, whose crafting-grid filter
        // likewise only allows extraction from the output slot.
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                AEPatternRegistries.BE_ASSEMBLER.get(),
                (be, dir) -> PatternDiskAssemblerBlockEntity.class.cast(be)
                        .getExposedOutputInventory().toItemHandler());

        // Batch assembler: grid node host + crafting machine (receives provider-pushed patterns).
        // NOTE: deliberately no ME_STORAGE / IStorageProvider exposure - its cell slots must stay private.
        event.registerBlockEntity(
                appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
                AEPatternRegistries.BE_BATCH_ASSEMBLER.get(),
                (be, dir) -> (appeng.api.networking.IInWorldGridNodeHost) be);
        event.registerBlockEntity(
                appeng.api.AECapabilities.CRAFTING_MACHINE,
                AEPatternRegistries.BE_BATCH_ASSEMBLER.get(),
                (be, dir) -> (appeng.api.implementations.blockentities.ICraftingMachine) be);
    }

    /**
     * Parts are not covered by {@link RegisterCapabilitiesEvent}: AE2 uses a separate event for them, so
     * the panel form of the provider has to register its return inventory here, exactly like the block
     * form does above (that is the channel a molecular assembler pushes crafted results back through).
     */
    private void registerPartCapabilities(appeng.api.parts.RegisterPartCapabilitiesEvent event) {
        event.addHostType(appeng.core.definitions.AEBlockEntities.CABLE_BUS.get());
        event.register(
                appeng.api.AECapabilities.GENERIC_INTERNAL_INV,
                (part, context) -> ((io.github.lounode.ae2pattern.common.part.PatternDiskProviderPart) part)
                        .getLogic().getReturnInv(),
                io.github.lounode.ae2pattern.common.part.PatternDiskProviderPart.class);
    }

    private void associateBlockEntities(RegisterEvent event) {
        if (event.getRegistryKey() == net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE) {
            AEPatternRegistries.BLOCK_TRANSFERER.get().setBlockEntity(
                    PatternTransfererBlockEntity.class,
                    AEPatternRegistries.BE_TRANSFERER.get(),
                    null,
                    (lvl, p, st, be) -> ((appeng.blockentity.ServerTickingBlockEntity) be).serverTick());

            AEPatternRegistries.BLOCK_PROVIDER.get().setBlockEntity(
                    PatternDiskProviderBlockEntity.class,
                    AEPatternRegistries.BE_PROVIDER.get(),
                    null,
                    null);

            AEPatternRegistries.BLOCK_ASSEMBLER.get().setBlockEntity(
                    PatternDiskAssemblerBlockEntity.class,
                    AEPatternRegistries.BE_ASSEMBLER.get(),
                    null,
                    null);

            AEPatternRegistries.BLOCK_BATCH_ASSEMBLER.get().setBlockEntity(
                    io.github.lounode.ae2pattern.common.block.entity.BatchAssemblerBlockEntity.class,
                    AEPatternRegistries.BE_BATCH_ASSEMBLER.get(),
                    null,
                    null);

            if (MeteoritePatternProviderRegistrations.isRegistered()) {
                MeteoritePatternProviderRegistrations.BLOCK.get().setBlockEntity(
                        MeteoritePatternProviderBlockEntity.class,
                        MeteoritePatternProviderRegistrations.BE.get(),
                        null,
                        (lvl, p, st, be) -> ((appeng.blockentity.ServerTickingBlockEntity) be).serverTick());
            }
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MOD_ID);
        registrar.playToClient(
                AssemblerAnimationPayload.TYPE,
                AssemblerAnimationPayload.STREAM_CODEC,
                (payload, context) -> {
                    var player = context.player();
                    if (player != null) {
                        payload.handleOnClient(player);
                    }
                });
        registrar.playToClient(
                DiskListPayload.TYPE,
                DiskListPayload.STREAM_CODEC,
                (payload, context) -> payload.handleOnClient(context));
        // 管理终端的表格：分组清单与「按需的盘内内容」。内容走单独一条包，因为一张盘最多 1024 张样板，
        // 不能跟着列表一起发（见 DiskContentPayload 的注释）。
        registrar.playToClient(
                DiskHostListPayload.TYPE,
                DiskHostListPayload.STREAM_CODEC,
                (payload, context) -> payload.handleOnClient(context));
        registrar.playToClient(
                DiskContentPayload.TYPE,
                DiskContentPayload.STREAM_CODEC,
                (payload, context) -> payload.handleOnClient(context));
        // 客户端上报「表格当前显示哪些盘」；服务端不猜视口，只按它答内容。
        registrar.playToServer(
                VisibleDisksPayload.TYPE,
                VisibleDisksPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var player = context.player();
                    if (player != null && player.containerMenu instanceof PatternDiskManagementTermMenu menu) {
                        payload.handleOnServer(menu);
                    }
                }));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(this::registerUpgrades);
    }

    private void registerUpgrades() {
        // 无线接入点的「终端绑定槽」按 GridLinkables 注册表决定收不收：AE2 自己只登记了它的两个无线终端
        // （InitGridLinkables），第三方终端必须在自己的初始化里登记，否则放进接入点会被拒收。
        // LINKABLE_HANDLER 是 AE2 给无线终端用的现成 handler，可直接复用。
        appeng.api.features.GridLinkables.register(
                AEPatternRegistries.ITEM_WIRELESS_PATTERN_DISK_ENCODING_TERMINAL.get(),
                appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER);
        appeng.api.features.GridLinkables.register(
                AEPatternRegistries.ITEM_WIRELESS_PATTERN_DISK_MANAGEMENT_TERMINAL.get(),
                appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER);

        var machine = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(net.minecraft.resources.ResourceLocation.parse("ae2_pattern_disk:pattern_transferer"));

        // 无线信号增幅器**不登记**：它管的是无线访问点的信号范围，放进终端的升级槽没有意义
        // （AE2WTLib 也没给自家终端挂它，同一个理由）。

        // 能源卡：AE2WTLib 的 UpgradeHelper 给所有无线终端加的是「能源卡上限 0」——等于默认不支持，
        // 要用它得自己登记上限。2 与 AE2 自家的无线终端一致。
        appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.ENERGY_CARD,
                AEPatternRegistries.ITEM_WIRELESS_PATTERN_DISK_ENCODING_TERMINAL.get(), 2);
        appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.ENERGY_CARD,
                AEPatternRegistries.ITEM_WIRELESS_PATTERN_DISK_MANAGEMENT_TERMINAL.get(), 2);

        var speedCard = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.parse("ae2:speed_card"));
        if (machine != null && speedCard != null) {
            appeng.api.upgrades.Upgrades.add(speedCard, machine, 4);
        }

        var assembler = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(net.minecraft.resources.ResourceLocation.parse("ae2_pattern_disk:pattern_disk_assembler"));
        if (assembler != null && speedCard != null) {
            appeng.api.upgrades.Upgrades.add(speedCard, assembler, 5);
        }

        var batchAssembler = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(net.minecraft.resources.ResourceLocation.parse("ae2_pattern_disk:batch_molecular_assembler"));
        if (batchAssembler != null && speedCard != null) {
            appeng.api.upgrades.Upgrades.add(speedCard, batchAssembler, 4);
        }

        // 自装配样板磁盘供应器：速度卡按 AE2 的规矩上；陨石超频卡是 AE2 Crystal Science 的升级件，得由本模组
        // 把它挂到自己的方块上（那张卡不知道这台设备），所以这里按 id 取来登记，取不到就只留速度卡那一条。
        if (MeteoritePatternProviderRegistrations.isRegistered()) {
            var meteoriteProvider = MeteoritePatternProviderRegistrations.BLOCK.get();
            if (speedCard != null) {
                appeng.api.upgrades.Upgrades.add(speedCard, meteoriteProvider, 4);
            }
            var overloadCard = AecsSoftDep.overloadCard();
            if (overloadCard != null) {
                appeng.api.upgrades.Upgrades.add(overloadCard, meteoriteProvider, 4);
            }
        }
    }
}
