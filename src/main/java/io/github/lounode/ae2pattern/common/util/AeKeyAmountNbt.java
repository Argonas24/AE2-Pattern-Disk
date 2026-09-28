package io.github.lounode.ae2pattern.common.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

/**
 * {@code Object2LongMap<AEKey>} 的 NBT 读写：自装配的待回送产物表（crafted contents）要跨存档保留。
 *
 * <p>
 * 这不是本模组独有的格式，只是「key + amount 的列表」这一种写法：AEKey 的通用 tag 编解码负责 key 本身，
 * 本类负责容器。损坏的条目跳过而不失败——一张读不出来的产物表不应该让方块实体加载失败。
 * </p>
 */
public final class AeKeyAmountNbt {

    private AeKeyAmountNbt() {}

    /** 把非正数量的条目丢掉后写入 {@code data[name]}（ListTag&lt;CompoundTag&gt;）。 */
    public static void write(CompoundTag data, String name, Object2LongMap<AEKey> map,
            HolderLookup.Provider registries) {
        var list = new ListTag();

        for (var entry : map.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }

            CompoundTag keyTag;
            try {
                keyTag = entry.getKey().toTagGeneric(registries);
            } catch (Throwable ignored) {
                // 编不出来的 key 直接丢：它也不该在表里。
                continue;
            }

            if (keyTag != null) {
                CompoundTag tag = new CompoundTag();
                tag.put("key", keyTag);
                tag.putLong("amount", amount);
                list.add(tag);
            }
        }

        data.put(name, list);
    }

    /** 读入 {@code target}（先清空）；缺字段或条目损坏时按空处理。 */
    public static void read(CompoundTag data, String name, Object2LongMap<AEKey> target,
            HolderLookup.Provider registries) {
        target.clear();

        if (!data.contains(name, Tag.TAG_LIST)) {
            return;
        }

        var list = data.getList(name, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            long amount = entry.getLong("amount");
            if (amount <= 0 || !entry.contains("key", Tag.TAG_COMPOUND)) {
                continue;
            }
            var key = AEKey.fromTagGeneric(registries, entry.getCompound("key"));
            if (key != null) {
                target.put(key, amount);
            }
        }
    }
}
