package com.ai.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 搜索服务（类似豆包的联网搜索能力）
 * <p>
 * 支持多引擎：
 * 1. tavily：调用 Tavily Search API（AI 平台同款，需 API Key）— 配置后自动优先
 * 2. bing：直接调用全局 Bing（bing.com，setmkt=zh-CN），解析 HTML — 免费实时主力，无 key 默认可
 * 3. baidu：直接调用百度搜索，解析 HTML — 中文兜底（部分 IP 会触发验证码）
 * 4. searxng：调用自建 SearXNG 实例（聚合百度/Bing）— 需要容器
 * 5. jina：调用 Jina AI s.jina.ai 搜索 API（需要 API key）— 高质量备选
 * 6. auto：按可用性依次尝试 Tavily、全局 Bing、百度、SearXNG、Jina
 * <p>
 * 引擎选择优先级：per-request 参数 > 全局 searxng.engine 配置 > auto
 */
@Slf4j
@Service
public class SearxngSearchService {

    @Value("${searxng.base-url:http://localhost:8888}")
    private String baseUrl;

    @Value("${searxng.enabled:true}")
    private boolean enabled;

    @Value("${searxng.engine:auto}")
    private String engine;

    @Value("${jina.base-url:https://s.jina.ai}")
    private String jinaBaseUrl;

    @Value("${jina.api-key:}")
    private String jinaApiKey;

    @Value("${jina.enabled:true}")
    private boolean jinaEnabled;

    @Value("${tavily.base-url:https://api.tavily.com}")
    private String tavilyBaseUrl;

    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    @Value("${tavily.enabled:true}")
    private boolean tavilyEnabled;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> SUPPORTED_ENGINES = Set.of("auto", "searxng", "jina", "bing", "baidu", "tavily");
    private static final int MAX_QUERY_LENGTH = 160;
    private static final int DEFAULT_RESULT_LIMIT = 6;

    public SearxngSearchService(RestTemplateBuilder restTemplateBuilder) {
        // 搜索属于可选增强能力，必须有界：上游不可用时不能卡住整次对话。
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(8))
                .build();
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 中文停用词（单字），过滤时忽略这些字以聚焦实质关键词
     */
    private static final Set<Character> STOPWORD_CHARS = Set.of(
            '的', '了', '是', '在', '我', '你', '他', '她', '它', '们',
            '这', '那', '个', '也', '都', '而', '及', '与', '或', '就',
            '把', '被', '让', '给', '向', '为', '对', '跟', '从', '到',
            '往', '由', '自', '以', '于', '当', '之', '其', '此', '该',
            '某', '每', '各', '一', '二', '三', '四', '五', '六', '七',
            '八', '九', '十', '百', '千', '万', '亿', '么', '吗', '呢',
            '啊', '吧', '呀', '哦', '哈', '嗯', '里', '中', '上', '下',
            '前', '后', '外', '内', '侧', '边', '间', '所', '着', '过',
            '地', '得', '可', '能', '会', '要', '想', '说', '看', '做',
            '什', '怎', '样', '如', '何'
    );

    /**
     * 执行搜索（向后兼容，使用全局引擎配置）
     */
    public List<SearchResult> search(String query, int numResults) {
        return search(query, numResults, null);
    }

    /**
     * 执行搜索，支持 per-request 引擎覆盖
     *
     * @param query          搜索关键词
     * @param numResults     返回结果数量（建议 4-6 条）
     * @param engineOverride 请求级引擎覆盖：searxng/jina/auto/bing/baidu，null 则用全局配置
     * @return 搜索结果列表（空列表表示无结果或搜索未启用）
     */
    public List<SearchResult> search(String query, int numResults, String engineOverride) {
        if (!enabled) {
            log.debug("搜索未启用，跳过搜索");
            return new ArrayList<>();
        }
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) {
            return new ArrayList<>();
        }

        // 请求级参数优先，其次全局配置
        String usedEngine = resolveEngine(engineOverride);
        int limit = Math.min(Math.max(numResults, 1), DEFAULT_RESULT_LIMIT);
        // 多取一批候选再做相关性排序，避免供应商前几条广告/聚合页把真正相关结果挤掉。
        int candidateLimit = Math.max(limit * 2, 10);

