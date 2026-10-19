package com.ai.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 搜索意图判断服务
 * 分析用户问题是否需要联网搜索，避免对所有问题都触发搜索
 * <p>
 * 策略（轻量级，零成本，不依赖额外模型调用）：
 * 1. 显式搜索触发词（"搜一下"、"帮我查"等）→ 搜索
 * 2. 时效性关键词（"最新"、"今天"、"现在"等）→ 搜索
 * 3. 事实性查询模式（价格、天气、排名等）→ 搜索
 * 4. 闲聊/短消息 → 不搜索
 */
@Slf4j
@Service
public class SearchIntentService {

    // 时效性关键词（触发搜索）
    private static final Set<String> TIME_SENSITIVE_KEYWORDS = Set.of(
            "最新", "今天", "今日", "昨日", "昨天", "明天", "本周", "上周", "下周",
            "本月", "上月", "本年", "近期", "最近", "现在", "目前", "当前", "实时", "热搜",
            "新闻", "快讯", "消息", "报道",
            "latest", "today", "yesterday", "recent", "current", "now", "news"
    );

    // 常见资料型问题：即使没有“搜索/最新”等字样，也应给模型补充可核验来源。
    private static final Set<String> RESEARCH_KEYWORDS = Set.of(
            "推荐", "对比", "比较", "评测", "教程", "文档", "接口", "api", "开源",
            "官网", "兼容", "政策", "法规", "标准", "下载", "安装", "配置", "依赖",
            "recommend", "compare", "comparison", "tutorial", "documentation", "open source"
    );

    // 搜索意图关键词
    private static final Set<String> SEARCH_TRIGGERS = Set.of(
            "搜索", "搜一下", "查一下", "查询", "帮我查", "搜索一下", "联网搜",
            "上网查", "网上查", "百度一下", "谷歌一下",
            "search", "google it", "look up"
    );

    // 事实性查询模式（XX价格、天气、排名等需要实时信息的问题）
    private static final Pattern FACTUAL_PATTERN = Pattern.compile(
            ".*(价格|多少钱|股价|汇率|天气|气温|比分|排名|排行榜|上榜|上映|发布|发售|开售|开服|什么时候|几点|哪里有|怎么去|路线|地址|电话|官网|更新|版本|补丁|公告|声明|回应|回应了).*"
    );

    private static final Pattern YEAR_PATTERN = Pattern.compile(".*20\\d{2}.*");

    // 应跳过搜索的闲聊模式
    private static final Pattern CHITCHAT_PATTERN = Pattern.compile(
            "^(你好|您好|hi|hello|hey|嗨|在吗|在不在|谢谢|感谢|多谢|再见|拜拜|晚安|早安|早上好|下午好|晚上好|好的|收到|明白|了解)[\\s!！.。?？~～]*$",
            Pattern.CASE_INSENSITIVE
    );

    // 纯日期/时间询问（不需要搜索，系统时间已在prompt中由 buildSearchContext 注入）
    // 仅匹配"今天星期几""现在几点"等纯时间问题，不影响"今天的新闻""今天的股市"等需要搜索的问题
    private static final Pattern DATETIME_QUERY_PATTERN = Pattern.compile(
            "^(今天|今日|现在|当前|今年).{0,8}(星期几|星期几了|周几|几号|几号了|几日|几点|几点了|几月几号|几月几日|什么日期|什么时间|什么日子|是哪一年|是哪年|日期是|几月).*$"
    );

    /**
     * 判断用户消息是否需要搜索
     */
    public boolean needsSearch(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) return false;
        String msg = userMessage.trim();

        // 闲聊不搜索
        if (CHITCHAT_PATTERN.matcher(msg).matches()) return false;

        // 纯日期/时间问题不搜索（系统时间已在prompt中注入，AI可直接回答，无需联网）
        if (DATETIME_QUERY_PATTERN.matcher(msg).matches()) return false;

        // 太短的消息不搜索（避免对"嗯"、"好的"等短消息触发）
        if (msg.length() < 4) return false;

        String lowerMsg = msg.toLowerCase(Locale.ROOT);

        // 1. 显式搜索触发词
        for (String trigger : SEARCH_TRIGGERS) {
            if (lowerMsg.contains(trigger.toLowerCase(Locale.ROOT))) return true;
        }

        // 2. 时效性关键词
        for (String kw : TIME_SENSITIVE_KEYWORDS) {
            if (lowerMsg.contains(kw.toLowerCase(Locale.ROOT))) return true;
        }

        // 年份不能写死，否则跨年后会漏掉“2027/2028 年政策”等问题。
        if (YEAR_PATTERN.matcher(msg).matches()) return true;

        // 3. 资料型问题
        for (String keyword : RESEARCH_KEYWORDS) {
            if (lowerMsg.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }

        // 4. 事实性查询模式
        if (FACTUAL_PATTERN.matcher(msg).matches()) return true;

        return false;
    }

    /**
     * 从用户消息中提取搜索关键词
     * 去掉搜索触发词前缀和疑问语气词，保留核心问题
     */
    public String extractSearchQuery(String userMessage) {
        if (userMessage == null) return "";
        String query = userMessage.trim();
        // 只清理句首的搜索命令，保留完整自然语言问题；不要把“是什么/多少/如何”等语义词从句中间删掉。
        query = query.replaceFirst("^请问\\s*", "");
        query = query.replaceFirst("^(?:帮我\\s*)?(?:搜索一下|搜一下|查一下|查询|搜索|搜|联网搜)\\s*", "");
        query = query.replaceFirst("^(?:帮我\\s*)?(?:百度|谷歌|bing)(?:一下)?\\s*", "");
        query = query.replaceFirst("^(?:上网|网上)(?:查|搜索)\\s*", "");
        query = query.replaceAll("\\s+", " ").trim();
        query = query.replaceAll("[，,。！？!?；;：:]+$", "").trim();
        query = query.replaceAll("(?:吗|呢|啊|吧|呀|哦)+$", "").trim();
        // 限制长度，避免过长的搜索词
        if (query.length() > 160) {
            query = query.substring(0, 160);
        }
        return query.isEmpty() ? userMessage.trim() : query;
    }
}
