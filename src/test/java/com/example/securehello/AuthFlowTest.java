package com.example.securehello;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end filter-chain tests. They deliberately go through the real login
 * endpoint instead of {@code @WithMockUser}, so the session credential path is
 * actually exercised.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    private static final String VALID_LOGIN = """
            {"username":"test","password":"123456"}""";

    private static final String WRONG_PASSWORD = """
            {"username":"test","password":"wrong-password"}""";

    private static final String UNKNOWN_USER = """
            {"username":"nobody","password":"123456"}""";

    private static final String BLANK_CREDENTIALS = """
            {"username":"","password":""}""";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedHelloReturns401WithFixedJsonOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // ErrorResponse is a record, so the field order is fixed. Pin the whole body:
        // README quotes this exact line as the observed output.
        assertEquals(
                "{\"status\":401,\"error\":\"Unauthorized\","
                        + "\"message\":\"Authentication required. POST /api/login first.\"}",
                result.getResponse().getContentAsString());
    }

    @Test
    void loginWithDemoCredentialsSucceedsAndCreatesSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("test"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn();

        assertNotNull(result.getRequest().getSession(false),
                "a successful login must create an HTTP session");
    }

    @Test
    void authenticatedHelloReturnsHelloWorld() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session, "login must produce a session to reuse");

        mockMvc.perform(get("/api/hello").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Hello World"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WRONG_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void loginWithUnknownUserReturns401() throws Exception {
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UNKNOWN_USER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void wrongPasswordAndUnknownUserReturnTheSameBody() throws Exception {
        MvcResult wrong = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WRONG_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult unknown = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UNKNOWN_USER))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String wrongBody = wrong.getResponse().getContentAsString();
        assertEquals(wrongBody, unknown.getResponse().getContentAsString(),
                "an unknown user must not be distinguishable from a wrong password");
        assertEquals(MediaType.APPLICATION_JSON_VALUE, wrong.getResponse().getContentType());
        assertTrue(wrongBody.contains("\"message\":\"Invalid username or password.\""), wrongBody);
    }

    @Test
    void failedReLoginDoesNotInvalidateAnExistingSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(post("/api/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WRONG_PASSWORD))
                .andExpect(status().isUnauthorized());

        // Documented behaviour: a typo during re-authentication must not log the user out.
        mockMvc.perform(get("/api/hello").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello World"));
    }

    @Test
    void blankCredentialsReturn400() throws Exception {
        // MockMvc stops at the handler-level 400 (empty body); a real Tomcat run forwards
        // to /error and adds the standard Boot JSON body, which the curl section shows.
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BLANK_CREDENTIALS))
                .andExpect(status().isBadRequest());
    }
}
