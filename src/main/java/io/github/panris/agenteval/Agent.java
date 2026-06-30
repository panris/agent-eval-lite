package io.github.panris.agenteval;

import java.util.function.Function;

/**
 * Interface for agent that can be evaluated.
 */
@FunctionalInterface
public interface Agent {

    /**
     * Execute agent with given input.
     *
     * @param input the input string
     * @return the output string
     */
    String execute(String input);

    /**
     * Wrap a simple function as an Agent.
     */
    static Agent from(Function<String, String> function) {
        return function::apply;
    }
}