        try {
            List<SearchResult> results;
            switch (usedEngine) {
                case "searxng":
                    results = searchSearxng(normalizedQuery, candidateLimit);
                    break;
                case "jina":
                    results = isJinaAvailable() ? searchJina(normalizedQuery, candidateLimit) : new ArrayList<>();
                    break;
                case "tavily":
                    results = isTavilyAvailable() ? searchTavily(normalizedQuery, candidateLimit) : new ArrayList<>();
                    break;
                case "auto":
                    results = searchAuto(normalizedQuery, candidateLimit);
                    break;
                case "bing":
                    results = searchBing(normalizedQuery, candidateLimit);
                    break;
                case "baidu":
                    results = searchBaidu(normalizedQuery, candidateLimit);
                    break;
                default:
                    results = new ArrayList<>();
                    break;
            }
            results = rankAndDedupe(results, normalizedQuery, limit);
            log.info("搜索完成: engine={}, query={}, results={}", usedEngine, normalizedQuery, results.size());
            return results;
        } catch (Exception e) {
            log.error("搜索异常: engine={}, query={}", usedEngine, normalizedQuery, e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析实际使用的引擎名（供日志/SSE 事件展示）
     */
    public String resolveEngine(String engineOverride) {
        String e = (engineOverride != null && !engineOverride.trim().isEmpty())
                ? engineOverride : this.engine;
        String normalized = e.trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_ENGINES.contains(normalized) ? normalized : "auto";
    }

    public boolean isEnabled() {
        return enabled;
    }

    private String normalizeQuery(String query) {
        if (query == null) return "";
        String normalized = query.replaceAll("\\s+", " ").trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            normalized = normalized.substring(0, MAX_QUERY_LENGTH);
        }
        return normalized;
    }

    private boolean isSearxngConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
                && !baseUrl.contains("localhost:8888")
                && !baseUrl.contains("127.0.0.1:8888");
    }

    private boolean isJinaAvailable() {
        return jinaEnabled && jinaApiKey != null && !jinaApiKey.isBlank();
    }

    private boolean isTavilyAvailable() {
        return tavilyEnabled && tavilyApiKey != null && !tavilyApiKey.isBlank();
    }

    private List<SearchResult> searchSearxngIfConfigured(String query, int numResults) {
        if (!isSearxngConfigured()) {
            return new ArrayList<>();
        }
        return searchSearxng(query, numResults);
    }

    private List<SearchResult> searchAuto(String query, int numResults) {
        // 智能回退优先级：
        // 1. Tavily（配置了 Key 时，效果最接近 AI 平台，中英双语）——可选
        // 2. 全局 Bing（www.bing.com + setmkt=zh-CN，免费、实时、结果干净）——主力
        // 3. 百度（部分 IP 会触发验证码，作为兜底）
        // 4. SearXNG / Jina（配置了才尝试）
        // 只有拿到“相关”结果才停止，避免某个引擎返回广告/固定错误页后阻断后续引擎。
        List<SearchProvider> providers = new ArrayList<>();
        if (isTavilyAvailable()) {
            providers.add(() -> searchTavily(query, numResults));
        }
        providers.add(() -> searchBing(query, numResults));
        providers.add(() -> searchBaidu(query, numResults));
        if (isSearxngConfigured()) {
            providers.add(() -> searchSearxng(query, numResults));
        }
        if (isJinaAvailable()) {
            providers.add(() -> searchJina(query, numResults));
        }

        for (SearchProvider provider : providers) {
            List<SearchResult> ranked = rankAndDedupe(provider.search(), query, numResults);
            if (!ranked.isEmpty()) return ranked;
        }
        return new ArrayList<>();
    }

    @FunctionalInterface
    private interface SearchProvider {
        List<SearchResult> search();
    }

