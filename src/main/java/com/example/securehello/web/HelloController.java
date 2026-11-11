package com.example.securehello.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The protected API from the task. It is only reachable with an authenticated
 * session; {@code SecurityConfig} rejects everything that is not explicitly public.
 */
@RestController
public class HelloController {

    public static final String HELLO_WORLD = "Hello World";

    @GetMapping(path = "/api/hello", produces = MediaType.TEXT_PLAIN_VALUE)
    public String hello() {
        return HELLO_WORLD;
    }
}
