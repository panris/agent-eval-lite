package io.github.panris.agenteval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based service for parsing requirement documents into test case candidates.
 * Supports two common formats:
 * <p>
 * Format A — Section-based (Markdown): splits on "###" headings
 * Format B — Inline list: splits on numbered lines (e.g. "1. ", "2. ")
 * Fallback — Double blank line paragraphs
 */
@Service
public class RequirementParser {

    private static final Logger log = LoggerFactory.getLogger(RequirementParser.class);

    private static final int MAX_TEXT_LENGTH = 50000;

    /**
     * Parse requirement text and return structured test case candidates.
     *
     * @param text           the raw requirement document (Markdown or plain text)
     * @param defaultGroup   default group ID for all parsed cases
     * @param defaultProject default project for all parsed cases
     * @param defaultModule  default module for all parsed cases
     * @param defaultFunction default function for all parsed cases
     * @return Map with success, cases (list of maps), and count
     */
    public Map<String, Object> parse(String text, String defaultGroup,
                                      String defaultProject, String defaultModule,
                                      String defaultFunction) {
        if (text == null || text.isBlank()) {
            return Map.of("success", false, "error", "需求文档内容不能为空");
        }

        // Truncate if too long
        String originalText = text;
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }

        List<ParsedTestCase> parsedCases = parseInternal(text);

        if (parsedCases.isEmpty()) {
            return Map.of(
                "success", false,
                "error", "无法从文档中识别出测试用例格式，请检查文档格式"
            );
        }