    /**
     * SearXNG 引擎：调用 SearXNG API（JSON 格式）
     * 失败时返回空列表，不内部回退（由 auto 模式顶层编排回退）
     */
    private List<SearchResult> searchSearxng(String query, int numResults) {
        try {
            String url = baseUrl + "/search?q=" + UriUtils.encode(query.trim(), StandardCharsets.UTF_8)
                    + "&format=json&categories=general&language=zh-CN&safesearch=1";
            log.info("SearXNG 搜索: query={}", query);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("SearXNG 搜索失败: status={}", response.getStatusCode());
                return new ArrayList<>();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode resultsNode = root.get("results");
            List<SearchResult> results = new ArrayList<>();
            if (resultsNode != null && resultsNode.isArray()) {
                int count = 0;
                for (JsonNode node : resultsNode) {
                    if (count >= numResults) break;
                    String title = getText(node, "title");
                    String link = getText(node, "url");
                    String snippet = getText(node, "content");
                    if (title.isEmpty() && snippet.isEmpty()) continue;
                    results.add(new SearchResult(title, link, snippet, "SearXNG"));
                    count++;
                }
            }
            return results;
        } catch (Exception e) {
            log.error("SearXNG 搜索异常: query={}", query, e);
            return new ArrayList<>();
        }
    }

