package io.github.lounode.ae2pattern.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;

import de.mari_023.ae2wtlib.api.gui.ScrollingUpgradesPanel;
import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

import io.github.lounode.ae2pattern.common.menu.PatternDiskWirelessManagementTermMenu;

/**
 * 无线版管理终端的屏幕：表格、盘内内容、附加排序全部继承面板版管理终端，无线那一套（升级卡面板、终端切换
 * 按钮、热键）与{@link PatternDiskWirelessEncodingTermScreen 无线编码终端}同一口径。
 */
public class PatternDiskWirelessManagementTermScreen extends PatternDiskManagementTermScreen
        implements IUniversalTerminalCapable {

    /** 升级卡面板：留给 {@code init()} 之后按可见行数回写行数（同无线编码终端）。 */
    private ScrollingUpgradesPanel upgradesPanel;

    public PatternDiskWirelessManagementTermScreen(PatternDiskWirelessManagementTermMenu menu,
            Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void init() {
        addToLeftToolbar(cycleTerminalButton());
        this.upgradesPanel = addUpgradePanel(widgets, getMenu());
        super.init();
        this.upgradesPanel.setMaxRows(Math.max(2, getVisibleRows()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return checkForTerminalKeys(keyCode, scanCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public WTMenuHost getHost() {
        return ((PatternDiskWirelessManagementTermMenu) getMenu()).getTerminalHost();
    }
}
