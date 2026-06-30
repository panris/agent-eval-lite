# Agent Eval Lite

A simple and easy-to-use Agent evaluation framework for Java.

## Quick Start (5 minutes)

### 1. Add dependency

```xml
<dependency>
    <groupId>io.github.panris</groupId>
    <artifactId>agent-eval-lite</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Basic Evaluation

```java
import io.github.panris.agenteval.Evaluation;
import io.github.panris.agenteval.Evaluator;
import java.util.List;

public class QuickStart {
    public static void main(String[] args) {
        // Define test cases
        List<TestCase> testCases = List.of(
            new TestCase("2+2=?", "4"),
            new TestCase("3*3=?", "9")
        );

        // Create evaluator with built-in metrics
        Evaluator evaluator = Evaluator.builder()
            .metrics("correctness", "response_time")
            .build();

        // Run evaluation
        EvaluationReport report = evaluator.evaluate(myAgent, testCases);

        // View results
        System.out.println(report.getSummary());
    }
}
```

## Features

- ✅ **Simple API**: Single entry point `Evaluator.evaluate()`
- ✅ **Built-in Metrics**: Correctness, Safety, Relevance, ResponseTime, etc.
- ✅ **Custom Scorers**: Easily create your own evaluation metrics
- ✅ **Multiple Formats**: Support JSON, CSV, YAML test cases
- ✅ **Rich Reports**: JSON, Markdown, DataFrame-style output

## Built-in Metrics

| Metric | Description |
|--------|-------------|
| `correctness` | Evaluates if the output matches expected result |
| `safety` | Checks for harmful, toxic, or inappropriate content |
| `relevance` | Measures how relevant the response is to the input |
| `response_time` | Tracks execution time |
| `tool_call_correctness` | Validates tool calls for agents |

## Custom Scorer

```java
@Scorer("my_custom_metric")
public class MyCustomScorer implements EvaluationScorer {
    @Override
    public ScorerResult evaluate(TestCase testCase, AgentOutput output) {
        // Your custom logic here
        return ScorerResult.builder()
            .score(0.9)
            .passed(true)
            .rationale("Custom evaluation passed")
            .build();
    }
}
```

## License

MIT License
