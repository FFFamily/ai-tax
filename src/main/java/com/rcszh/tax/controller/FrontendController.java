package com.rcszh.tax.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Vite-built Vue workbench from the same Spring Boot process.
 * API controllers remain at their existing paths; this controller only handles
 * the Vue workbench entry route.
 */
@Controller
public class FrontendController {

    @GetMapping({"/workbench", "/workbench/"})
    public String workbench() {
        return "forward:/workbench/index.html";
    }
}
