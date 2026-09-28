package io.github.lounode.ae2pattern.common.menu.slot;

import net.minecraft.world.entity.player.Player;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.RestrictedInputSlot;

/**
 * 样板输出栏的槽：只做展示与中转，不允许玩家把它拿起。
 *
 * <p>AE2 原版的同语义槽（{@code RestrictedInputSlot} 的 {@code ENCODED_PATTERN}）是「编码产物出口」，
 * 拿走是正常玩法；本模组这一格是编码区与样板磁盘之间的中转，产物最终由编码流程写进磁盘，
 * 被拿走只会打断编辑。</p>
 *
 * <p>防线必须落在槽上，而不是屏幕的 {@code slotClicked}：整理、配方查询这类 mod 的快捷键会直接发
 * {@code ServerboundContainerClickPacket}（QUICK_MOVE / PICKUP 等），服务端只经
 * {@code AbstractContainerMenu#clicked} 落到 {@link #mayPickup}，完全不经过客户端屏幕。</p>
 *
 * <p>编码流程自身不走玩家交互——它直接写 {@code encodingLogic} 持有的 inventory，所以这里关掉
 * 拿起不会影响编码与清空。</p>
 */
public class PatternOutputSlot extends RestrictedInputSlot {

    public PatternOutputSlot(InternalInventory inventory, int slot) {
        super(PlacableItemType.ENCODED_PATTERN, inventory, slot);
    }

    /** 一律不可拿起：光标拿到它之后编码区就空了，编辑链随即断掉。 */
    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
