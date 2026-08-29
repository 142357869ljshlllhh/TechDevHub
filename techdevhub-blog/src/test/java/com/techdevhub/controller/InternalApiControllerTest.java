package com.techdevhub.controller;

import com.techdevhub.entity.BlogInfo;
import com.techdevhub.filter.InternalTokenFilter;
import com.techdevhub.service.BlogService;
import com.techdevhub.vo.BlogSummaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T7 DoD：Python 工具回调端点契约测试。
 * 关键断言：裸 JSON（无 Result 壳）、create_draft 返回 {"id":...}（tools.py 解析对齐）、
 * 门禁缺 401/错 403。
 */
class InternalApiControllerTest {

    private BlogService blogService;
    private MockMvc secured;
    private MockMvc open;

    @BeforeEach
    void setUp() {
        blogService = Mockito.mock(BlogService.class);
        InternalApiController controller = new InternalApiController(blogService);
        secured = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new InternalTokenFilter("secret-token")).build();
        open = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new InternalTokenFilter("")).build();
    }

    @Test
    void missingToken_401_wrongToken_403() throws Exception {
        secured.perform(get("/api/v1/internal/community/hot"))
                .andExpect(status().isUnauthorized());
        secured.perform(get("/api/v1/internal/community/hot")
                        .header("X-Internal-Token", "bad"))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyTokenConfig_allowsLocalDebug() throws Exception {
        open.perform(get("/api/v1/internal/community/hot"))
                .andExpect(status().isOk());
    }

    @Test
    void hot_returnsBareJsonArrayForLlmObservation() throws Exception {
        when(blogService.hotTop10()).thenReturn(List.of(
                new BlogSummaryVO(1L, 9L, "alice", "Spring 事务失效", "内容预览", 2L,
                        1, 5, 100, 3, null)));
        // 裸数组：tools.py 会 json.dumps 整个响应当 LLM 观察文本，不能有 data 壳
        secured.perform(get("/api/v1/internal/community/hot")
                        .header("X-Internal-Token", "secret-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Spring 事务失效"))
                .andExpect(jsonPath("$[0].author").value("alice"));
    }

    @Test
    void drafts_returnsIdTitleContent() throws Exception {
        BlogInfo draft = new BlogInfo();
        draft.setId(7L);
        draft.setTitle("草稿A");
        draft.setContent("正文");
        draft.setStatus(0);
        when(blogService.draftsOf(9L)).thenReturn(List.of(draft));

        secured.perform(get("/api/v1/internal/blog/drafts")
                        .header("X-Internal-Token", "secret-token")
                        .param("userId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].title").value("草稿A"));
    }

    @Test
    void createDraft_returnsBareIdObject() throws Exception {
        when(blogService.createDraft(eq(9L), anyString(), anyString())).thenReturn(99L);
        // 对端契约：data.get("id") or data.get("draftId") —— 必须是裸 {"id":99}
        secured.perform(post("/api/v1/internal/blog/drafts")
                        .header("X-Internal-Token", "secret-token")
                        .contentType("application/json")
                        .content("{\"userId\":9,\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99));
        verify(blogService).createDraft(9L, "t", "c");
    }
}
