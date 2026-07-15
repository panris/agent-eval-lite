package io.github.panris.agenteval.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RequirementParser — rule-based requirement document parsing.
 */
class RequirementParserTest {

    private RequirementParser parser;

    @BeforeEach
    void setUp() {
        parser = new RequirementParser();
    }

    // ============ Format A: Section-based (### headings) ============

    @Test
    @DisplayName("Format A: Parse section-based requirement with ### headings")
    void testFormatA_SectionBased() {
        String text = """
            ## 用户登录
            ### 登录成功
            输入：admin / 123456
            期望：返回 token，状态码 200
            说明：正确凭据验证

            ### 登录失败
            输入：admin / wrong
            期望：返回错误提示 "密码错误"

            ### 验证码错误
            输入：admin / correct / wrong_captcha
            期望：返回错误提示 "验证码错误"
            """;

        List<RequirementParser.ParsedTestCase> cases = parser.parseInternal(text);

        assertThat(cases).hasSize(3);

        assertThat(cases.get(0).name()).isEqualTo("登录成功");
        assertThat(cases.get(0).input()).isEqualTo("admin / 123456");
        assertThat(cases.get(0).expected()).contains("返回 token");
        assertThat(cases.get(0).description()).isEqualTo("正确凭据验证");
        assertThat(cases.get(0).lineNumber()).isEqualTo(1);

        assertThat(cases.get(1).name()).isEqualTo("登录失败");
        assertThat(cases.get(1).input()).isEqualTo("admin / wrong");
        assertThat(cases.get(1).expected()).contains("密码错误");
        assertThat(cases.get(1).description()).isNull();

        assertThat(cases.get(2).name()).isEqualTo("验证码错误");
        assertThat(cases.get(2).input()).isEqualTo("admin / correct / wrong_captcha");
        assertThat(cases.get(2).expected()).contains("验证码错误");
    }

    @Test
    @DisplayName("Format A: Parse with multi-line names and English field labels")
    void testFormatA_EnglishLabelsAndMultiLineName() {
        String text = """
            ## User Auth
            ### Successful Login with Valid Credentials
            A test for valid username and password
            Input: admin@example.com
            Expected: HTTP 200, token returned
            Desc: Happy path test

            ### Login Failure
            Input: invalid@test.com / wrongpass
            Expected: HTTP 401, error message
            """;

        List<RequirementParser.ParsedTestCase> cases = parser.parseInternal(text);

        assertThat(cases).hasSize(2);

        assertThat(cases.get(0).name()).isEqualTo("Successful Login with Valid Credentials - A test for valid username and password");
        assertThat(cases.get(0).input()).isEqualTo("admin@example.com");
        assertThat(cases.get(0).expected()).isEqualTo("HTTP 200, token returned");
        assertThat(cases.get(0).description()).isEqualTo("Happy path test");

        assertThat(cases.get(1).name()).isEqualTo("Login Failure");
        assertThat(cases.get(1).input()).isEqualTo("invalid@test.com / wrongpass");
        assertThat(cases.get(1).expected()).isEqualTo("HTTP 401, error message");
    }

