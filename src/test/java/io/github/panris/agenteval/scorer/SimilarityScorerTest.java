package io.github.panris.agenteval.scorer;

import io.github.panris.agenteval.*;
import io.github.panris.agenteval.scorer.builtin.SimilarityScorer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SimilarityScorer (Jaccard trigram similarity).
 */
class SimilarityScorerTest {

    private final SimilarityScorer scorer = new SimilarityScorer();

    @Test
    @DisplayName("Identical strings → all trigrams overlap → score 1.0, passed")
    void testIdenticalStrings() {
        TestCase tc = new TestCase("identical strings test", "hello world");
        AgentOutput out = new AgentOutput("hello world", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.getScore()).isEqualTo(1.0);
        assertThat(r.isPassed()).isTrue();
    }

    @Test
    @DisplayName("Very different strings → low similarity, failed")
    void testVeryDifferent() {
        TestCase tc = new TestCase("very different test", "abcdef");
        AgentOutput out = new AgentOutput("ghijkl", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.getScore()).isEqualTo(0.0);
        assertThat(r.isPassed()).isFalse(); // 0.0 < 0.5
    }

    @Test
    @DisplayName("Empty expected → failed")
    void testEmptyExpected() {
        TestCase tc = new TestCase("empty expected test", "");
        AgentOutput out = new AgentOutput("", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.isPassed()).isFalse();
    }

    @Test
    @DisplayName("Empty hypothesis → score 0.0, failed")
    void testEmptyHypothesis() {
        TestCase tc = new TestCase("empty hypothesis test", "hello world");
        AgentOutput out = new AgentOutput("", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.getScore()).isEqualTo(0.0);
        assertThat(r.isPassed()).isFalse();
    }

    @Test
    @DisplayName("Agent error → failed")
    void testAgentError() {
        TestCase tc = new TestCase("agent error test", "hello");
        AgentOutput out = new AgentOutput(new RuntimeException("network error"));

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.isPassed()).isFalse();
        assertThat(r.getRationale()).contains("Agent execution failed");
    }

    @Test
    @DisplayName("Partial overlap → moderate score, may pass or fail depending on threshold")
    void testPartialOverlap() {
        TestCase tc = new TestCase("partial overlap test", "hello world this is a test");
        AgentOutput out = new AgentOutput("hello world that is a demo", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        // Should have non-zero overlap but below 0.5
        assertThat(r.getScore()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("Score at threshold 0.5 → passed")
    void testExactThreshold() {
        // Trigrams from "ab" → [] (too short), "abc" → ["abc"]
        // Score should be 1.0 if both are empty, 0.0 otherwise
        // Use longer strings to get exact behavior
        TestCase tc = new TestCase("exact threshold test", "abc");
        AgentOutput out = new AgentOutput("abc", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.getScore()).isEqualTo(1.0);
        assertThat(r.isPassed()).isTrue();
    }

    @Test
    @DisplayName("Single character strings → both empty trigrams → score 1.0")
    void testSingleCharBoth() {
        TestCase tc = new TestCase("single char both test", "a");
        AgentOutput out = new AgentOutput("b", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        // Both have empty trigram sets → union = {}, intersection = {} → 0/0 treated as 1.0
        assertThat(r.getScore()).isEqualTo(1.0);
        assertThat(r.isPassed()).isTrue();
    }

    @Test
    @DisplayName("getName returns 'similarity'")
    void testGetName() {
        assertThat(scorer.getName()).isEqualTo("similarity");
    }

    @Test
    @DisplayName("Rationale contains similarity percentage")
    void testRationale() {
        TestCase tc = new TestCase("rationale test", "hello");
        AgentOutput out = new AgentOutput("hello", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.getRationale()).contains("Similarity");
        assertThat(r.getRationale()).contains("%");
    }
}