        List<Map<String, Object>> caseMaps = new ArrayList<>();
        for (ParsedTestCase pc : parsedCases) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", pc.name() != null ? pc.name() : "");
            m.put("input", pc.input() != null ? pc.input() : "");
            m.put("expected", pc.expected() != null ? pc.expected() : "");
            m.put("description", pc.description() != null ? pc.description() : "");
            if (defaultGroup != null) m.put("groupId", defaultGroup);
            if (defaultProject != null) m.put("project", defaultProject);
            if (defaultModule != null) m.put("module", defaultModule);
            if (defaultFunction != null) m.put("function", defaultFunction);
            caseMaps.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("cases", caseMaps);
        result.put("count", caseMaps.size());
        return result;
    }

    /**
     * Internal parsing logic. Tries Format A first, then Format B, then fallback.
     */
    List<ParsedTestCase> parseInternal(String text) {
        // Try Format A: section-based (### headings)
        List<ParsedTestCase> cases = parseFormatA(text);
        if (!cases.isEmpty()) {
            log.debug("Parsed {} test cases using Format A (section-based)", cases.size());
            return cases;
        }

        // Try Format B: numbered list
        cases = parseFormatB(text);
        if (!cases.isEmpty()) {
            log.debug("Parsed {} test cases using Format B (numbered list)", cases.size());
            return cases;
        }

        // Fallback: double blank line paragraphs
        cases = parseFallback(text);
        if (!cases.isEmpty()) {
            log.debug("Parsed {} test cases using fallback (paragraphs)", cases.size());
        }
        return cases;
    }

    // Patterns for field matching (used across formats)
    private static final Pattern INPUT_PATTERN = Pattern.compile(
        "(?:输入|Input)[：:]\\s*(.+)", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern EXPECTED_PATTERN = Pattern.compile(
        "(?:期望|期望输出|Expected)[：:]\\s*(.+)", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern DESC_PATTERN = Pattern.compile(
        "(?:说明|Desc)[：:]\\s*(.+)", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Format A: Section-based (Markdown).
     * Splits by "###" headings. Each section = one test case candidate.
     */
    List<ParsedTestCase> parseFormatA(String text) {
        List<ParsedTestCase> results = new ArrayList<>();

        // Split by ### headings
        String[] sections = text.split("(?m)^###\\s+");

        // No ### found — skip Format A entirely
        if (sections.length <= 1) {
            return results;
        }

        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].trim();
            if (section.isEmpty()) continue;

            // Skip preamble (everything before the first ###)
            if (i == 0 && !text.trim().startsWith("###")) {
                continue;
            }

            ParsedTestCase parsed = parseSectionBlock(section, results.size());
            if (parsed != null) {
                results.add(parsed);
            }
        }

        return results;
    }

    /**
     * Parse a single section block (after ###) into a test case candidate.
     */
    private ParsedTestCase parseSectionBlock(String block, int index) {
        if (block.isBlank()) return null;

        // Split into lines
        String[] lines = block.split("\n");
        List<String> nonEmptyLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                nonEmptyLines.add(trimmed);
            }
        }

        if (nonEmptyLines.isEmpty()) return null;

        String name = null;
        String input = null;
        String expected = null;
        String description = null;

        // Find the first line that contains a field marker (input/expected/desc)
        int firstFieldLine = -1;
        for (int i = 0; i < nonEmptyLines.size(); i++) {
            String line = nonEmptyLines.get(i);
            if (INPUT_PATTERN.matcher(line).find() ||
                EXPECTED_PATTERN.matcher(line).find() ||
                DESC_PATTERN.matcher(line).find()) {
                firstFieldLine = i;
                break;
            }
        }

        // Name comes from lines before the first field marker
        if (firstFieldLine > 0) {
            StringBuilder nameBuilder = new StringBuilder();
            for (int i = 0; i < firstFieldLine; i++) {
                if (i > 0) nameBuilder.append(" - ");
                nameBuilder.append(nonEmptyLines.get(i));
            }
            name = nameBuilder.toString();
        } else if (firstFieldLine == -1) {
            // No field markers found — use first line as name
            name = nonEmptyLines.get(0);
        } else {
            // firstFieldLine == 0 — no name lines
            name = "";
        }

        // Parse fields using find() (not matches())
        for (String line : nonEmptyLines) {
            Matcher im = INPUT_PATTERN.matcher(line);
            if (im.find()) {
                String val = im.group(1).trim();
                if (!val.isEmpty()) input = val;
                continue;
            }

            Matcher em = EXPECTED_PATTERN.matcher(line);
            if (em.find()) {
                String val = em.group(1).trim();
                if (!val.isEmpty()) expected = val;
                continue;
            }

            Matcher dm = DESC_PATTERN.matcher(line);
            if (dm.find()) {
                String val = dm.group(1).trim();
                if (!val.isEmpty()) description = val;
            }
        }

        // Fallback: try to find "输入" and "期望" in the same line (no colon)
        if (input == null && expected == null) {
            for (String line : nonEmptyLines) {
                // Try to find "输入" and "期望" in the same line
                boolean hasInput = line.contains("输入") || line.toLowerCase().contains("input");
                boolean hasExpected = line.contains("期望") || line.toLowerCase().contains("expected");
                if (hasInput || hasExpected) {
                    String[] parts = line.split("[，,；;]");
                    for (String part : parts) {
                        String p = part.trim();
                        String extracted;
                        extracted = tryExtractAfter(p, "输入", "Input");
                        if (extracted != null && input == null) {
                            input = extracted;
                            continue;
                        }
                        extracted = tryExtractAfter(p, "期望", "期望输出", "Expected");
                        if (extracted != null && expected == null) {
                            expected = extracted;
                            continue;
                        }
                    }
                }
            }
        }

        // If still no name, use a default
        if (name == null || name.isBlank()) {
            name = "用例 " + (index + 1);
        }

        return new ParsedTestCase(name, input, expected, description, index + 1);
    }

    /**
     * Try to extract text after the first occurrence of any marker in str.
     * Returns null if no marker found.
     */
    private String tryExtractAfter(String str, String... markers) {
        int idx = findFirstMarker(str, markers);
        if (idx < 0) return null;
        String after = str.substring(idx).replaceFirst("^(?:" + String.join("|", markers) + ")[：:]?\\s*", "");
        return after.trim();
    }

    /**
     * Format B: Numbered list.
     * Splits by lines starting with a number followed by a dot, e.g. "1. ", "2. "
     */
    List<ParsedTestCase> parseFormatB(String text) {
        List<ParsedTestCase> results = new ArrayList<>();

        // Match numbered lines: "1. content", "2. content", etc.
        Pattern numberedLinePattern = Pattern.compile("^(\\d+)\\.\\s*(.*)$", Pattern.MULTILINE);
        Matcher matcher = numberedLinePattern.matcher(text);

        List<String> lineContents = new ArrayList<>();
        List<Integer> lineNumbers = new ArrayList<>();

        while (matcher.find()) {
            String content = matcher.group(2).trim();
            if (!content.isEmpty()) {
                lineContents.add(content);
                lineNumbers.add(Integer.parseInt(matcher.group(1)));
            }
        }

        // Need at least 2 items to be a list format
        if (lineContents.size() < 2) {
            return results;
        }

        for (int i = 0; i < lineContents.size(); i++) {
            ParsedTestCase parsed = parseInlineLine(lineContents.get(i), i + 1);
            if (parsed != null) {
                results.add(parsed);
            }
        }

        return results;
    }

    /**
     * Parse an inline numbered line into a test case candidate.
     * Handles multiple formats:
     * - "名称 - 输入 xxx，期望 yyy"
     * - "名称 - Input: xxx, Expected: yyy"
     * - "名称 - xxx -> yyy"
     */
    private ParsedTestCase parseInlineLine(String line, int index) {
        String name = null;
        String input = null;
        String expected = null;
        String description = null;

        // Try to extract input and expected via field markers (find, not matches)
        Matcher im = INPUT_PATTERN.matcher(line);
        Matcher em = EXPECTED_PATTERN.matcher(line);

        String inputSegment = null;
        String expectedSegment = null;

        if (im.find()) {
            inputSegment = im.group(1).trim();
        }
        if (em.find()) {
            expectedSegment = em.group(1).trim();
        }

        if (inputSegment != null || expectedSegment != null) {
            // The part before "Input"/"输入" is the name
            int inputIdx = findFirstMarker(line, "输入", "Input");
            if (inputIdx > 0) {
                name = line.substring(0, inputIdx).trim();
                // Clean up leading/trailing separators
                name = name.replaceAll("^[-—\\s]+", "").trim();
                name = name.replaceAll("[-—\\s]+$", "").trim();
            }

            input = inputSegment;
            expected = expectedSegment;

            // Check for description
            Matcher dm = DESC_PATTERN.matcher(line);
            if (dm.find()) {
                description = dm.group(1).trim();
            }
        } else {
            // No field markers — try Chinese inline: "名称 - 输入 xxx，期望 yyy"
            // where "输入" and "期望" appear without colon separator
            boolean hasInput = line.contains("输入") || line.contains("Input");
            boolean hasExpected = line.contains("期望") || line.contains("Expected");

            if (hasInput || hasExpected) {
                // Try to split by "，" or "；" or ","
                String[] parts = line.split("[，,；;]");
                for (String part : parts) {
                    String p = part.trim();
                    String extracted;
                    extracted = tryExtractAfter(p, "输入", "Input");
                    if (extracted != null && input == null) {
                        input = extracted;
                        continue;
                    }
                    extracted = tryExtractAfter(p, "期望", "期望输出", "Expected");
                    if (extracted != null && expected == null) {
                        expected = extracted;
                        continue;
                    }
                }

                // Name is the part before "输入"
                int inputIdx = findFirstMarker(line, "输入", "Input");
                if (inputIdx > 0) {
                    name = line.substring(0, inputIdx).trim();
                    name = name.replaceAll("^[-—\\s]+", "").trim();
                    name = name.replaceAll("[-—\\s]+$", "").trim();
                }
            } else {
                // Fallback: try dash-separated: "name - input - expected"
                String[] parts = line.split("[-—]");
                List<String> trimmedParts = new ArrayList<>();
                for (String p : parts) {
                    String t = p.trim();
                    if (!t.isEmpty()) trimmedParts.add(t);
                }

                if (trimmedParts.size() >= 3) {
                    name = trimmedParts.get(0);
                    input = trimmedParts.get(1);
                    expected = trimmedParts.get(2);
                    if (trimmedParts.size() >= 4) {
                        description = trimmedParts.get(3);
                    }
                }
            }
        }

        if (name == null || name.isBlank()) {
            name = "用例 " + index;
        }

        return new ParsedTestCase(name, input, expected, description, index);
    }

    /**
     * Find the first occurrence of any of the given markers in the string.
     * Returns the index, or -1 if none found.
     */
    private int findFirstMarker(String str, String... markers) {
        int minIdx = Integer.MAX_VALUE;
        for (String marker : markers) {
            int idx = str.indexOf(marker);
            if (idx >= 0 && idx < minIdx) {
                minIdx = idx;
            }
        }
        return minIdx == Integer.MAX_VALUE ? -1 : minIdx;
    }

    /**
     * Fallback: split by double blank lines (\n\n\n+), each paragraph = one test case.
     */
    List<ParsedTestCase> parseFallback(String text) {
        List<ParsedTestCase> results = new ArrayList<>();

        // Split by 3+ newlines (double blank lines or more)
        String[] paragraphs = text.split("\\n{3,}");
        if (paragraphs.length < 2) return results;

        for (int i = 0; i < paragraphs.length; i++) {
            String para = paragraphs[i].trim();
            if (para.isEmpty()) continue;

            ParsedTestCase parsed = parseParagraph(para, i + 1);
            if (parsed != null) {
                results.add(parsed);
            }
        }

        return results;
    }

    /**
     * Parse a paragraph into a test case candidate.
     * First sentence = name, rest = body.
     * Look for "期望"/"Expected" in sentences.
     */
    private ParsedTestCase parseParagraph(String para, int index) {
        // Split into sentences (roughly by 。.!！\n)
        String[] sentences = para.split("[。.!！\\n]+");
        List<String> nonEmptySentences = new ArrayList<>();
        for (String s : sentences) {
            String t = s.trim();
            if (!t.isEmpty()) nonEmptySentences.add(t);
        }

        if (nonEmptySentences.isEmpty()) return null;

        String name = nonEmptySentences.get(0);
        String input = null;
        String expected = null;
        String description = null;

        // Collect body sentences (after first)
        StringBuilder inputBuilder = new StringBuilder();
        String lastExpectedSentence = null;

        for (int i = 1; i < nonEmptySentences.size(); i++) {
            String sentence = nonEmptySentences.get(i);
            boolean hasExpected = sentence.contains("期望") || sentence.contains("期望输出") ||
                sentence.toLowerCase().contains("expected");
            // Check if this sentence starts with 输入/Input marker
            boolean hasInput = sentence.startsWith("输入") || sentence.startsWith("Input");
            // Check if this sentence starts with 说明/Desc marker
            boolean hasDesc = sentence.startsWith("说明") || (sentence.startsWith("Desc") && sentence.length() > 4 && !sentence.substring(4).startsWith(":") && !sentence.substring(4).startsWith("："));

            if (hasExpected) {
                lastExpectedSentence = sentence;
            } else if (hasInput) {
                if (inputBuilder.length() > 0) inputBuilder.append("；");
                inputBuilder.append(sentence);
            } else if (hasDesc) {
                description = sentence;
            } else {
                if (inputBuilder.length() > 0) inputBuilder.append("；");
                inputBuilder.append(sentence);
            }
        }

        input = inputBuilder.length() > 0 ? inputBuilder.toString() : null;

        if (lastExpectedSentence != null) {
            expected = lastExpectedSentence
                .replaceFirst("^(?:期望|期望输出|Expected)[：:]?\\s*", "")
                .trim();
        }

        return new ParsedTestCase(name, input, expected, description, index);
    }

    /**
     * Record representing a single parsed test case.
     */
    public record ParsedTestCase(
        String name,
        String input,
        String expected,
        String description,
        int lineNumber
    ) {}
}