    @Test
    @DisplayName("Format A: Section with no field markers still gets parsed")
    void testFormatA_NoFieldMarkers() {
        String text = """
            ## Tests
            ### Just a description block
            This is a purely descriptive section without
            explicit field markers like Input or Expected.
            """;

        List<RequirementParser.ParsedTestCase> cases = parser.parseInternal(text);

        // Even without field markers, it should produce something
        // The name is the first line after ###, and it may not extract input/expected
        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).name()).isEqualTo("Just a description block");
        // Input/expected may be null if no markers found
    }

    // ============ Format B: Numbered list ============

    @Test
    @DisplayName("Format B: Parse numbered list with Chinese markers")
    void testFormatB_NumberedListChinese() {
        String text = """
            1. 登录成功 - 输入 admin/123456，期望返回 token
            2. 登录失败 - 输入 bad/credentials，期望返回错误提示
            3. 验证码错误 - 输入 correct/wrong_captcha，期望提示验证码错误
            """;

        List<RequirementParser.ParsedTestCase> cases = parser.parseInternal(text);

        assertThat(cases).hasSize(3);

        assertThat(cases.get(0).name()).isEqualTo("登录成功");
        assertThat(cases.get(0).input()).contains("admin/123456");
        assertThat(cases.get(0).expected()).contains("返回 token");

        assertThat(cases.get(1).name()).isEqualTo("登录失败");
        assertThat(cases.get(1).input()).contains("bad/credentials");
        assertThat(cases.get(1).expected()).contains("返回错误提示");

        assertThat(cases.get(2).name()).isEqualTo("验证码错误");
        assertThat(cases.get(2).input()).contains("correct/wrong_captcha");
        assertThat(cases.get(2).expected()).contains("验证码错误");
    }

    @Test
    @DisplayName("Format B: Parse numbered list with English markers and inline format")
    void testFormatB_NumberedListEnglish() {
        String text = """
            1. User Login - Input: admin/123456, Expected: JWT token returned
            2. Failed Attempt - Input: bad/user, Expected: 401 error
            """;

        List<RequirementParser.ParsedTestCase> cases = parser.parseInternal(text);

        assertThat(cases).hasSize(2);
        assertThat(cases.get(0).name()).isEqualTo("User Login");
        assertThat(cases.get(0).input()).contains("admin/123456");
        assertThat(cases.get(0).expected()).contains("JWT token returned");

        assertThat(cases.get(1).name()).isEqualTo("Failed Attempt");
        assertThat(cases.get(1).input()).contains("bad/user");
        assertThat(cases.get(1).expected()).contains("401 error");
    }

    // ============ Empty / Error cases ============

    @Test
    @DisplayName("Empty input returns error")
    void testEmptyInput() {
        Map<String, Object> result = parser.parse("", null, null, null, null);
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).isNotNull();

        result = parser.parse("   ", null, null, null, null);
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).isNotNull();

        result = parser.parse(null, null, null, null, null);
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).isNotNull();
    }

    @Test
    @DisplayName("Malformed input returns error")
    void testMalformedInput() {
        // Random text with no recognizable pattern
        String gibberish = "这是一段随机文字，没有任何测试用例格式。";
        Map<String, Object> result = parser.parse(gibberish, null, null, null, null);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).isNotNull();
    }

    // ============ Edge cases ============

    @Test
    @DisplayName("Very long input is truncated to 50000 chars")
    void testVeryLongInput() {
        // Create a long text with recognizable patterns beyond 50000 chars
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("### Test Case ").append(i + 1).append("\n");
            sb.append("输入：input_").append(i).append("\n");
            sb.append("期望：expected_").append(i).append("\n\n");
        }
        String longText = sb.toString();

        // Build a text long enough to be truncated (50000+)
        // With about 40 chars per case, we need ~1250 cases
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 1500; i++) {
            sb2.append("### Test Case ").append(i + 1).append("\n");
            sb2.append("输入：input_").append(i).append("\n");
            sb2.append("期望：expected_").append(i).append("\n\n");
        }
        String longText2 = sb2.toString();
        assertThat(longText2.length()).isGreaterThan(50000);

        Map<String, Object> result = parser.parse(longText2, null, null, null, null);
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("count")).isNotNull();
        int count = (int) result.get("count");
        // Should have parsed some cases despite potential truncation
        assertThat(count).isPositive();
        // With ~47 chars per case at 50000 char limit, we expect ~1063 cases
        // The count is less than the original 1500, showing truncation worked
        assertThat(count).isLessThan(1400);
        assertThat(count).isGreaterThan(500);
    }

    @Test
    @DisplayName("parse() with defaults attaches group/dimension metadata")
    void testParseWithDefaults() {
        String text = """
            ### Test One
            输入：value1
            期望：result1
            """;

        Map<String, Object> result = parser.parse(text, "my-group", "my-project", "my-module", "my-function");

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("count")).isEqualTo(1);

        List<Map<String, Object>> cases = (List<Map<String, Object>>) result.get("cases");
        assertThat(cases.get(0)).containsEntry("groupId", "my-group");
        assertThat(cases.get(0)).containsEntry("project", "my-project");
        assertThat(cases.get(0)).containsEntry("module", "my-module");
        assertThat(cases.get(0)).containsEntry("function", "my-function");
    }

    @Test
    @DisplayName("Mixed formats still parse correctly (Format A takes priority)")
    void testMixedFormats() {
        // Text that could match both formats — Format A (###) should take priority
        String text = """
            ### Login Test
            输入：admin
            期望：success

            1. some other format
            2. that should be ignored
            """;

        List<RequirementParser.ParsedTestCase> cases = parser.parseInternal(text);
        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).name()).isEqualTo("Login Test");
        assertThat(cases.get(0).input()).isEqualTo("admin");
        assertThat(cases.get(0).expected()).isEqualTo("success");
    }

    @Test
    @DisplayName("Format A with ## section heading but no ### sub-sections")
    void testFormatA_WithSectionHeadingOnly() {
        // Only ## headings, no ### sub-sections — should fall through to Format B or fallback.
        // Use triple newlines (\n\n\n) so the fallback can split on paragraphs.
        String text = "## User Login\nInput: admin\nExpected: success\n\n\n## Logout\nInput: logout_request\nExpected: session_ended";

        List<RequirementParser.ParsedTestCase> cases = parser.parseInternal(text);
        // No ### found, so Format A empty. No numbered list, so Format B empty.
        // Fallback splits on \n\n\n+ which should match here
        assertThat(cases).isNotEmpty();
    }

    @Test
    @DisplayName("Fallback: double blank line paragraphs")
    void testFallbackParagraphs() {
        String text = """
            用户登录功能测试
            输入有效的用户名和密码。
            期望返回登录成功页面。

            用户注册功能测试
            输入新用户信息。
            期望注册成功并跳转到首页。
            """;

        // Only 1 double-blank-line gap (between paragraphs) — fallback needs \n\n\n+
        // Let's use 3+ newlines
        String text2 = "用户登录功能测试\n输入有效的用户名和密码。\n期望返回登录成功页面。\n\n\n用户注册功能测试\n输入新用户信息。\n期望注册成功并跳转到首页。";

        List<RequirementParser.ParsedTestCase> cases = parser.parseInternal(text2);
        assertThat(cases).hasSize(2);
        assertThat(cases.get(0).name()).isEqualTo("用户登录功能测试");
        assertThat(cases.get(0).input()).contains("输入有效的用户名和密码");
        assertThat(cases.get(0).expected()).contains("返回登录成功页面");

        assertThat(cases.get(1).name()).isEqualTo("用户注册功能测试");
        assertThat(cases.get(1).input()).contains("输入新用户信息");
        assertThat(cases.get(1).expected()).contains("注册成功");
    }

    @Test
    @DisplayName("parse() returns correct Map structure")
    void testParseReturnsCorrectStructure() {
        String text = """
            ### Login
            输入：user/pass
            期望：ok
            """;

        Map<String, Object> result = parser.parse(text, null, null, null, null);

        assertThat(result).containsKey("success");
        assertThat(result).containsKey("cases");
        assertThat(result).containsKey("count");

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("count")).isEqualTo(1);

        List<Map<String, Object>> cases = (List<Map<String, Object>>) result.get("cases");
        assertThat(cases).hasSize(1);
        assertThat(cases.get(0)).containsKey("name");
        assertThat(cases.get(0)).containsKey("input");
        assertThat(cases.get(0)).containsKey("expected");
        assertThat(cases.get(0)).containsKey("description");
    }
}
