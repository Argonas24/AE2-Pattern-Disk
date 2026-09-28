package io.github.lounode.ae2pattern.common.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import io.github.lounode.ae2pattern.AEPatternRegistries;
import io.github.lounode.ae2pattern.common.item.PatternDiskItem;

/**
 * 洗掉样板磁盘上的工作方块标记：一张空的、带标记的样板磁盘 → 同一张盘，只是去掉了 {@code DISK_PREFIX}。
 *
 * <p>标记本来描述的是盘里装哪台机器的配方；盘被清空之后标记会留在上面，于是又冒出「0 内容却带类型」的
 * 盘。这个配方就是给它一个说理的去处——比在右键菜单里再塞一条交互干净。</p>
 *
 * <p>为什么不写成数据包里的 shapeless：json 配料只认物品与指定的组件值，表达不了「盘里是空的」，
 * 那样一张装满样板的盘也会被顺手洗净、内容不可逆丢失。容量也不写死——认的是输入的 ItemStack，
 * 出料复制它再删标记，所以各容量等级共用这一个配方。</p>
 */
public class ClearDiskMarkRecipe extends CustomRecipe {

    public ClearDiskMarkRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** 恰好一张空盘，且确实带着标记。 */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            var stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!found.isEmpty()) {
                return false; // 只接受单个输入
            }
            if (!(stack.getItem() instanceof PatternDiskItem disk)) {
                return false;
            }
            if (disk.used(stack) > 0) {
                return false; // 有内容：洗标记会把盘里的样板一起带走
            }
            if (stack.get(AEPatternRegistries.DISK_PREFIX.get()) == null) {
                return false; // 本来就没标记，没什么可洗
            }
            found = stack;
        }
        return !found.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (int i = 0; i < input.size(); i++) {
            var stack = input.getItem(i);
            if (!stack.isEmpty()) {
                var cleared = stack.copy();
                cleared.remove(AEPatternRegistries.DISK_PREFIX.get());
                return cleared;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AEPatternRegistries.CLEAR_DISK_MARK.get();
    }
}
