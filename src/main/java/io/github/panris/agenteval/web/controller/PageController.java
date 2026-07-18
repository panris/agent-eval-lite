package io.github.panris.agenteval.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for serving HTML pages.
 */
@Controller
public class PageController {

    @GetMapping("/agents")
    public String agentsPage() {
        return "agents";
    }
}
