package io.github.panris.agenteval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: boots the full Spring context against an in-memory SQLite DB
 * (activated via the 'test' profile, see application-test.yml) and exercises the
 * real wiring end-to-end (controller → service → JPA → SQLite).
 *
 * This closes the long-standing gap of "no integration / front-end tests": every
 * application page template is rendered through the Thymeleaf view resolver (so a
 * broken template fails the build), and a real evaluate flow persists a report.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void allApplicationPagesRenderWithHttp200() throws Exception {
        for (String path : List.of("/", "/manage", "/agents", "/eval-config", "/eval-llm-config")) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void healthAndReportsEndpointsReturnOk() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").exists())
                .andExpect(jsonPath("$.filtered").exists())
                .andExpect(jsonPath("$.reports").exists());
    }

    @Test
    void endToEndCreateCaseThenEvaluateCreatesReport() throws Exception {
        // 1) create a test case
        String createBody = mockMvc.perform(post("/api/testcases")
                        .contentType("application/json")
                        .content("{\"name\":\"it-case\",\"input\":\"2+2=?\",\"expected\":\"4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode createNode = objectMapper.readTree(createBody);
        String caseId = createNode.get("testCase").get("id").asText();

        // 2) evaluate by case id with the built-in demo agent (no config needed)
        mockMvc.perform(post("/api/evaluate/cases")
                        .contentType("application/json")
                        .content("{\"caseIds\":[\"" + caseId + "\"],\"metrics\":[\"correctness\"],\"agentType\":\"demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reportId").exists());

        // 3) the report is now persisted and visible via the reports API
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)));
    }

    // ============ 前端回归测试（填补前端 0 自动化测试缺口）============

    /**
     * 回归守卫：所有页面必须引用外部 JS（非空内联 script），防止 P2 内联 script 清零工作回退。
     * 渲染后的内联脚本表现为 {@code <script>}（无 src 属性），外部脚本为 {@code <script src=...>}。
     */
    @Test
    void allPagesReferenceExternalScriptsNotInline() throws Exception {
        for (String path : List.of("/", "/manage", "/agents", "/eval-config", "/eval-llm-config", "/share/abc")) {
            String html = mockMvc.perform(get(path))
                    .andReturn().getResponse().getContentAsString();
            // 不存在无属性的 <script> 开标签（即内联脚本）
            org.junit.jupiter.api.Assertions.assertFalse(
                    html.contains("<script>"),
                    "页面 " + path + " 存在内联 <script> 块，应抽取为外部 JS 文件");
        }
    }

    /**
     * 首页关键统计/容器元素必须存在于渲染结果中（Thymeleaf 模板未被破坏）。
     */
    @Test
    void indexPageRendersKeyStatsElements() throws Exception {
        String html = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (String id : List.of("totalTestCases", "totalReports", "avgPassRate",
                "avgResponseTime", "recentReportsContent", "systemStatusContent")) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    html.contains("id=\"" + id + "\""),
                    "首页缺少关键元素 #" + id);
        }
    }

    /**
     * 管理页关键表格容器必须存在（历史/用例/评测结果区域）。
     */
    @Test
    void managePageRendersKeyTables() throws Exception {
        String html = mockMvc.perform(get("/manage"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (String id : List.of("history-table", "cases-table", "eval-table")) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    html.contains("id=\"" + id + "\""),
                    "管理页缺少关键表格 #" + id);
        }
    }

    /**
     * Actuator 监控端点可用（test profile 暴露 health/info/metrics）。
     */
    @Test
    void actuatorEndpointsAreExposed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }
}
