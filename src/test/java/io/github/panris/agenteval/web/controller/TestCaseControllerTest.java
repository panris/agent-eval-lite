package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import org.junit.jupiter.api.*;
import org.springframework.ui.Model;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for TestCaseController — no Spring context needed.
 */
class TestCaseControllerTest {

    private TestCaseController controller;
    private TestCaseRepository mockRepository;

    @BeforeEach
    void setUp() {
        mockRepository = mock(TestCaseRepository.class);
        controller = new TestCaseController(mockRepository);
    }

    // ============ POST /api/testcases — create ============

    @Test
    @DisplayName("POST /api/testcases with null input → BAD_REQUEST")
    void testCreateWithNullInput() {
        TestCaseRequest req = new TestCaseRequest();
        req.setInput(null);
        req.setExpected("output");

        Map<String, Object> resp = controller.createTestCase(req);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("输入"));
    }

    @Test
    @DisplayName("POST /api/testcases with blank input → BAD_REQUEST")
    void testCreateWithBlankInput() {
        TestCaseRequest req = new TestCaseRequest();
        req.setInput("   ");
        req.setExpected("output");

        Map<String, Object> resp = controller.createTestCase(req);

        assertFalse((Boolean) resp.get("success"));
    }

    @Test
    @DisplayName("POST /api/testcases with null expected → BAD_REQUEST")
    void testCreateWithNullExpected() {
        TestCaseRequest req = new TestCaseRequest();
        req.setInput("hello");
        req.setExpected(null);

        Map<String, Object> resp = controller.createTestCase(req);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("期望"));
    }

    @Test
    @DisplayName("POST /api/testcases with valid request → success")
    void testCreateSuccess() {
        TestCaseRequest req = new TestCaseRequest();
        req.setName("test-1");
        req.setInput("hello");
        req.setExpected("world");
        req.setProject("proj");
        req.setModule("mod");

        TestCaseEntity saved = new TestCaseEntity("test-name", "hello", "world");
        saved.setName("test-1");
        when(mockRepository.saveTestCase(any(TestCaseEntity.class))).thenReturn(saved);

        Map<String, Object> resp = controller.createTestCase(req);

        assertTrue((Boolean) resp.get("success"));
        assertNotNull(resp.get("testCase"));
        verify(mockRepository).saveTestCase(any(TestCaseEntity.class));
    }

    // ============ GET /api/testcases — list ============

    @Test
    @DisplayName("GET /api/testcases with empty repository → empty list")
    void testListEmpty() {
        when(mockRepository.findAllTestCasesPage(anyInt(), anyInt())).thenReturn(List.of());
        when(mockRepository.countAllTestCases()).thenReturn(0);

        Map<String, Object> resp = controller.listTestCases(null, 1, 20, null);

        assertTrue((Boolean) resp.get("success"));
        @SuppressWarnings("unchecked")
        List<?> cases = (List<?>) resp.get("testCases");
        assertTrue(cases.isEmpty());
        assertEquals(0, resp.get("total"));
    }

    @Test
    @DisplayName("GET /api/testcases with data → returns paginated list")
    void testListWithData() {
        TestCaseEntity tc = new TestCaseEntity("case-name", "in", "out");
        tc.setName("case-1");
        when(mockRepository.findAllTestCasesPage(1, 20)).thenReturn(List.of(tc));
        when(mockRepository.countAllTestCases()).thenReturn(1);

        Map<String, Object> resp = controller.listTestCases(null, 1, 20, null);

        assertTrue((Boolean) resp.get("success"));
        @SuppressWarnings("unchecked")
        List<?> cases = (List<?>) resp.get("testCases");
        assertEquals(1, cases.size());
        assertEquals(1, resp.get("total"));
        assertEquals(1, resp.get("page"));
    }

    @Test
    @DisplayName("GET /api/testcases with keyword → filters results")
    void testListWithKeyword() {
        TestCaseEntity tc = new TestCaseEntity("test-case", "hello world", "output");
        tc.setName("case-1");
        when(mockRepository.findAllTestCases()).thenReturn(List.of(tc));

        Map<String, Object> resp = controller.listTestCases(null, 1, 20, "hello");

        assertTrue((Boolean) resp.get("success"));
        @SuppressWarnings("unchecked")
        List<?> cases = (List<?>) resp.get("testCases");
        assertEquals(1, cases.size());
    }

    @Test
    @DisplayName("GET /api/testcases with groupId → filters by group")
    void testListByGroupId() {
        TestCaseEntity tc = new TestCaseEntity("case-name", "in", "out");
        tc.setName("case-g");
        tc.setGroupId("group-1");
        when(mockRepository.findTestCasesByGroupId("group-1")).thenReturn(List.of(tc));

        Map<String, Object> resp = controller.listTestCases("group-1", 1, 20, null);

        assertTrue((Boolean) resp.get("success"));
        @SuppressWarnings("unchecked")
        List<?> cases = (List<?>) resp.get("testCases");
        assertEquals(1, cases.size());
    }

    @Test
    @DisplayName("GET /api/testcases clamps page<1 to 1")
    void testListClampPage() {
        when(mockRepository.findAllTestCasesPage(1, 20)).thenReturn(List.of());
        when(mockRepository.countAllTestCases()).thenReturn(0);

        Map<String, Object> resp = controller.listTestCases(null, -5, 20, null);

        assertEquals(1, resp.get("page"));
    }

    @Test
    @DisplayName("GET /api/testcases clamps size>100 to 100")
    void testListClampSize() {
        when(mockRepository.findAllTestCasesPage(1, 100)).thenReturn(List.of());
        when(mockRepository.countAllTestCases()).thenReturn(0);

        controller.listTestCases(null, 1, 500, null);

        verify(mockRepository).findAllTestCasesPage(1, 100);
    }

    // ============ GET /api/testcases/{id} ============

    @Test
    @DisplayName("GET /api/testcases/{id} with unknown id → NOT_FOUND")
    void testGetNotFound() {
        when(mockRepository.findTestCaseById("ghost")).thenReturn(Optional.empty());

        Map<String, Object> resp = controller.getTestCase("ghost");

        assertFalse((Boolean) resp.get("success"));
        assertEquals("测试用例不存在", resp.get("error"));
    }

    @Test
    @DisplayName("GET /api/testcases/{id} with valid id → success")
    void testGetFound() {
        TestCaseEntity tc = new TestCaseEntity("case-name", "in", "out");
        when(mockRepository.findTestCaseById("tc-1")).thenReturn(Optional.of(tc));

        Map<String, Object> resp = controller.getTestCase("tc-1");

        assertTrue((Boolean) resp.get("success"));
        assertNotNull(resp.get("testCase"));
    }

    // ============ PUT /api/testcases/{id} ============

    @Test
    @DisplayName("PUT /api/testcases/{id} with unknown id → NOT_FOUND")
    void testUpdateNotFound() {
        TestCaseRequest req = new TestCaseRequest();
        req.setInput("hello");
        req.setExpected("world");

        when(mockRepository.findTestCaseById("ghost")).thenReturn(Optional.empty());

        Map<String, Object> resp = controller.updateTestCase("ghost", req);

        assertFalse((Boolean) resp.get("success"));
        assertEquals("测试用例不存在", resp.get("error"));
    }

    @Test
    @DisplayName("PUT /api/testcases/{id} with null input → BAD_REQUEST")
    void testUpdateNullInput() {
        TestCaseRequest req = new TestCaseRequest();
        req.setInput(null);
        req.setExpected("world");

        Map<String, Object> resp = controller.updateTestCase("tc-1", req);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("输入"));
    }

    @Test
    @DisplayName("PUT /api/testcases/{id} with valid data → success")
    void testUpdateSuccess() {
        TestCaseRequest req = new TestCaseRequest();
        req.setInput("hello");
        req.setExpected("world");
        req.setName("updated");

        TestCaseEntity existing = new TestCaseEntity("case-name", "in", "out");
        TestCaseEntity saved = new TestCaseEntity("test-name", "hello", "world");
        saved.setName("updated");

        when(mockRepository.findTestCaseById("tc-1")).thenReturn(Optional.of(existing));
        when(mockRepository.saveTestCase(any(TestCaseEntity.class))).thenReturn(saved);

        Map<String, Object> resp = controller.updateTestCase("tc-1", req);

        assertTrue((Boolean) resp.get("success"));
        assertNotNull(resp.get("testCase"));
    }

    // ============ DELETE /api/testcases/{id} ============

    @Test
    @DisplayName("DELETE /api/testcases/{id} with unknown id → NOT_FOUND")
    void testDeleteNotFound() {
        when(mockRepository.findTestCaseById("ghost")).thenReturn(Optional.empty());

        Map<String, Object> resp = controller.deleteTestCase("ghost");

        assertFalse((Boolean) resp.get("success"));
        assertEquals("测试用例不存在", resp.get("error"));
    }

    @Test
    @DisplayName("DELETE /api/testcases/{id} with valid id → success")
    void testDeleteSuccess() {
        TestCaseEntity tc = new TestCaseEntity("case-name", "in", "out");
        when(mockRepository.findTestCaseById("tc-1")).thenReturn(Optional.of(tc));

        Map<String, Object> resp = controller.deleteTestCase("tc-1");

        assertTrue((Boolean) resp.get("success"));
        verify(mockRepository).deleteTestCase("tc-1");
    }

    // ============ GET /api/testcases/dimensions ============

    @Test
    @DisplayName("GET /api/testcases/dimensions → returns projects/modules/functions")
    void testGetDimensions() {
        when(mockRepository.findDistinctProjects()).thenReturn(List.of("proj-a", "proj-b"));
        when(mockRepository.findDistinctModules()).thenReturn(List.of("mod-1"));
        when(mockRepository.findDistinctFunctions()).thenReturn(List.of("fn-x"));

        Map<String, Object> resp = controller.getDimensions();

        assertTrue((Boolean) resp.get("success"));
        @SuppressWarnings("unchecked")
        List<String> projects = (List<String>) resp.get("projects");
        assertEquals(2, projects.size());
    }

    // ============ PUT /api/testcases/{id}/tags ============

    @Test
    @DisplayName("PUT /api/testcases/{id}/tags with null body → BAD_REQUEST")
    void testUpdateTagsNullBody() {
        Map<String, Object> resp = controller.updateTags("tc-1", null);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("tags"));
    }

    @Test
    @DisplayName("PUT /api/testcases/{id}/tags with missing tags key → BAD_REQUEST")
    void testUpdateTagsMissingKey() {
        Map<String, Object> body = Map.of("other", "value");

        Map<String, Object> resp = controller.updateTags("tc-1", body);

        assertFalse((Boolean) resp.get("success"));
    }

    @Test
    @DisplayName("PUT /api/testcases/{id}/tags with non-list tags → BAD_REQUEST")
    void testUpdateTagsNotAList() {
        Map<String, Object> body = Map.of("tags", "not-a-list");

        Map<String, Object> resp = controller.updateTags("tc-1", body);

        assertFalse((Boolean) resp.get("success"));
    }

    @Test
    @DisplayName("PUT /api/testcases/{id}/tags with unknown id → NOT_FOUND")
    void testUpdateTagsNotFound() {
        Map<String, Object> body = Map.of("tags", List.of("tag1", "tag2"));
        when(mockRepository.findTestCaseById("ghost")).thenReturn(Optional.empty());

        Map<String, Object> resp = controller.updateTags("ghost", body);

        assertFalse((Boolean) resp.get("success"));
        assertEquals("测试用例不存在", resp.get("error"));
    }

    @Test
    @DisplayName("PUT /api/testcases/{id}/tags with valid data → success")
    void testUpdateTagsSuccess() {
        Map<String, Object> body = Map.of("tags", List.of("api", "v2"));
        TestCaseEntity tc = new TestCaseEntity("case-name", "in", "out");
        tc.setMetadata(new LinkedHashMap<>());
        TestCaseEntity saved = new TestCaseEntity("case-name", "in", "out");
        saved.setMetadata(new LinkedHashMap<>(Map.of("tags", List.of("api", "v2"))));

        when(mockRepository.findTestCaseById("tc-1")).thenReturn(Optional.of(tc));
        when(mockRepository.saveTestCase(any(TestCaseEntity.class))).thenReturn(saved);

        Map<String, Object> resp = controller.updateTags("tc-1", body);

        assertTrue((Boolean) resp.get("success"));
    }

    // ============ POST /api/testcases/batch — batch import ============

    @Test
    @DisplayName("POST /api/testcases/batch with null list → BAD_REQUEST")
    void testBatchImportNull() {
        Map<String, Object> resp = controller.batchImport(null);

        assertFalse((Boolean) resp.get("success"));
    }

    @Test
    @DisplayName("POST /api/testcases/batch with empty list → BAD_REQUEST")
    void testBatchImportEmpty() {
        Map<String, Object> resp = controller.batchImport(List.of());

        assertFalse((Boolean) resp.get("success"));
    }

    @Test
    @DisplayName("POST /api/testcases/batch with >100 items → BAD_REQUEST")
    void testBatchImportTooMany() {
        List<TestCaseRequest> requests = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            TestCaseRequest req = new TestCaseRequest();
            req.setName("c" + i);
            req.setInput("in" + i);
            req.setExpected("out" + i);
            requests.add(req);
        }

        Map<String, Object> resp = controller.batchImport(requests);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("100"));
    }

    @Test
    @DisplayName("POST /api/testcases/batch with validation error on item → BAD_REQUEST")
    void testBatchImportValidationError() {
        List<TestCaseRequest> requests = List.of(
            createValidRequest("c1"),
            createInvalidRequestNullInput("c2")
        );

        Map<String, Object> resp = controller.batchImport(requests);

        assertFalse((Boolean) resp.get("success"));
    }

    @Test
    @DisplayName("POST /api/testcases/batch with valid items → success")
    void testBatchImportSuccess() {
        List<TestCaseRequest> requests = List.of(
            createValidRequest("c1"),
            createValidRequest("c2")
        );
        when(mockRepository.saveAllTestCases(anyList())).thenReturn(List.of(
            new TestCaseEntity("case-name", "in", "out"),
            new TestCaseEntity("case-name", "in", "out")
        ));

        Map<String, Object> resp = controller.batchImport(requests);

        assertTrue((Boolean) resp.get("success"));
        assertEquals(2, resp.get("imported"));
        verify(mockRepository).saveAllTestCases(anyList());
    }

    // ---------- helpers ----------

    private TestCaseRequest createValidRequest(String name) {
        TestCaseRequest req = new TestCaseRequest();
        req.setName(name);
        req.setInput("input");
        req.setExpected("expected");
        return req;
    }

    private TestCaseRequest createInvalidRequestNullInput(String name) {
        TestCaseRequest req = new TestCaseRequest();
        req.setName(name);
        req.setInput(null);
        req.setExpected("expected");
        return req;
    }
}
