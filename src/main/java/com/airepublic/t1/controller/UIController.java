package com.airepublic.t1.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for serving the Thymeleaf-based UI.
 */
@Controller
public class UIController {

    /**
     * Serves the main chat UI page.
     *
     * @return the name of the Thymeleaf template
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * Serves the main chat UI page at /ui path.
     *
     * @return the name of the Thymeleaf template
     */
    @GetMapping("/ui")
    public String ui() {
        return "index";
    }
}
