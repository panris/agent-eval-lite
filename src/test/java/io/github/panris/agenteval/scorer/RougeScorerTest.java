package io.github.panris.agenteval.scorer;

import io.github.panris.agenteval.*;
import io.github.panris.agenteval.scorer.builtin.RougeScorer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RougeScorer (ROUGE-L / LCS ratio).
 */
class RougeScorerTest {

    private final RougeScorer scorer = new RougeScorer();

    @Test
    @DisplayName("Identical strings → LCS = length → ratio 1.0 → passed")
    void testIdenticalStrings() {
        TestCase tc = new TestCase("same string test", "hello world");
        AgentOutput out = new AgentOutput("hello world", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.getScore()).isEqualTo(1.0);
        assertThat(r.isPassed()).isTrue();
    }

    @Test
    @DisplayName("Substring relationship → high LCS ratio")
    void testSubstring() {
        // expected: "hello world" (11 chars), actual: "hello world and more" (20 chars)
        // LCS = "hello world" = 11, ratio = LCS / reference_len = 11/11 = 1.0
        TestCase tc = new TestCase("substring test", "hello world");
        AgentOutput out = new AgentOutput("hello world and more", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.getScore()).isEqualTo(1.0);
        assertThat(r.isPassed()).isTrue();
    }

    @Test
    @DisplayName("No common subsequence → score 0.0, failed")
    void testNoCommonSubsequence() {
        TestCase tc = new TestCase("no common subsequence test", "abc");
        AgentOutput out = new AgentOutput("xyz", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.getScore()).isEqualTo(0.0);
        assertThat(r.isPassed()).isFalse();
    }

    @Test
    @DisplayName("Partial overlap → moderate score")
    void testPartialOverlap() {
        // expected: "the quick brown fox" (19 chars), actual: "the fast red fox" (16 chars)
        // LCS = "the  fox" = "the  fox" no... let me think
        // LCS("thequickbrownfox", "thefastredfox") = "the  fox" = 8 chars, ratio = 8/19 = 0.42
        TestCase tc = new TestCase("partial overlap test", "the quick brown fox");
        AgentOutput out = new AgentOutput("the fast red fox", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.getScore()).isBetween(0.3, 0.6);
    }

    @Test
    @DisplayName("Empty expected → failed")
    void testEmptyExpected() {
        TestCase tc = new TestCase("empty expected test", "");
        AgentOutput out = new AgentOutput("hello world", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.isPassed()).isFalse();
    }

    @Test
    @DisplayName("Empty hypothesis → score 0.0, failed")
    void testEmptyHypothesis() {
        TestCase tc = new TestCase("empty hypothesis test", "hello");
        AgentOutput out = new AgentOutput("", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.getScore()).isEqualTo(0.0);
        assertThat(r.isPassed()).isFalse();
    }

    @Test
    @DisplayName("Agent error → failed")
    void testAgentError() {
        TestCase tc = new TestCase("agent error test", "hello");
        AgentOutput out = new AgentOutput(new RuntimeException("OOM"));

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.isPassed()).isFalse();
        assertThat(r.getRationale()).contains("Agent execution failed");
    }

    @Test
    @DisplayName("Score below threshold 0.4 → failed")
    void testFailThreshold() {
        // Very different strings → low LCS ratio
        TestCase tc = new TestCase("fail threshold test", "abcdefghij");
        AgentOutput out = new AgentOutput("xyzlmnopqr", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.isPassed()).isFalse(); // ratio should be very low
    }

    @Test
    @DisplayName("getName returns 'rouge'")
    void testGetName() {
        assertThat(scorer.getName()).isEqualTo("rouge");
    }
}
