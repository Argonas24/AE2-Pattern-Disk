package io.github.lounode.ae2pattern.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;

import de.mari_023.ae2wtlib.api.gui.ScrollingUpgradesPanel;
import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

import io.github.lounode.ae2pattern.common.menu.PatternDiskWirelessEncodingTermMenu;

/**
 * 无线版编码终端的屏幕：布局、磁盘列表、附加排序按钮全部继承面板版，只多出 AE2WTLib 那一套无线终端的东西
 * ——升级卡面板、终端切换按钮、热键。三样都由 {@link IUniversalTerminalCapable} 的默认实现提供，本类负责
 * 把它们挂上去、并在按键时先问一句热键。
 *
 * <p>顺序与 AE2WTLib 自己的无线终端（{@code WETScreen}）一致：先挂按钮与升级面板、再走父类的
 * {@code init()} 铺开布局。</p>
 */
public class PatternDiskWirelessEncodingTermScreen extends PatternDiskEncodingTermScreen
        implements IUniversalTerminalCapable {

    /** 升级卡面板：留给 {@code init()} 之后按可见行数回写行数（与 AE2WTLib 自己的无线终端同口径）。 */
    private ScrollingUpgradesPanel upgradesPanel;

    public PatternDiskWirelessEncodingTermScreen(PatternDiskWirelessEncodingTermMenu menu,
            Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void init() {
        addToLeftToolbar(cycleTerminalButton());
        this.upgradesPanel = addUpgradePanel(widgets, getMenu());
        super.init();
        // 行数按屏幕实际能放下多少收：不写这一句就恒为默认的 2 行，高屏会白白空着。
        this.upgradesPanel.setMaxRows(Math.max(2, getVisibleRows()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 无线终端的热键（切换终端、打开无线设置）优先于普通按键。
        return checkForTerminalKeys(keyCode, scanCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public WTMenuHost getHost() {
        return ((PatternDiskWirelessEncodingTermMenu) getMenu()).getTerminalHost();
    }
}
