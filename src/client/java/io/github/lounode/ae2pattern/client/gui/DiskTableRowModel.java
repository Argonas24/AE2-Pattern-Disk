package io.github.lounode.ae2pattern.client.gui;

import net.minecraft.world.item.ItemStack;

/**
 * 管理终端表格的行模型：三类行 + 它们的共同父类型。
 *
 * <p>从 {@code PatternDiskManagementTermScreen}（1300 多行）里抽出来——屏幕同时管着行模型、表格绘制、
 * 点击分发与滚动，这些行类型是其中唯一不依赖 GL 状态、可以整块搬走的部分。屏幕侧用静态导入
 * （{@code import static ...DiskTableRowModel.*;}）沿用 {@code Row} / {@code HostRow} 这些短名，
 * 所以消费点不必逐个改写。</p>
 *
 * <p><b>{@link Row} 是 sealed 的</b>：行种类就这三种，新增一种时编译器会把所有 {@code switch} 与
 * {@code instanceof} 链点出来，免得漏掉某处绘制分支。</p>
 */
public final class DiskTableRowModel {

    private DiskTableRowModel() {}

    /** 表格里的一行。 */
    public sealed interface Row permits HostRow, DiskRow, FreeSlotsRow {
    }

    /** 组头行：一台（或几台同名合并的）宿主机器。{@code diskCount} 是当前过滤/显示口径下的磁盘数。 */
    public record HostRow(String groupName, String name, ItemStack icon, int diskCount) implements Row {
    }

    /**
     * 磁盘行。{@code from == 0} 是首行：第 0 格是磁盘本身、后面 16 格是它里面的样板；{@code from > 0} 是续行：
     * 17 格全是样板，{@code from} 是这一行第 0 格对应的样板序号（续行从第一格开始接）。
     */
    public record DiskRow(String groupName, long serial, ItemStack disk, int from) implements Row {
    }

    /**
     * 供应器剩余的一个空槽，一行一格、竖着排在第一列。{@code foldedCount} &gt; 0 时这一行代表整组的全部空槽，
     * 数字写在格的右上角。
     */
    public record FreeSlotsRow(String groupName, int foldedCount) implements Row {
    }
}
