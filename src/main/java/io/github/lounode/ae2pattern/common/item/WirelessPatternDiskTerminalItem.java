package io.github.lounode.ae2pattern.common.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.locator.ItemMenuHostLocator;

import de.mari_023.ae2wtlib.api.terminal.ItemWT;

/**
 * 无线版样板磁盘终端的物品。两个无线终端（编码 / 管理）共用这一个类——它们差的只有菜单类型，而那由构造
 * 传入；宿主、逻辑、菜单内容都在 {@link io.github.lounode.ae2pattern.common.menu.WirelessPatternDiskTerminalHost}
 * 与各自的菜单里。
 *
 * <p>物品属性（堆叠数为 1、电池能量）由 AE2WTLib 的 {@link ItemWT} 定死，本类不参与，所以注册时那个
 * {@code Properties} 参数用不上。</p>
 */
public class WirelessPatternDiskTerminalItem extends ItemWT {

    private final MenuType<?> menuType;

    public WirelessPatternDiskTerminalItem(MenuType<?> menuType) {
        this.menuType = menuType;
    }

    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return this.menuType;
    }
}
