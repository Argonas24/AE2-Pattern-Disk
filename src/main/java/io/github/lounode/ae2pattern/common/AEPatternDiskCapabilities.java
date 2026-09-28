package io.github.lounode.ae2pattern.common;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.items.tools.powered.powersink.PoweredItemCapabilities;

import io.github.lounode.ae2pattern.AEPatternRegistries;

/**
 * 把两个无线终端接进 NeoForge 的 FE 能量能力。
 *
 * <p>AE2 侧只用自家接口 {@link IAEItemPowerStorage} 表达物品储能（AE 充能器就是按这个接口判定能否充电），
 * 而 FE 能力是在 {@code InitCapabilityProviders} 里<b>逐个 ItemDefinition</b> 登记的，只覆盖 AE2 自家的电力
 * 物品——继承 {@code AEBasePoweredItem} 的第三方物品不会自动获得。这里复用 AE2 自己的 FE 桥
 * {@link PoweredItemCapabilities} 补上登记，于是 MEK 能量单元、充能台这类 FE 设备也能给终端充电。</p>
 */
@EventBusSubscriber(modid = "ae2_pattern_disk")
public final class AEPatternDiskCapabilities {

    private AEPatternDiskCapabilities() {}

    @SubscribeEvent
    public static void registerItemEnergy(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> new PoweredItemCapabilities(stack, (IAEItemPowerStorage) stack.getItem()),
                AEPatternRegistries.ITEM_WIRELESS_PATTERN_DISK_ENCODING_TERMINAL.get(),
                AEPatternRegistries.ITEM_WIRELESS_PATTERN_DISK_MANAGEMENT_TERMINAL.get());
    }
}
