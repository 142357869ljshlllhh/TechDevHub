package com.techdevhub.controller;

import com.techdevhub.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Python AI 工具回调端点（T7）。门禁由 InternalTokenFilter（/api/v1/internal/*）承担。
 *
 * 契约来源：techdevhub-agent/docs/java_integration_deploy.md §5 ——
 *   GET  /community/hot          → get_hot 工具
 *   GET  /blog/drafts?userId=    → get_drafts 工具
 *   POST /blog/drafts            → create_draft 写工具（确认后才会调用）
 *
 * 两个关键决策：
 * 1. 返回裸 JSON（不套 Result 壳）：Python tools.py 直接 json.dumps(resp.json())
 *    当观察文本喂给 LLM，包一层 data 壳只会浪费 token 且干扰模型阅读；
 * 2. 身份信任边界：调用方是 Python 服务（X-Internal-Token 门禁），userId 由
 *    Python 侧按真实登录用户（X-User-Id）覆盖后传入，Java 不再做归属校验。
 */
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalApiController {

    private final BlogService blogService;

    /** 热点榜单：LLM 消费的是文本，字段宁少勿杂——id/title/author/热度计数即可 */
    @GetMapping("/community/hot")
    public List<Map<String, Object>> hot() {
        return blogService.hotTop10().stream()
                .map(vo -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", vo.getId());
                    item.put("title", vo.getTitle());
                    item.put("author", vo.getAuthorUsername());
                    item.put("preview", vo.getContentPreview());
                    item.put("viewCount", vo.getViewCount());
                    item.put("likeCount", vo.getLikeCount());
                    return item;
                })
                .toList();
    }

    @GetMapping("/blog/drafts")
    public List<Map<String, Object>> drafts(@RequestParam("userId") Long userId) {
        return blogService.draftsOf(userId).stream()
                .map(b -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", b.getId());
                    item.put("title", b.getTitle());
                    item.put("content", b.getContent());
                    return item;
                })
                .toList();
    }

    /** create_draft 工具回调：对端解析 data.get("id") or data.get("draftId") */
    @PostMapping("/blog/drafts")
    public Map<String, Object> createDraft(@RequestBody CreateDraftRequest request) {
        Long id = blogService.createDraft(request.userId(), request.title(), request.content());
        return Map.of("id", id);
    }

    /** create_draft 请求体：{userId, title, content}——字段名与 tools.py 逐字对齐 */
    public record CreateDraftRequest(Long userId, String title, String content) { }
}
