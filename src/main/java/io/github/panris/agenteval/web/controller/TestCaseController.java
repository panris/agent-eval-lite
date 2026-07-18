package io.github.panris.agenteval.web.controller;


import io.github.panris.agenteval.web.Constants;
import io.github.panris.agenteval.web.dto.ApiResponse;
import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.service.RequirementParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for test case management.
 */
@RestController
@RequestMapping("/api/testcases")
public class TestCaseController {

    private static final Logger log = LoggerFactory.getLogger(TestCaseController.class);

    private final TestCaseRepository repository;
    private final RequirementParser requirementParser;

    public TestCaseController(TestCaseRepository repository, RequirementParser requirementParser) {
        this.repository = repository;
        this.requirementParser = requirementParser;
    }

    /**
     * Create a new test case.
     */
    @Operation(summary = "创建新的测试用例")
    @PostMapping
    public Map<String, Object> createTestCase(@RequestBody TestCaseRequest request) {
        Map<String, Object> valError = validateInput(request);
        if (valError != null) return valError;
        TestCaseEntity testCase = new TestCaseEntity(
            request.getName(),
            request.getInput(),
            request.getExpected()
        );
        testCase.setGroupId(request.getGroupId());
        testCase.setProject(request.getProject());
        testCase.setModule(request.getModule());
        testCase.setFunction(request.getFunction());
        testCase.setDescription(request.getDescription());
        testCase.setMetadata(request.getMetadata());

        TestCaseEntity saved = repository.saveTestCase(testCase);

        return Map.of(
            "success", true,
            "testCase", saved
        );
    }

