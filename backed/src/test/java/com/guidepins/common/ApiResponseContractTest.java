package com.guidepins.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.auth.security.SessionAuthenticationFilter;
import com.guidepins.auth.security.SessionCookie;
import com.guidepins.auth.service.AuthService;
import com.guidepins.config.AuthSecurityConfig;
import jakarta.servlet.RequestDispatcher;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 验证容易遗漏的响应边界：字符串、重复包装、MVC 错误、安全过滤器与容器兜底。 */
@WebMvcTest(controllers = {ApiResponseContractTest.ContractController.class, ApiErrorController.class},
        properties = "spring.config.import=")
@AutoConfigureMockMvc
@Import({ApiResponseContractTest.ContractController.class, AuthSecurityConfig.class,
        SessionAuthenticationFilter.class, SessionCookie.class, ApiResponseWriter.class})
class ApiResponseContractTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;

    @BeforeEach
    void authenticatedRequest() {
        when(auth.authenticate(any())).thenReturn(Optional.of(new AccountIdentity(
                UUID.randomUUID(), "contract@example.test", null, "user", null, Instant.now())));
    }

    @Test
    void wrapsObjectsAndJsonStringsWithoutWrappingExistingEnvelopeTwice() throws Exception {
        mvc.perform(get("/api/contract/object"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.message").value("查询成功"))
                .andExpect(jsonPath("$.data.name").value("南京"));
        mvc.perform(get("/api/contract/string"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("文本数据"));
        mvc.perform(get("/api/contract/envelope"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("已处理")).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void unifiesMvcMethodAndMediaErrorsAndUnknownResources() throws Exception {
        mvc.perform(get("/api/contract/post-only"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.code").value(405)).andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/contract/object").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable()).andExpect(jsonPath("$.code").value(406))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/contract/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void hidesUnexpectedMvcAndContainerErrorDetails() throws Exception {
        String body = mvc.perform(get("/api/contract/crash"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(ApiResponse.messageFor(500)))
                .andExpect(jsonPath("$.data").isEmpty()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("internal-sensitive-detail"));
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "internal-sensitive-detail"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(ApiResponse.messageFor(500)))
                .andExpect(jsonPath("$.data").isEmpty()).andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void unifiesUnauthenticatedAndPreMvcAuthenticationFailures() throws Exception {
        when(auth.authenticate(any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/contract/object"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录")).andExpect(jsonPath("$.data").isEmpty());
        when(auth.authenticate(any())).thenThrow(new IllegalStateException("internal-sensitive-detail"));
        String body = mvc.perform(get("/api/contract/object"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(ApiResponse.messageFor(500)))
                .andExpect(jsonPath("$.data").isEmpty()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("internal-sensitive-detail"));
    }

    @RestController
    static class ContractController {
        @GetMapping("/api/contract/object")
        Map<String, String> object() { return Map.of("name", "南京"); }

        @GetMapping(value = "/api/contract/string", produces = MediaType.APPLICATION_JSON_VALUE)
        String string() { return "文本数据"; }

        @GetMapping("/api/contract/envelope")
        ApiResponse<Void> envelope() { return ApiResponse.success(200, "已处理", null); }

        @PostMapping("/api/contract/post-only")
        Map<String, String> postOnly() { return Map.of(); }

        @GetMapping("/api/contract/crash")
        void crash() { throw new IllegalStateException("internal-sensitive-detail"); }
    }
}
