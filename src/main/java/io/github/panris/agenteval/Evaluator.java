package io.github.panris.agenteval;

import io.github.panris.agenteval.scorer.EvaluationScorer;
import io.github.panris.agenteval.scorer.ScorerResult;
import io.github.panris.agenteval.scorer.builtin.CorrectnessScorer;
import io.github.panris.agenteval.scorer.builtin.ResponseTimeScorer;
import io.github.panris.agenteval.scorer.builtin.SafetyScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Main evaluator for running agent evaluations.
 */
public class Evaluator {

    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);

    private final List<EvaluationScorer> scorers;
    private final int maxWorkers;
    private final int timeoutMs;

    private Evaluator(Builder builder) {
        this.scorers = builder.scorers;
        this.maxWorkers = builder.maxWorkers;
        this.timeoutMs = builder.timeoutMs;
    }

    /**
     * Create a new builder for Evaluator.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Evaluate an agent against test cases.
     *
     * @param agent     the agent to evaluate
     * @param testCases the test cases
     * @return evaluation report
     */
    public EvaluationReport evaluate(Agent agent, List<TestCase> testCases) {
        logger.info("Starting evaluation with {} test cases", testCases.size());
        long startTime = System.currentTimeMillis();

        List<Evaluation> evaluations = new ArrayList<>();

        // Execute with thread pool for concurrency
        ExecutorService executor = Executors.newFixedThreadPool(maxWorkers);
        List<Future<Evaluation>> futures = new ArrayList<>();

        for (TestCase testCase : testCases) {
            Future<Evaluation> future = executor.submit(() -> evaluateTestCase(agent, testCase));
            futures.add(future);
        }

        // Collect results
        for (Future<Evaluation> future : futures) {
            try {
                Evaluation evaluation = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                evaluations.add(evaluation);
            } catch (TimeoutException e) {
                logger.error("Evaluation timeout for test case");
                evaluations.add(createTimeoutEvaluation());
            } catch (Exception e) {
                logger.error("Evaluation error", e);
                evaluations.add(createErrorEvaluation(e));
            }
        }

        executor.shutdown();

        long executionTime = System.currentTimeMillis() - startTime;
        logger.info("Evaluation completed in {} ms", executionTime);

        return new EvaluationReport(evaluations, executionTime);
    }

    private Evaluation evaluateTestCase(Agent agent, TestCase testCase) {
        logger.debug("Evaluating test case: {}", testCase.getId());

        // Execute agent
        long startTime = System.currentTimeMillis();
        AgentOutput output;
        try {
            String result = agent.execute(testCase.getInput());
            long execTime = System.currentTimeMillis() - startTime;
            output = new AgentOutput(result, Map.of(), execTime);
        } catch (Exception e) {
            output = new AgentOutput(e);
        }

        // Run all scorers
        Map<String, ScorerResult> scorerResults = new HashMap<>();
        for (EvaluationScorer scorer : scorers) {
            ScorerResult result = scorer.evaluate(testCase, output);
            scorerResults.put(scorer.getName(), result);
        }

        return new Evaluation(testCase.getId(), output, scorerResults);
    }

    private Evaluation createTimeoutEvaluation() {
        Map<String, ScorerResult> results = new HashMap<>();
        AgentOutput timeoutOutput = new AgentOutput("TIMEOUT", Map.of(), 0);
        for (EvaluationScorer scorer : scorers) {
            results.put(scorer.getName(), ScorerResult.failed("Evaluation timeout"));
        }
        return new Evaluation("timeout", timeoutOutput, results);
    }

    private Evaluation createErrorEvaluation(Exception e) {
        Map<String, ScorerResult> results = new HashMap<>();
        AgentOutput errorOutput = new AgentOutput(e);
        for (EvaluationScorer scorer : scorers) {
            results.put(scorer.getName(), ScorerResult.failed("Error: " + e.getMessage()));
        }
        return new Evaluation("error", errorOutput, results);
    }

    /**
     * Builder for Evaluator.
     */
    public static class Builder {
        private List<EvaluationScorer> scorers = new ArrayList<>();
        private int maxWorkers = 4;
        private int timeoutMs = 30000;

        /**
         * Add metrics by name.
         *
         * @param metricNames names of built-in metrics
         * @return this builder
         */
        public Builder metrics(String... metricNames) {
            for (String name : metricNames) {
                EvaluationScorer scorer = createBuiltinScorer(name);
                if (scorer != null) {
                    scorers.add(scorer);
                } else {
                    logger.warn("Unknown metric: {}", name);
                }
            }
            return this;
        }

        /**
         * Add custom scorer.
         *
         * @param scorer the custom scorer
         * @return this builder
         */
        public Builder scorer(EvaluationScorer scorer) {
            scorers.add(scorer);
            return this;
        }

        /**
         * Set max workers for concurrent execution.
         *
         * @param maxWorkers max concurrent workers
         * @return this builder
         */
        public Builder maxWorkers(int maxWorkers) {
            this.maxWorkers = maxWorkers;
            return this;
        }

        /**
         * Set timeout in milliseconds.
         *
         * @param timeoutMs timeout in milliseconds
         * @return this builder
         */
        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        /**
         * Build the evaluator.
         *
         * @return the evaluator
         */
        public Evaluator build() {
            if (scorers.isEmpty()) {
                logger.info("No scorers specified, using default: correctness");
                scorers.add(new CorrectnessScorer());
            }
            return new Evaluator(this);
        }

        private EvaluationScorer createBuiltinScorer(String name) {
            return switch (name.toLowerCase()) {
                case "correctness" -> new CorrectnessScorer();
                case "safety" -> new SafetyScorer();
                case "response_time" -> new ResponseTimeScorer();
                case "bleu" -> new io.github.panris.agenteval.scorer.builtin.BleuScorer();
                case "rouge" -> new io.github.panris.agenteval.scorer.builtin.RougeScorer();
                case "similarity" -> new io.github.panris.agenteval.scorer.builtin.SimilarityScorer();
                default -> null;
            };
        }
    }
}
