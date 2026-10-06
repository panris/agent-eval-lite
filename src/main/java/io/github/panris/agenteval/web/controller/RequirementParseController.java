package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.web.Constants;
import io.github.panris.agenteval.web.dto.ApiResponse;
import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.service.RequirementParser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求解析：从需求文档解析生成测试用例。
 */
@RestController
@RequestMapping("/api/testcases")
public class RequirementParseController {

    private final RequirementParser requirementParser;
    private final TestCaseRepository repository;

    public RequirementParseController(RequirementParser requirementParser, TestCaseRepository repository) {
        this.requirementParser = requirementParser;
        this.repository = repository;
    }

    /**
     * 从需求文档解析生成测试用例候选。
     */
    @Operation(summary = "从需求文档解析生成测试用例候选")
    @PostMapping("/parse-from-requirements")
    public Map<String, Object> parseFromRequirements(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ApiResponse.error("需求文档内容不能为空");
        }
        if (text.length() > Constants.MAX_REQUIREMENT_TEXT_LENGTH) {
            text = text.substring(0, Constants.MAX_REQUIREMENT_TEXT_LENGTH);
        }

        String defaultGroup = body.getOrDefault("groupId", null);
        String defaultProject = body.getOrDefault("project", null);
        String defaultModule = body.getOrDefault("module", null);
        String defaultFunction = body.getOrDefault("function", null);

        if (defaultGroup != null && defaultGroup.isBlank()) defaultGroup = null;
        if (defaultProject != null && defaultProject.isBlank()) defaultProject = null;
        if (defaultModule != null && defaultModule.isBlank()) defaultModule = null;
        if (defaultFunction != null && defaultFunction.isBlank()) defaultFunction = null;

        return requirementParser.parse(text, defaultGroup, defaultProject, defaultModule, defaultFunction);
    }

    /**
     * 保存解析后的测试用例到数据库。
     */
    @Operation(summary = "保存解析后的测试用例到数据库")
    @PostMapping("/save-parsed")
    public Map<String, Object> saveParsed(@RequestBody List<Map<String, Object>> cases) {
        if (cases == null || cases.isEmpty()) {
            return ApiResponse.error("没有要保存的测试用例");
        }
        if (cases.size() > 200) {
            return ApiResponse.error("一次最多保存 200 个测试用例");
        }

        int savedCount = 0;
        for (Map<String, Object> caseMap : cases) {
            String name = (String) caseMap.getOrDefault("name", "");
            String input = (String) caseMap.get("input");
            String expected = (String) caseMap.get("expected");

            if (input == null || input.isBlank()) continue;
            if (expected == null || expected.isBlank()) continue;

            TestCaseEntity tc = new TestCaseEntity(name, input, expected);

            if (caseMap.containsKey("groupId")) {
                String gid = (String) caseMap.get("groupId");
                if (gid != null && !gid.isBlank()) tc.setGroupId(gid);
            }
            if (caseMap.containsKey("project")) tc.setProject((String) caseMap.get("project"));
            if (caseMap.containsKey("module")) tc.setModule((String) caseMap.get("module"));
            if (caseMap.containsKey("function")) tc.setFunction((String) caseMap.get("function"));
            if (caseMap.containsKey("description")) tc.setDescription((String) caseMap.get("description"));

            repository.saveTestCase(tc);
            savedCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("saved", savedCount);
        return result;
    }
}
