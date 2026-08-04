package io.github.panris.agenteval.service;

import io.github.panris.agenteval.TestCase;
import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.web.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评测用例解析服务：三种来源（DTO / ID / 维度）的统一入口，
 * 消除 EvalController 中 resolveFromDtos/resolveFromCaseIds/resolveFromDimensions
 * 三个重复 private 方法。
 */
@Service
public class EvalCaseService {

    private static final Logger log = LoggerFactory.getLogger(EvalCaseService.class);

    private final TestCaseRepository testCaseRepository;

    public EvalCaseService(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    /** 测试用例总数（用于首页控制台统计）。 */
    public long countTestCases() {
        return testCaseRepository.count();
    }

    /** Result holder: either resolved TestCase list or an error message. */
    public record CaseResolution(List<TestCase> testCases, String errorMessage) {
        public CaseResolution(List<TestCase> testCases) { this(testCases, null); }
        public CaseResolution(String errorMessage) { this(null, errorMessage); }
        public boolean hasError() { return errorMessage != null; }
        public boolean isError() { return errorMessage != null; }
        public List<TestCase> getOrThrow() {
            if (isError()) throw new IllegalStateException(errorMessage);
            return testCases;
        }
    }

    /** 从内联 DTO 列表解析用例（含长度/数量校验）。 */
    public CaseResolution resolveFromDtos(List<? extends TestCaseDtoLike> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return new CaseResolution("请提供测试用例（cases / caseIds / project+module+function 三选一）");
        }
        if (dtos.size() > Constants.MAX_CASES_PER_EVAL) {
            return new CaseResolution("测试用例数量不能超过 " + Constants.MAX_CASES_PER_EVAL + " 个");
        }
        for (int i = 0; i < dtos.size(); i++) {
            TestCaseDtoLike dto = dtos.get(i);
            if (dto.getInput() == null || dto.getInput().trim().isEmpty()) {
                return new CaseResolution("第 " + (i + 1) + " 个测试用例的输入不能为空");
            }
            if (dto.getInput().length() > Constants.MAX_INPUT_LENGTH) {
                return new CaseResolution("第 " + (i + 1) + " 个测试用例的输入过长（最大 " + Constants.MAX_INPUT_LENGTH + " 字符）");
            }
            if (dto.getExpected() != null && dto.getExpected().length() > Constants.MAX_INPUT_LENGTH) {
                return new CaseResolution("第 " + (i + 1) + " 个测试用例的期望输出过长（最大 " + Constants.MAX_INPUT_LENGTH + " 字符）");
            }
        }
        List<TestCase> cases = dtos.stream()
                .map(dto -> new TestCase(dto.getInput(), dto.getExpected()))
                .toList();
        return new CaseResolution(cases);
    }

    /** 从数据库按 ID 列表解析用例。 */
    public CaseResolution resolveFromCaseIds(List<String> caseIds) {
        log.info("Resolving {} case IDs", caseIds != null ? caseIds.size() : 0);
        if (caseIds == null || caseIds.isEmpty()) {
            return new CaseResolution("未找到有效的测试用例");
        }
        if (caseIds.size() > Constants.MAX_CASES_PER_EVAL) {
            return new CaseResolution("测试用例数量不能超过 " + Constants.MAX_CASES_PER_EVAL + " 个");
        }
        List<TestCase> cases = caseIds.stream()
                .map(id -> testCaseRepository.findTestCaseById(id))
                .filter(java.util.Optional::isPresent)
                .map(opt -> {
                    TestCaseEntity e = opt.get();
                    return new TestCase(e.getId(), e.getInput(), e.getExpected(), null, null,
                            e.getProject(), e.getModule(), e.getFunction());
                })
                .toList();
        log.info("Resolved {} test cases from {} IDs", cases.size(), caseIds.size());
        if (cases.isEmpty()) {
            return new CaseResolution("未找到有效的测试用例");
        }
        return new CaseResolution(cases);
    }

    /** 从 project/module/function 维度解析用例。 */
    public CaseResolution resolveFromDimensions(String project, String module, String function) {
        List<TestCaseEntity> byDims = testCaseRepository.findTestCasesByDimensions(project, module, function);
        if (byDims.isEmpty()) {
            return new CaseResolution("没有符合所选维度的测试用例");
        }
        if (byDims.size() > Constants.MAX_CASES_PER_EVAL) {
            return new CaseResolution("测试用例数量不能超过 " + Constants.MAX_CASES_PER_EVAL + " 个（当前 " + byDims.size() + "）");
        }
        List<TestCase> cases = byDims.stream()
                .map(e -> new TestCase(e.getId(), e.getInput(), e.getExpected(), null, null,
                        e.getProject(), e.getModule(), e.getFunction()))
                .toList();
        return new CaseResolution(cases);
    }

    /** DTO 接口：让 resolveFromDtos 与具体 DTO 类型解耦。 */
    public interface TestCaseDtoLike {
        String getInput();
        String getExpected();
    }
}
