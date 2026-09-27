package io.github.lounode.ae2pattern.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置。
 *
 * <p>两项都是纯客户端视图设置：附加排序的层级词表，以及附加排序最后那层数值序的名字门槛。它们只影响
 * 客户端怎么给自己的物品网格排序，不改变任何服务端行为，所以按 {@code CLIENT} 类型注册——服务端不加载
 * 这份文件，也不会随网络同步（每个玩家的排序偏好本来就该各管各的）。</p>
 *
 * <p>文件落在 {@code config/ae2_pattern_disk-client.toml}。改完存档不需要重开，客户端重载配置即生效。</p>
 */
public final class AEPDConfig {

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADDITIONAL_SORT_TIERS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADDITIONAL_SORT_NUMERIC_REGEX;

    /**
     * 默认层级表：一组一个元素，组内按「从低到高」列层级词。
     *
     * <p>每条前面可带一个 mod 范围（{@code mekanism*} 这种，省略或写 {@code *} 即不限），用来隔开同名层级词
     * 在不同模组里的含义——{@code basic} 在 Mekanism 与 Powah 里都有，靠它各归各的顺序。</p>
     *
     * <p>层级词写注册名里的片段而不是中文名：显示名随语言变，注册名不会，所以同一个词在中英文客户端下都能
     * 命中，元素也短得多（这一项在配置文件里是单行写的，NeoForge 不会把数组拆成多行）。要按中文名匹配时写成
     * {@code 中文|english} 即可，两种写法命中其一就算。</p>
     */
    private static final List<String> DEFAULT_TIERS = List.of(
            // Mekanism 工厂：basic 基础 → advanced 高级 → elite 精英 → ultimate 终极
            // → absolute 绝对 → supreme 至尊 → cosmic 寰宇支配 → infinite 悖论无限
            "mekanism*:basic,advanced,elite,ultimate,absolute,supreme,cosmic,infinite",
            // Mekanism 合金：infused 灌注 → reinforced 强化 → atomic 原子
            // → radiance 辐光 → thermonuclear 热核 → shining 闪耀 → spectrum 光谱
            "mekanism*:infused,reinforced,atomic,radiance,thermonuclear,shining,spectrum",
            // Powah：starter 初级 → basic 基础 → hardened 硬化 → blazing 烈焰
            // → niotic 钻石 → spirited 富生 → nitro 下界 → creative 创造
            "powah:starter,basic,hardened,blazing,niotic,spirited,nitro,creative");

    /**
     * 默认的数值序门槛：任何名字里带数字的条目都参与组内数值序，与加入这一项之前覆盖的名字集合相同。
     *
     * <p>门槛用正则而不是名字清单，是为了让玩家自己收窄范围：比如只放行 {@code [0-9]+[kM]} 这类容量写法，
     * 免得别的模组里「1 号扳手 / 10 号扳手」也被当成容量来排。</p>
     */
    private static final List<String> DEFAULT_NUMERIC_REGEX = List.of("[0-9]");

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment(
                "Tier groups for the additional sort; only used when the additional sort is on and the",
                "sort order is 'by mod'.",
                "",
                "One element per group, written as '<mod filter>:word1,word2,...':",
                "  - The mod filter may be omitted; '*' or an empty filter means 'any mod'.",
                "  - Within a group the words go from the lowest tier to the highest.",
                "  - Groups are tried in the order listed; the first group that matches an item wins,",
                "    so order the groups from the most specific to the most general.",
                "",
                "Words are matched against the item's registry name and its display name, anywhere in",
                "them - so every item whose name carries one of these words is affected, not just the",
                "factories and alloys the defaults were written for. Use registry name fragments",
                "(basic, advanced, ...) to keep the line short and language-independent;"
                + " 'chinese|english' works",
                "too, where either spelling matching is enough.",
                "",
                "The defaults cover (in this order):",
                "  1. Mekanism factories - basic, advanced, elite, ultimate, absolute, supreme, cosmic,",
                "     infinite (基础、高级、精英、终极、绝对、至尊、寰宇支配、悖论无限)",
                "  2. Mekanism alloys - infused, reinforced, atomic, radiance, thermonuclear, shining,",
                "     spectrum (灌注、强化、原子、辐光、热核、闪耀、光谱)",
                "  3. Powah - starter, basic, hardened, blazing, niotic, spirited, nitro, creative",
                "     (初级、基础、硬化、烈焰、钻石、富生、下界、创造)",
                "",
                "Sodium's Config API cannot present a list like this one, so it is not used here.");
        ADDITIONAL_SORT_TIERS = builder.defineList(
                "additional_sort.tiers", DEFAULT_TIERS, () -> "", element -> element instanceof String);
        builder.comment(
                "Which names take part in the numeric level of the additional sort - the last level,",
                "where 1k < 4k < 16k < 64k < 256k < 1M.",
                "",
                "How to write one: every element is a Java regular expression (java.util.regex, the same",
                "flavour mods already use), and it is searched for anywhere inside the item's display",
                "name - so '[0-9]' means 'the name contains a digit' and does not have to describe the",
                "whole name. Matching is case-sensitive unless the expression says '(?i)'. A backslash",
                "must be doubled inside a TOML string ('\\d' becomes '\\\\d'), so prefer escape-free",
                "classes such as '[0-9]+' where you can.",
                "",
                "What the order means: nothing. The elements are not tried in turn and there is no",
                "'first one wins' - a name takes part as soon as at least one of them matches anywhere,",
                "and that is all this list decides. Where such a name ends up is then decided by the",
                "numbers it contains, with a trailing k/M/G/T/P/E counted as a power of 1024 - which is",
                "why capacity chains like 1k, 4k, 16k, 64k, 256k, 1M come out in the right order.",
                "",
                "What NOT taking part means: those names are placed after the ones that take part, and",
                "among themselves they keep the plain literal order. The two groups stay apart on",
                "purpose - a comparison whose rule depended on which two names it was handed would not",
                "be consistent, and the sort would then throw 'Comparison method violates its general",
                "contract'. (The 'additional_sort.tiers' list above is the opposite case: there the",
                "listed order is the rule.)",
                "",
                "The default '[0-9]' lets every name containing a digit take part - the same set of",
                "names this level used to cover before it became configurable. Narrow it to keep",
                "numbers that are not capacities out, e.g. '[0-9]+[kKmM]' for the '1k'/'4k'/'1M' style,",
                "or '^[0-9]+k' to only take names that start with one; an empty list turns the numeric",
                "level off altogether.",
                "",
                "A malformed expression is skipped on its own - the other elements still apply and the",
                "sort keeps working. If nothing in the list can be compiled at all, the level is left",
                "wide open (no filtering) rather than silently turned off.");
        ADDITIONAL_SORT_NUMERIC_REGEX = builder.defineList(
                "additional_sort.numeric_regex", DEFAULT_NUMERIC_REGEX, () -> "",
                element -> element instanceof String);
        CLIENT_SPEC = builder.build();
    }

    private AEPDConfig() {
    }
}
