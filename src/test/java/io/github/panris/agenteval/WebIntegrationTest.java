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
}
