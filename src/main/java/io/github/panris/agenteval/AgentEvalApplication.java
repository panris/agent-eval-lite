package io.github.panris.agenteval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
public class AgentEvalApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentEvalApplication.class, args);
    }
}
