package com.neon10.ratatoskr.ai

import kotlin.random.Random

class SimpleAiService : AiService {
    private enum class Theme { GREET, SCHEDULE, PRICE, APOLOGY, GENERAL }

    override suspend fun generateReplies(context: String?, limit: Int, prefs: StylePrefs): List<ReplyOption> {
        val t = Theme.entries[Random.nextInt(Theme.entries.size)]
        val v = variants(t)
        return listOf(
            ReplyOption("保守", v.conservative.random()),
            ReplyOption("激进", v.aggressive.random()),
            ReplyOption("出其不意", v.unexpected.random())
        )
    }

    private fun detectTheme(ctx: String?): Theme {
        val s = ctx?.lowercase()?.trim().orEmpty()
        return when {
            s.contains("你好") || s.contains("hi") || s.contains("hello") -> Theme.GREET
            s.contains("约") || s.contains("一起") || s.contains("吃饭") || s.contains("晚饭") || s.contains("见面") -> Theme.SCHEDULE
            s.contains("价格") || s.contains("报价") || s.contains("多少钱") || s.contains("费用") -> Theme.PRICE
            s.contains("抱歉") || s.contains("迟到") || s.contains("晚点") || s.contains("对不起") -> Theme.APOLOGY
            else -> Theme.entries[Random.nextInt(Theme.entries.size)]
        }
    }

    private data class Variants(
        val conservative: List<String>,
        val aggressive: List<String>,
        val unexpected: List<String>
    )

    private fun variants(t: Theme): Variants = when (t) {
        Theme.GREET -> Variants(
            conservative = listOf("你好～很高兴收到你的消息", "在呢，有什么我可以帮忙的？"),
            aggressive = listOf("要不我们直接电话聊 5 分钟？", "方便的话，现在就开始吧？"),
            unexpected = listOf("正在加载社交 buff…完成！你好呀", "今天的开场白由你来定？我跟上～")
        )
        Theme.SCHEDULE -> Variants(
            conservative = listOf("可以的，你看今晚 7 点合适吗？", "本周内都可约，你更偏向哪天？"),
            aggressive = listOf("不如就定明天 7 点地铁A口的咖啡店？", "要不要直接安排今天下班后见面？"),
            unexpected = listOf("掷个硬币决定时间，正面今晚，反面周末～", "我来带甜点，你来定地点，成交？")
        )
        Theme.PRICE -> Variants(
            conservative = listOf("我们有标准价与套餐价，你更关注哪种？", "价格透明，可按需求组合"),
            aggressive = listOf("基础版 X 元，进阶版 Y 元，现在下单享折扣", "这周签约可享专属优惠，考虑一下？"),
            unexpected = listOf("先不谈数字，聊聊你最在意的价值点？", "给你一个神秘价位区间，猜中有奖励～")
        )
        Theme.APOLOGY -> Variants(
            conservative = listOf("抱歉让你久等了，我这就处理", "对不起，稍晚回复，正在跟进"),
            aggressive = listOf("能否再给我 10 分钟，我会第一时间回复你", "我优先把这件事闭环，稍后把结果发你"),
            unexpected = listOf("时间管理系统刚重启成功…", "我给你点个道歉奶茶可还行？")
        )
        Theme.GENERAL -> Variants(
            conservative = listOf("我理解你的想法，我们可以一步一步来", "如果复杂，我们先拆成小目标"),
            aggressive = listOf("要不直接定个行动项？我先做 A，你看 B", "给我一个范围，我先给出初版方案"),
            unexpected = listOf("已开启高情商模式，请继续输入～", "我来当气氛组，你来当决策官？")
        )
    }
}
