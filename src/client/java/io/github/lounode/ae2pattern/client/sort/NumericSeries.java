package io.github.lounode.ae2pattern.client.sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jetbrains.annotations.Nullable;

import io.github.lounode.ae2pattern.config.AEPDConfig;

/**
 * 附加排序最后那层「组内按名字里的数值排」的名字门槛：哪些名字参与这一层。
 *
 * <p>数值比较本身（数字 + 可选单位按 1024 的幂换算、逐段比大小）在 {@link NaturalOrder}，这里只管筛选。
 * 门槛是一组正则，写在 {@code config/ae2_pattern_disk-client.toml} 的 {@code additional_sort.numeric_regex}
 * 里：名字里任意一条正则命中即参与。默认 {@code [0-9]}——任何带数字的名字都参与，与加入这一项之前覆盖
 * 的名字集合相同（同分组内的次序差别见下）。</p>
 *
 * <p>「参与与否」落在排序键的第一位：参与的名字整体排在没参与的前面，同类之内再各按自己的规则比（参与的
 * 按数值、没参与的按字面）。不能写成「这一对里有一方不参与就整对改字面序」——那样比较方式取决于「跟谁
 * 比」，收窄正则后会成环（{@code 2k存储元件 < 10k存储元件} 是数值判，却 {@code 2k存储元件 > 1x存储元件
 * > 10k存储元件} 是字面判），而排序算法（TimSort）遇到循环会抛
 * {@code IllegalArgumentException: Comparison method violates its general contract!}，把整个网格排序弄崩；
 * 本模组的比较器交给 AE2 用，也没法在调用侧兜底。</p>
 *
 * <p>默认 {@code [0-9]} 下「参与 ⇔ 含数字」，而含数字与不含数字的名字落在同一分组（去掉数字后同名）本就
 * 罕见；两者相遇时的次序差别也只在字面序把不含数字的一侧排在前面时才看得出来（前缀型如
 * {@code Storage Component} 与 {@code Storage Component 1k}，或数字位之前收尾于空格、{@code !}、
 * {@code .} 这类排在 {@code '0'} 之前的字符）。</p>
 */
public final class NumericSeries {

    /** 编译好的门槛正则；{@code null} 表示不设门槛（全部名字都参与）。 */
    private static @Nullable List<Pattern> patterns;
    /** 解析时那份配置值，换了一份就重新编译。 */
    private static @Nullable Object parsedFrom;

    private NumericSeries() {
    }

    /**
     * 带门槛的数值序。规则在一次调用里定死：排序中途配置变化不会让同一个比较器前后换口径，重载在下次重排
     * 生效。名字的判定结果按名字缓存，随比较器一起回收，不跨屏积起来。
     *
     * <p>四个分支：配置读不出来或表里全写坏 → 不设门槛（全按数值序）；表被留空 → 整层关掉（全按字面序）；
     * 其余 → 参与的先排，同类之内按各自规则。</p>
     */
    public static Comparator<String> strings() {
        var compiled = patterns();
        if (compiled == null) {
            return NaturalOrder::compare;
        }
        if (compiled.isEmpty()) {
            return String.CASE_INSENSITIVE_ORDER;
        }
        var participated = new HashMap<String, Boolean>();
        return (left, right) -> {
            boolean leftPart = participates(left, compiled, participated);
            boolean rightPart = participates(right, compiled, participated);
            if (leftPart != rightPart) {
                // 参与与否是排序键的第一位：比较方式只取决于名字自己，才成得了全序（见类注释）。
                return leftPart ? -1 : 1;
            }
            return leftPart
                    ? NaturalOrder.compare(left, right)
                    : String.CASE_INSENSITIVE_ORDER.compare(left, right);
        };
    }

    /** 丢掉编译结果，下次按当前配置重来。配置对象自己换了时这里会自动重编译，这个入口留给显式调用。 */
    public static void invalidate() {
        patterns = null;
        parsedFrom = null;
    }

    private static boolean participates(String name, List<Pattern> compiled, Map<String, Boolean> cache) {
        return cache.computeIfAbsent(name, key -> {
            for (var pattern : compiled) {
                if (pattern.matcher(key).find()) {
                    return true;
                }
            }
            return false;
        });
    }

    private static @Nullable List<Pattern> patterns() {
        var configured = configuredRegex();
        if (configured != parsedFrom) {
            patterns = compile(configured);
            parsedFrom = configured;
        }
        return patterns;
    }

    /**
     * 当前配置里的正则表。配置还没加载时（例如在专用服务端、或配置界面正在重建 spec 的间隙）读不出来，
     * 这时返回 {@code null}——不设门槛，比「当成空表」安全：空表会让数值序静默消失。
     */
    private static @Nullable List<? extends String> configuredRegex() {
        try {
            var value = AEPDConfig.ADDITIONAL_SORT_NUMERIC_REGEX.get();
            return value == null ? List.of() : value;
        } catch (Throwable unavailable) {
            return null;
        }
    }

    /**
     * 编译：写错的那条单独跳过，不牵连同表里的其它正则，也不让排序抛异常。表被留空（或只写了空白项）
     * 是玩家要关掉这一层，给空表；表里有内容却一条都编译不出来是配置写坏，给 {@code null}（不设门槛），
     * 免得静默把整层关掉、让人以为排序坏了。
     */
    private static @Nullable List<Pattern> compile(@Nullable List<? extends String> raw) {
        if (raw == null) {
            return null;
        }
        var compiled = new ArrayList<Pattern>();
        boolean declaredSomething = false;
        for (var entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            declaredSomething = true;
            try {
                compiled.add(Pattern.compile(entry.trim()));
            } catch (PatternSyntaxException invalid) {
                // 单条写错就少一条门槛，别的照常生效。
            }
        }
        if (compiled.isEmpty()) {
            return declaredSomething ? null : List.of();
        }
        return List.copyOf(compiled);
    }
}
