package com.grocerychoice.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String VERCEL_GIT_MAIN_ORIGIN =
            "https://grocery-choice-customer-git-main-grocery-choice.vercel.app";
    private static final String VERCEL_PROD_ORIGIN =
            "https://grocery-choice-customer.vercel.app";
    private static final String LOCALHOST_ORIGIN =
            "http://localhost:5173";
    private static final String UNAUTHORIZED_ORIGIN =
            "https://evil-unauthorized-site.com";

    @Test
    @DisplayName("OPTIONS /api/products preflight from Vercel git-main origin returns 200 with CORS headers")
    void testPreflightProductsVercelGitMain() throws Exception {
        mockMvc.perform(options("/api/products")
                .header("Origin", VERCEL_GIT_MAIN_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VERCEL_GIT_MAIN_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("OPTIONS /api/categories preflight from canonical Vercel origin returns 200 with CORS headers")
    void testPreflightCategoriesVercelProd() throws Exception {
        mockMvc.perform(options("/api/categories")
                .header("Origin", VERCEL_PROD_ORIGIN)
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VERCEL_PROD_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("OPTIONS /api/orders preflight on authenticated endpoint returns 200 without requiring auth")
    void testPreflightAuthenticatedEndpoint() throws Exception {
        mockMvc.perform(options("/api/orders")
                .header("Origin", VERCEL_GIT_MAIN_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VERCEL_GIT_MAIN_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("OPTIONS /api/products preflight from localhost origin returns 200")
    void testPreflightLocalhost() throws Exception {
        mockMvc.perform(options("/api/products")
                .header("Origin", LOCALHOST_ORIGIN)
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", LOCALHOST_ORIGIN));
    }

    @Test
    @DisplayName("OPTIONS /api/products preflight from unauthorized origin is forbidden")
    void testPreflightUnauthorizedOrigin() throws Exception {
        mockMvc.perform(options("/api/products")
                .header("Origin", UNAUTHORIZED_ORIGIN)
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("GET /api/products with Vercel origin returns 200 with CORS header")
    void testGetProductsWithCors() throws Exception {
        mockMvc.perform(get("/api/products")
                .header("Origin", VERCEL_GIT_MAIN_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VERCEL_GIT_MAIN_ORIGIN));
    }

    @Test
    @DisplayName("POST /api/orders without authentication is still rejected (security preserved)")
    void testPostOrdersUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/orders")
                .header("Origin", VERCEL_GIT_MAIN_ORIGIN)
                .header("Content-Type", "application/json")
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
