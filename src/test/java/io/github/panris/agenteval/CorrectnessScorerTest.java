package io.github.panris.agenteval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for CorrectnessScorer.
 */
class CorrectnessScorerTest {

    @Test
    @DisplayName("Exact match should return score 1.0")
    void testExactMatch() {
        TestCase testCase = new TestCase("2+2=?", "4");
        AgentOutput output = new AgentOutput("4", null, 100);

        io.github.panris.agenteval.scorer.builtin.CorrectnessScorer scorer =
            new io.github.panris.agenteval.scorer.builtin.CorrectnessScorer();

        io.github.panris.agenteval.scorer.ScorerResult result = scorer.evaluate(testCase, output);

        assertThat(result.getScore()).isEqualTo(1.0);
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("Partial match should return high score")
    void testPartialMatch() {
        TestCase testCase = new TestCase("What is Java?", "Java is a programming language");
        AgentOutput output = new AgentOutput("Java is a popular programming language", null, 100);

        io.github.panris.agenteval.scorer.builtin.CorrectnessScorer scorer =
            new io.github.panris.agenteval.scorer.builtin.CorrectnessScorer();

        io.github.panris.agenteval.scorer.ScorerResult result = scorer.evaluate(testCase, output);

        assertThat(result.getScore()).isGreaterThan(0.7);
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("No match should fail")
    void testNoMatch() {
        TestCase testCase = new TestCase("2+2=?", "4");
        AgentOutput output = new AgentOutput("This is completely different", null, 100);

        io.github.panris.agenteval.scorer.builtin.CorrectnessScorer scorer =
            new io.github.panris.agenteval.scorer.builtin.CorrectnessScorer();

        io.github.panris.agenteval.scorer.ScorerResult result = scorer.evaluate(testCase, output);

        assertThat(result.isPassed()).isFalse();
    }
}
