package io.github.panris.agenteval.scorer;

import io.github.panris.agenteval.*;
import io.github.panris.agenteval.scorer.builtin.BleuScorer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for BleuScorer.
 */
class BleuScorerTest {

    private final BleuScorer scorer = new BleuScorer();

    @Test
    @DisplayName("Full word overlap → score 1.0, passed")
    void testFullOverlap() {
        TestCase tc = new TestCase("full word overlap test", "hello world");
        AgentOutput out = new AgentOutput("hello world", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.isPassed()).isTrue();
        assertThat(r.getScore()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Partial word overlap → moderate score")
    void testPartialOverlap() {
        TestCase tc = new TestCase("word overlap test", "hello world foo");
        AgentOutput out = new AgentOutput("hello world bar", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        // 2 matches / 3 hyp words = 0.67
        assertThat(r.getScore()).isCloseTo(0.667, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("No word overlap → score 0.0, failed")
    void testNoOverlap() {
        TestCase tc = new TestCase("no overlap test", "apple banana");
        AgentOutput out = new AgentOutput("dog cat bird", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.getScore()).isEqualTo(0.0);
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
    @DisplayName("Agent error → score 0.0, failed")
    void testAgentError() {
        TestCase tc = new TestCase("agent error test", "hello");
        AgentOutput out = new AgentOutput(new RuntimeException("timeout"));

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.isPassed()).isFalse();
        assertThat(r.getRationale()).contains("Agent execution failed");
    }

    @Test
    @DisplayName("getName returns 'bleu'")
    void testGetName() {
        assertThat(scorer.getName()).isEqualTo("bleu");
    }

    @Test
    @DisplayName("Score above threshold 0.5 → passed")
    void testPassThreshold() {
        TestCase tc = new TestCase("high overlap", "the quick brown fox");
        AgentOutput out = new AgentOutput("the quick", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.isPassed()).isTrue(); // 2/2 = 1.0
    }

    @Test
    @DisplayName("Score below threshold 0.5 → failed")
    void testFailThreshold() {
        TestCase tc = new TestCase("no overlap", "a b c d e");
        AgentOutput out = new AgentOutput("x y z", null, 50);

        ScorerResult r = scorer.evaluate(tc, out);

        assertThat(r.isPassed()).isFalse(); // 0/3 = 0.0
    }
}