    /**
     * List test cases with pagination.
     */
    @Operation(summary = "分页列出测试用例，支持按分组和关键词过滤")
    @GetMapping
    public Map<String, Object> listTestCases(
        @RequestParam(required = false) String groupId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword
    ) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > Constants.MAX_BATCH_SIZE) size = Constants.MAX_BATCH_SIZE;

        List<TestCaseEntity> testCases;
        int total;

        if (groupId != null && !groupId.isEmpty()) {
            List<TestCaseEntity> filtered = repository.findTestCasesByGroupId(groupId);
            total = filtered.size();
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase();
                filtered = filtered.stream()
                    .filter(tc ->
                        (tc.getName() != null && tc.getName().toLowerCase().contains(kw)) ||
                        (tc.getInput() != null && tc.getInput().toLowerCase().contains(kw)) ||
                        (tc.getExpected() != null && tc.getExpected().toLowerCase().contains(kw)) ||
                        (tc.getDescription() != null && tc.getDescription().toLowerCase().contains(kw)) ||
                        (tc.getMetadata() != null && tc.getMetadata().toString().toLowerCase().contains(kw))
                    )
                    .toList();
            }
            total = filtered.size();
            int from = (page - 1) * size;
            if (from >= filtered.size()) {
                testCases = List.of();
            } else {
                testCases = filtered.subList(from, Math.min(from + size, filtered.size()));
            }
        } else {
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase();
                testCases = repository.findAllTestCases().stream()
                    .filter(tc ->
                        (tc.getName() != null && tc.getName().toLowerCase().contains(kw)) ||
                        (tc.getInput() != null && tc.getInput().toLowerCase().contains(kw)) ||
                        (tc.getExpected() != null && tc.getExpected().toLowerCase().contains(kw)) ||
                        (tc.getDescription() != null && tc.getDescription().toLowerCase().contains(kw)) ||
                        (tc.getMetadata() != null && tc.getMetadata().toString().toLowerCase().contains(kw))
                    )
                    .toList();
                total = testCases.size();
                int from = (page - 1) * size;
                if (from >= testCases.size()) {
                    testCases = List.of();
                } else {
                    testCases = testCases.subList(from, Math.min(from + size, testCases.size()));
                }
            } else {
                testCases = repository.findAllTestCasesPage(page, size);
                total = repository.countAllTestCases();
            }
        }

        int totalPages = (int) Math.ceil((double) total / size);

        return Map.of(
            "success", true,
            "testCases", testCases,
            "total", total,
            "page", page,
            "size", size,
            "totalPages", totalPages
        );
    }

    /**
     * 获取所有测试用例的三维分组去重值，用于前端下拉框。
     */
    @Operation(summary = "获取所有三维分组去重值（projects/modules/functions）")
    @GetMapping("/dimensions")
    public Map<String, Object> getDimensions() {
        return Map.of(
            "success", true,
            "projects", repository.findDistinctProjects(),
            "modules", repository.findDistinctModules(),
            "functions", repository.findDistinctFunctions()
        );
    }

    /**
     * Get a specific test case.
     */
    @Operation(summary = "获取指定测试用例详情")
    @GetMapping("/{id}")
    public Map<String, Object> getTestCase(@PathVariable String id) {
        return repository.findTestCaseById(id)
            .map(tc -> Map.<String, Object>of(
                "success", true,
                "testCase", tc
            ))
            .orElse(Map.of(
                "success", false,
                "error", "测试用例不存在"
            ));
    }

    /**
     * Update a test case.
     */
    @Operation(summary = "更新指定测试用例内容")
    @PutMapping("/{id}")
    public Map<String, Object> updateTestCase(
        @PathVariable String id,
        @RequestBody TestCaseRequest request
    ) {
        Map<String, Object> valError = validateInput(request);
        if (valError != null) return valError;
        return repository.findTestCaseById(id)
            .map(tc -> {
                tc.setName(request.getName());
                tc.setInput(request.getInput());
                tc.setExpected(request.getExpected());
                if (request.getGroupId() != null) {
                    tc.setGroupId(request.getGroupId());
                }
                if (request.getProject() != null) {
                    tc.setProject(request.getProject());
                }
                if (request.getModule() != null) {
                    tc.setModule(request.getModule());
                }
                if (request.getFunction() != null) {
                    tc.setFunction(request.getFunction());
                }
                if (request.getDescription() != null) {
                    tc.setDescription(request.getDescription());
                }
                if (request.getMetadata() != null) {
                    tc.setMetadata(request.getMetadata());
                }
                TestCaseEntity saved = repository.saveTestCase(tc);
                return Map.<String, Object>of(
                    "success", true,
                    "testCase", saved
                );
            })
            .orElse(Map.of(
                "success", false,
                "error", "测试用例不存在"
            ));
    }

    /**
     * Delete a test case.
     */
    @Operation(summary = "删除指定测试用例")
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteTestCase(@PathVariable String id) {
        if (repository.findTestCaseById(id).isPresent()) {
            repository.deleteTestCase(id);
            return Map.of(
                "success", true,
                "message", "Test case deleted"
            );
        }
        return Map.of(
            "success", false,
            "error", "测试用例不存在"
        );
    }
    
    /**
     * Update test case tags.
     */
    @Operation(summary = "批量更新测试用例标签")
    @PutMapping("/{id}/tags")
    public Map<String, Object> updateTags(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        if (body == null || !body.containsKey("tags")) {
            return ApiResponse.error("tags 不能为空");
        }
        Object tagsObj = body.get("tags");
        if (!(tagsObj instanceof List)) {
            return ApiResponse.error("tags 必须是一个列表");
        }
        Optional<TestCaseEntity> opt = repository.findTestCaseById(id);
        if (opt.isEmpty()) {
            return ApiResponse.error("测试用例不存在");
        }
        TestCaseEntity tc = opt.get();
        if (tc.getMetadata() == null) {
            tc.setMetadata(new java.util.LinkedHashMap<>());
        }
        tc.getMetadata().put("tags", tagsObj);
        tc.updateTimestamp();
        repository.saveTestCase(tc);
        return ApiResponse.success("tags", tc.getMetadata().get("tags"));
    }
    
    /**
     * Batch import test cases.
     */
    @Operation(summary = "批量导入测试用例（Excel/CSV）")
    @PostMapping("/batch")
    public Map<String, Object> batchImport(@RequestBody List<TestCaseRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            log.warn("batchImport: request list is empty");
            return ApiResponse.error("Request list is empty");
        }
        if (requests.size() > Constants.MAX_BATCH_SIZE) {
            log.warn("batchImport: batch size {} exceeds {} items", requests.size(), Constants.MAX_BATCH_SIZE);
            return ApiResponse.error("Batch size exceeds " + Constants.MAX_BATCH_SIZE + " items");
        }
        // Validate each item
        for (int i = 0; i < requests.size(); i++) {
            Map<String, Object> ve = validateInput(requests.get(i));
            if (ve != null) {
                log.warn("batchImport: item {} validation failed", i);
                return ve;
            }
        }
        List<TestCaseEntity> testCases = requests.stream()
            .map(req -> {
                TestCaseEntity tc = new TestCaseEntity(
                    req.getName(),
                    req.getInput(),
                    req.getExpected()
                );
                tc.setGroupId(req.getGroupId());
                tc.setProject(req.getProject());
                tc.setModule(req.getModule());
                tc.setFunction(req.getFunction());
                tc.setMetadata(req.getMetadata());
                return tc;
            })
            .toList();

        List<TestCaseEntity> saved = repository.saveAllTestCases(testCases);

        return Map.of(
            "success", true,
            "imported", saved.size(),
            "testCases", saved
        );
    }

    /**
     * Parse requirement text into test case candidates.
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

        // Treat empty strings as null
        if (defaultGroup != null && defaultGroup.isBlank()) defaultGroup = null;
        if (defaultProject != null && defaultProject.isBlank()) defaultProject = null;
        if (defaultModule != null && defaultModule.isBlank()) defaultModule = null;
        if (defaultFunction != null && defaultFunction.isBlank()) defaultFunction = null;

        return requirementParser.parse(text, defaultGroup, defaultProject, defaultModule, defaultFunction);
    }

    /**
     * Save parsed test cases from requirement documents.
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
            if (caseMap.containsKey("project")) {
                tc.setProject((String) caseMap.get("project"));
            }
            if (caseMap.containsKey("module")) {
                tc.setModule((String) caseMap.get("module"));
            }
            if (caseMap.containsKey("function")) {
                tc.setFunction((String) caseMap.get("function"));
            }
            if (caseMap.containsKey("description")) {
                tc.setDescription((String) caseMap.get("description"));
            }

            repository.saveTestCase(tc);
            savedCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("saved", savedCount);
        return result;
    }

    private Map<String, Object> validateInput(TestCaseRequest request) {
        if (request.getInput() == null || request.getInput().isBlank()) {
            return ApiResponse.error("输入不能为空");
        }
        if (request.getExpected() == null || request.getExpected().isBlank()) {
            return ApiResponse.error("期望输出不能为空");
        }
        if (request.getInput().length() > Constants.MAX_INPUT_LENGTH || request.getExpected().length() > Constants.MAX_INPUT_LENGTH) {
            return ApiResponse.error("输入或期望输出不能超过 " + Constants.MAX_INPUT_LENGTH + " 字符");
        }
        return null;
    }
}

class TestCaseRequest {
    private String name;
    private String input;
    private String expected;
    private String groupId;
    private String project;
    private String module;
    private String function;
    private String description;
    private Map<String, Object> metadata;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
