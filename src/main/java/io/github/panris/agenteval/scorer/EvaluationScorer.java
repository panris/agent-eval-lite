package io.github.panris.agenteval.scorer;

import io.github.panris.agenteval.AgentOutput;
import io.github.panris.agenteval.TestCase;

/**
 * Interface for evaluation scorers.
 */
public interface EvaluationScorer {

    /**
     * Get the name of this scorer.
     *
     * @return scorer name
     */
    String getName();

    /**
     * Evaluate a test case with given agent output.
     *
     * @param testCase the test case
     * @param output   the agent output
     * @return the evaluation result
     */
    ScorerResult evaluate(TestCase testCase, AgentOutput output);

    /**
     * Get the description of this scorer.
     *
     * @return description
     */
    default String getDescription() {
        return "Evaluation scorer: " + getName();
    }

    /**
     * Get the threshold for passing.
     *
     * @return threshold (0.0 to 1.0)
     */
    default double getThreshold() {
        return 0.7;
    }
}
