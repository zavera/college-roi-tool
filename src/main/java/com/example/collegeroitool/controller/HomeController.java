package com.example.collegeroitool.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Explicit route for "/" so Spring Security's filter chain intercepts it
 * instead of the static-resource welcome-page handler (which bypasses auth).
 * Authentication is enforced by SecurityConfig.filterChain when devBypass=false.
 */
@Controller
public class HomeController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public Resource index() {
        return new ClassPathResource("static/index.html");
    }
}
