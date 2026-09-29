package io.github.lounode.ae2pattern.client.integration.polymorph;

import net.neoforged.fml.ModList;

import io.github.lounode.ae2pattern.client.gui.PatternDiskEncodingTermScreen;

/**
 * Polymorph 的客户端接入点：告诉它「本模组的编码终端也要一个多态按钮」。
 *
 * <p>Polymorph 开放的是全局工厂注册（{@code registerWidget}），它打开任意界面时都会问一遍这个工厂，
 * 所以这里只注册一次就够，不需要动我们的 Screen 类。</p>
 *
 * <p>没装 Polymorph 时整个方法直接返回：Polymorph 的类型只在 {@link Bridge} 里出现，而未装时
 * {@code Bridge} 不会被触达，因此不会在类加载阶段抛 {@code NoClassDefFoundError}。</p>
 */
public final class PolymorphClientCompat {

    public static final String MOD_ID = "polymorph";

    private PolymorphClientCompat() {}

    /** 客户端初始化时调用。 */
    public static void register() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        Bridge.register();
    }

    /** 唯一直接引用 Polymorph 类型的地方，只在确认它在场之后才被加载。 */
    private static final class Bridge {

        private Bridge() {}

        static void register() {
            com.illusivesoulworks.polymorph.api.client.PolymorphWidgets.getInstance()
                    .registerWidget(screen -> {
                        // 无线编码终端继承自这个屏幕，所以这一个判断覆盖方块版、面板版与无线版。
                        if (screen instanceof PatternDiskEncodingTermScreen encodingScreen) {
                            return new PatternDiskPolymorphWidget(encodingScreen);
                        }
                        return null;
                    });
        }
    }
}
