package com.example.securehello;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-HTTP cookie checks. MockMvc does not exercise Tomcat's session serialization
 * or the {@code server.servlet.session.cookie.*} settings, so this test starts the
 * embedded server and inspects the actual {@code Set-Cookie} header.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCookieTest {

    private static final String VALID_LOGIN = """
            {"username":"test","password":"123456"}""";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void loginCookieIsHttpOnlyAndSameSiteLax() {
        ResponseEntity<String> response = login(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "a successful login must set a session cookie");
        assertTrue(setCookie.contains("JSESSIONID="), setCookie);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.replace(" ", "").contains("SameSite=Lax"), setCookie);
    }

    @Test
    void loginRotatesAnExistingSessionId() {
        String firstId = sessionId(login(null));
        assertNotNull(firstId);

        ResponseEntity<String> second = login("JSESSIONID=" + firstId);
        assertEquals(HttpStatus.OK, second.getStatusCode());

        String secondId = sessionId(second);
        assertNotEquals(firstId, secondId,
                "login must change the session id (session-fixation protection)");
    }

    private ResponseEntity<String> login(String cookieHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookieHeader != null) {
            headers.add(HttpHeaders.COOKIE, cookieHeader);
        }
        return rest.postForEntity("/api/login",
                new HttpEntity<>(VALID_LOGIN, headers), String.class);
    }

    private static String sessionId(ResponseEntity<String> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "expected a Set-Cookie header");
        String prefix = "JSESSIONID=";
        int start = setCookie.indexOf(prefix);
        assertTrue(start >= 0, "expected JSESSIONID in " + setCookie);
        int valueStart = start + prefix.length();
        int end = setCookie.indexOf(';', valueStart);
        return end < 0 ? setCookie.substring(valueStart) : setCookie.substring(valueStart, end);
    }
}