    /**
     * Jina AI 引擎：调用 s.jina.ai 搜索 API（JSON 格式）。
     * 当前接口要求 Authorization；没有 key 时由 auto 模式跳过并使用直连兜底。
     */
    private List<SearchResult> searchJina(String query, int numResults) {
        try {
            String separator = jinaBaseUrl.contains("?") ? "&" : "?";
            String url = jinaBaseUrl + separator + "q=" + UriUtils.encode(query.trim(), StandardCharsets.UTF_8);
            log.info("Jina 搜索: query={}", query);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept-Language", "zh-CN,zh;q=0.9");
            headers.set("X-Retain-Images", "none");
            headers.set("X-Return-Format", "text");
            headers.set("Authorization", "Bearer " + jinaApiKey.trim());
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Jina 搜索失败: status={}", response.getStatusCode());
                return new ArrayList<>();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode dataNode = root.isArray() ? root : root.get("data");
            List<SearchResult> results = new ArrayList<>();
            if (dataNode != null && dataNode.isArray()) {
                int count = 0;
                for (JsonNode node : dataNode) {
                    if (count >= numResults) break;
                    String title = getText(node, "title");
                    String link = getText(node, "url");
                    String snippet = getText(node, "content");
                    if (snippet.length() > 200) {
                        snippet = snippet.substring(0, 200) + "...";
                    }
                    if (title.isEmpty() && snippet.isEmpty()) continue;
                    results.add(new SearchResult(title, link, snippet, "Jina"));
                    count++;
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Jina 搜索异常: query={}", query, e);
            return new ArrayList<>();
        }
    }

    /**
     * Tavily 引擎：调用 Tavily Search API（专为 LLM 设计的联网搜索，中英双语、结果干净）。
     * 需要在配置中提供 tavily.api-key；未配置时由 auto 模式跳过并回退百度。
     */
    private List<SearchResult> searchTavily(String query, int numResults) {
        try {
            String url = tavilyBaseUrl + "/search";
            log.info("Tavily 搜索: query={}", query);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", USER_AGENT);

            Map<String, Object> body = new HashMap<>();
            body.put("api_key", tavilyApiKey.trim());
            body.put("query", query);
            body.put("max_results", Math.min(Math.max(numResults, 5), 10));
            body.put("search_depth", "basic");
            body.put("include_answer", false);
            body.put("include_raw_content", false);

            String json = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Tavily 搜索失败: status={}", response.getStatusCode());
                return new ArrayList<>();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode resultsNode = root.get("results");
            List<SearchResult> results = new ArrayList<>();
            if (resultsNode != null && resultsNode.isArray()) {
                int count = 0;
                for (JsonNode node : resultsNode) {
                    if (count >= numResults) break;
                    String title = getText(node, "title");
                    String link = getText(node, "url");
                    String snippet = getText(node, "content");
                    if (title.isEmpty() && snippet.isEmpty()) continue;
                    results.add(new SearchResult(title, link, snippet, "Tavily"));
                    count++;
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Tavily 搜索异常: query={}", query, e);
            return new ArrayList<>();
        }
    }

    /**
     * Bing 引擎：调用全局 Bing（bing.com）搜索，解析 HTML 结果。
     * 注意：不要用 cn.bing.com —— 其海外/国内访问会经常返回 SEO 垃圾站固定页（如"E4空调"），
     * 而全局 www.bing.com + setmkt=zh-CN 返回干净、真实、实时的中文结果。
     * 失败时回退百度。
     */
    private List<SearchResult> searchBing(String query, int numResults) {
        try {
            String url = "https://www.bing.com/search?q=" + UriUtils.encode(query.trim(), StandardCharsets.UTF_8)
                    + "&setmkt=zh-CN&setlang=zh-hans&count=" + Math.max(numResults, 10);
            log.info("Bing 搜索: query={}", query);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept-Language", "zh-CN,zh;q=0.9");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String html = response.getBody();
            log.info("Bing 响应: status={}, htmlLength={}", response.getStatusCode(), html != null ? html.length() : 0);

            if (!response.getStatusCode().is2xxSuccessful() || html == null || html.isEmpty()) {
                log.warn("Bing 搜索失败: status={}", response.getStatusCode());
                return searchBaidu(query, numResults);
            }

            List<SearchResult> results = parseBingHtml(html, numResults);

            if (results.isEmpty()) {
                log.warn("Bing 未解析到结果，尝试百度: query={}", query);
                return searchBaidu(query, numResults);
            }

            return results;
        } catch (Exception e) {
            log.error("Bing 搜索异常: query={}", query, e);
            return searchBaidu(query, numResults);
        }
    }

    /**
     * 百度搜索引擎：调用百度搜索，解析 HTML 结果
     * 国内中文搜索质量优于 Bing，作为 Bing 结果不相关时的回退
     */
    private List<SearchResult> searchBaidu(String query, int numResults) {
        try {
            String url = "https://www.baidu.com/s?wd=" + UriUtils.encode(query.trim(), StandardCharsets.UTF_8)
                    + "&rn=" + Math.max(numResults, 10) + "&ie=utf-8";
            log.info("百度搜索: query={}", query);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept-Language", "zh-CN,zh;q=0.9");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            headers.set("Referer", "https://www.baidu.com/");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String html = response.getBody();
            log.info("百度响应: status={}, htmlLength={}", response.getStatusCode(), html != null ? html.length() : 0);

            if (!response.getStatusCode().is2xxSuccessful() || html == null || html.isEmpty()) {
                log.warn("百度搜索失败: status={}", response.getStatusCode());
                return new ArrayList<>();
            }

            List<SearchResult> results = parseBaiduHtml(html, numResults);
            log.info("百度解析结果数: {}", results.size());
            for (int i = 0; i < Math.min(results.size(), 3); i++) {
                log.info("  百度结果[{}]: title={}", i + 1, results.get(i).getTitle());
            }

            return results;
        } catch (Exception e) {
            log.error("百度搜索异常: query={}", query, e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析百度搜索结果 HTML
     * 百度结果结构：
     * <div class="result c-container ..."><h3 class="t"><a href="...">TITLE</a></h3>...SNIPPET...</div>
     * 或 <div class="c-container ..."><h3 class="t"><a ...>TITLE</a></h3>...</div>
     */
    private List<SearchResult> parseBaiduHtml(String html, int numResults) {
        List<SearchResult> results = new ArrayList<>();
        if (html == null || html.isEmpty()) return results;

        // 匹配结果块：class 含 "result" 或 "c-container"
        Pattern blockPattern = Pattern.compile(
                "<div[^>]*class=\"[^\"]*(?:result|c-container)[^\"]*\"[^>]*>(.*?)(?=<div[^>]*class=\"[^\"]*(?:result|c-container)|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        // 匹配 h3 > a 标签
        Pattern h3Pattern = Pattern.compile(
                "<h3[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        // snippet 在 class 含 abstract/content 的标签中
        Pattern snippetPattern = Pattern.compile(
                "<(?:div|span)[^>]*class=\"[^\"]*(?:abstract|content|text)[^\"]*\"[^>]*>(.*?)</(?:div|span)>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        Matcher blockMatcher = blockPattern.matcher(html);
        while (blockMatcher.find() && results.size() < numResults) {
            String block = blockMatcher.group(1);
            Matcher h3Matcher = h3Pattern.matcher(block);
            if (!h3Matcher.find()) continue;

            String url = h3Matcher.group(1).trim();
            String title = stripTags(h3Matcher.group(2)).trim();
            if (title.isEmpty()) continue;

            // 百度的 URL 是跳转链接，跳过 baidu.com 自身链接
            if (url.contains("baidu.com/link") == false && url.contains("baidu.com/")) continue;

            String snippet = "";
            Matcher snippetMatcher = snippetPattern.matcher(block);
            if (snippetMatcher.find()) {
                snippet = stripTags(snippetMatcher.group(1)).trim();
            }
            if (snippet.length() > 200) {
                snippet = snippet.substring(0, 200) + "...";
            }
            results.add(new SearchResult(title, url, snippet, "Baidu"));
        }
        return results;
    }

    /**
     * 解析 Bing 搜索结果 HTML
     * Bing 结果结构（2026 版）：
     * <li class="b_algo" data-id iid=SERP.xxxx>
     *   <div class="b_tpcn">...</div>
     *   <h2 class=""><a target="_blank" href="URL" h="ID=...">TITLE</a></h2>
     *   <p class="b_lineclamp...">SNIPPET</p>
     * </li>
     */
    private List<SearchResult> parseBingHtml(String html, int numResults) {
        List<SearchResult> results = new ArrayList<>();
        if (html == null || html.isEmpty()) return results;

        // 匹配 b_algo 结果块（非贪婪到下一个 b_algo 或文档末尾）
        Pattern blockPattern = Pattern.compile(
                "<li[^>]*class=\"[^\"]*b_algo[^\"]*\"[^>]*>(.*?)(?=<li[^>]*class=\"[^\"]*b_algo|</ol>|</ul>|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        // 匹配 h2 > a 标签（href 可能在其他属性之后）
        Pattern h2Pattern = Pattern.compile(
                "<h2[^>]*>\\s*<a[^>]*href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        // snippet 可能在不规则容器中
        Pattern snippetPattern = Pattern.compile(
                "<p[^>]*>(.*?)</p>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        Matcher blockMatcher = blockPattern.matcher(html);
        while (blockMatcher.find() && results.size() < numResults) {
            String block = blockMatcher.group(1);
            Matcher h2Matcher = h2Pattern.matcher(block);
            if (!h2Matcher.find()) continue;

            String url = h2Matcher.group(1).trim();
            String title = stripTags(h2Matcher.group(2)).trim();
            if (title.isEmpty()) continue;

            // 过滤 Bing 内部链接（bing.com、msn.com 等）
            if (url.contains("bing.com/") || url.contains("msn.com/")) continue;

            String snippet = "";
            Matcher snippetMatcher = snippetPattern.matcher(block);
            while (snippetMatcher.find()) {
                String candidate = stripTags(snippetMatcher.group(1)).trim();
                if (candidate.length() > 20) {
                    snippet = candidate;
                    break;
                }
            }
            if (snippet.length() > 200) {
                snippet = snippet.substring(0, 200) + "...";
            }
            results.add(new SearchResult(title, url, snippet, "Bing"));
        }
        return results;
    }

    /**
     * 去重并按标题/摘要相关性重排。
     * <p>
     * 搜索供应商的原始排序不能直接当作最终排序：聚合源常会返回重复页面、广告页或只在摘要中命中的结果。
     * 这里保持轻量实现，不引入本地向量模型；没有达到最低相关性时返回空，让模型明确说“没有找到”。
     */
    private List<SearchResult> rankAndDedupe(List<SearchResult> results, String query, int numResults) {
        if (results == null || results.isEmpty()) return new ArrayList<>();

        Map<String, SearchResult> unique = new LinkedHashMap<>();
        for (SearchResult result : results) {
            if (result == null) continue;
            String title = stripTags(result.getTitle());
            String snippet = stripTags(result.getSnippet());
            String url = result.getUrl() == null ? "" : result.getUrl().trim();
            if (title.isEmpty() && snippet.isEmpty()) continue;
            if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) continue;
            String key = url.isEmpty() ? title.toLowerCase(Locale.ROOT) : canonicalUrl(url);
            unique.putIfAbsent(key, new SearchResult(title, url, snippet, result.getSource()));
        }

        List<ScoredResult> scored = new ArrayList<>();
        for (SearchResult result : unique.values()) {
            double score = relevanceScore(result, query);
            if (score >= 1.0d) {
                scored.add(new ScoredResult(result, score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredResult::score).reversed());

        List<SearchResult> ranked = new ArrayList<>();
        for (ScoredResult item : scored) {
            if (ranked.size() >= numResults) break;
            ranked.add(item.result());
        }
        if (ranked.isEmpty() && !unique.isEmpty()) {
            log.warn("搜索结果未达到相关性阈值: query={}, candidates={}", query, unique.size());
        }
        return ranked;
    }

    private double relevanceScore(SearchResult result, String query) {
        String normalizedQuery = normalizeForMatch(query);
        String title = normalizeForMatch(result.getTitle());
        String snippet = normalizeForMatch(result.getSnippet());
        String body = title + " " + snippet;
        if (normalizedQuery.isEmpty() || body.isBlank()) return 0d;

        Set<String> terms = extractSearchTerms(query);

        // 统计“实质性”的单字中文词（已排除停用词），用于判定查询需命中多少字才算“相关”。
        // 防止只命中单个常见字（如“价”）的垃圾结果（例：搜“今日金价”返回“别克E4报价”仅含“价”字）被误判为相关。
        long distinctChineseChars = terms.stream()
                .filter(t -> t.length() == 1)
                .filter(t -> {
                    char c = t.charAt(0);
                    return c >= 0x4E00 && c <= 0x9FFF;
                }).count();

        double score = 0d;
        if (title.contains(normalizedQuery)) score += 8d;
        if (snippet.contains(normalizedQuery)) score += 3d;

        int matchedTerms = 0;
        for (String term : terms) {
            if (title.contains(term)) {
                score += term.length() == 1 ? 1.1d : 3.2d;
                matchedTerms++;
            } else if (body.contains(term)) {
                score += term.length() == 1 ? 0.35d : 1.4d;
                matchedTerms++;
            }
        }
        if (matchedTerms == 0) return 0d;

        // 对中文查询，要求“查询的二元词（bigram）”至少命中一个，单字/散字命中无法通过。
        // 例如搜“今日金价”需正文出现“今日/日金/金价”其一；仅含“日”（6月8日）和“价”（报价）的无关页会被过滤。
        if (distinctChineseChars >= 2) {
            int require = Math.max(2, (int) Math.ceil(distinctChineseChars * 0.6));
            long matchedChinese = terms.stream()
                    .filter(t -> t.length() == 1)
                    .filter(t -> {
                        char c = t.charAt(0);
                        return c >= 0x4E00 && c <= 0x9FFF && (title.contains(t) || body.contains(t));
                    }).count();
            boolean bigramHit = hasQueryBigramTitleSnippet(normalizedQuery, title, snippet);
            // 纯多字中文查询：必须命中至少一个连续二元词，或命中足够多的字（宽松下限仅适用于二元词无法构成时）
            if (bigramHit) {
                // 二元词已命中即视为相关入口，仍保留词频计分
            } else if (matchedChinese < require) {
                return 0d;
            }
        }

        String host = hostOf(result.getUrl());
        if (isAuthoritativeHost(host)) score += 1.25d;
        if (title.length() >= 8) score += 0.25d;
        return score;
    }

    /**
     * 判断查询中是否存在一个“连续中文二元词”出现在结果的标题或摘要里。
     * 用连续二元词可避免只命中单个常见字（日期/后缀如“日”“价”）的无关页被误判为相关。
     */
    private boolean hasQueryBigramTitleSnippet(String query, String title, String snippet) {
        int len = query.length();
        if (len < 2) return false;
        for (int i = 0; i + 1 < len; i++) {
            char c1 = query.charAt(i);
            char c2 = query.charAt(i + 1);
            if (c1 >= 0x4E00 && c1 <= 0x9FFF && c2 >= 0x4E00 && c2 <= 0x9FFF) {
                String bigram = "" + c1 + c2;
                if (title.contains(bigram) || snippet.contains(bigram)) return true;
            }
        }
        return false;
    }

    private Set<String> extractSearchTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        String normalized = normalizeForMatch(query);
        Matcher asciiMatcher = Pattern.compile("[a-z0-9][a-z0-9._+/#-]{1,}").matcher(normalized);
        while (asciiMatcher.find()) terms.add(asciiMatcher.group());
        for (char c : normalized.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF && !STOPWORD_CHARS.contains(c)) {
                terms.add(String.valueOf(c));
            }
        }
        if (terms.isEmpty() && !normalized.isBlank()) terms.add(normalized);
        return terms;
    }

    private String normalizeForMatch(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String canonicalUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host + path;
        } catch (Exception ignored) {
            return url.toLowerCase(Locale.ROOT).replaceAll("[?#].*$", "");
        }
    }

    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isAuthoritativeHost(String host) {
        if (host == null || host.isBlank()) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".gov.cn") || normalized.endsWith(".edu.cn")
                || normalized.contains("github.com") || normalized.startsWith("docs.")
                || normalized.startsWith("developer.");
    }

    private record ScoredResult(SearchResult result, double score) {
    }

    /**
     * 去除 HTML 标签，保留纯文本
     */
    private String stripTags(String html) {
        if (html == null) return "";
        String text = html.replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
        Matcher entityMatcher = Pattern.compile("&#(?:(?i:x)([0-9a-f]+)|([0-9]+));").matcher(text);
        StringBuffer decoded = new StringBuffer();
        while (entityMatcher.find()) {
            try {
                int radix = entityMatcher.group(1) != null ? 16 : 10;
                String digits = entityMatcher.group(1) != null ? entityMatcher.group(1) : entityMatcher.group(2);
                int codePoint = Integer.parseInt(digits, radix);
                String replacement = Character.isValidCodePoint(codePoint)
                        ? new String(Character.toChars(codePoint)) : "";
                entityMatcher.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
            } catch (RuntimeException ignored) {
                entityMatcher.appendReplacement(decoded, Matcher.quoteReplacement(entityMatcher.group()));
            }
        }
        entityMatcher.appendTail(decoded);
        return decoded.toString().replaceAll("\\s+", " ").trim();
    }

    private String getText(JsonNode node, String field) {
        JsonNode val = node.get(field);
        return val != null && !val.isNull() ? val.asText() : "";
    }

    /**
     * 把搜索结果拼接为可注入 prompt 的上下文文本
     */
    public String buildSearchContext(List<SearchResult> results) {
        if (results == null || results.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        LocalDateTime now = LocalDateTime.now();
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE HH:mm", Locale.CHINA));
        sb.append("【当前系统时间】").append(dateStr).append("（请以此为准回答日期/时间类问题）。\n\n");
        sb.append("以下是根据用户问题从互联网搜索到的参考资料，请结合「当前系统时间」和这些信息回答用户问题。\n");
        sb.append("重要规则：\n");
        sb.append("1. 对于「今天几号/星期几/现在几点」等日期时间问题，直接用上方的「当前系统时间」回答，不要依赖搜索结果。\n");
        sb.append("2. 搜索结果可能过时或不准确，请交叉验证后再回答；与问题明显无关时可直接忽略。\n");
        sb.append("3. 如果搜索结果与用户问题明显不相关（如用户问某只股票但搜索结果是其他无关内容），请明确告知「未能从搜索中找到相关信息」，不要编造答案。\n");
        sb.append("4. 回答时适当标注参考来源序号，帮助用户验证。\n\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("【参考").append(i + 1).append("】");
            if (!r.title.isEmpty()) sb.append(r.title);
            sb.append("\n");
            if (!r.snippet.isEmpty()) sb.append(r.snippet).append("\n");
            if (!r.source.isEmpty()) sb.append("搜索引擎: ").append(r.source).append("\n");
            if (!r.url.isEmpty()) sb.append("来源: ").append(r.url).append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 搜索结果数据结构
     */
    public static class SearchResult {
        private final String title;
        private final String url;
        private final String snippet;
        private final String source;

        public SearchResult(String title, String url, String snippet) {
            this(title, url, snippet, "");
        }

        public SearchResult(String title, String url, String snippet, String source) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
            this.source = source == null ? "" : source;
        }

        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public String getSnippet() { return snippet; }
        public String getSource() { return source; }
    }
}
