package com.guidepins.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 覆盖容器错误分派的兜底 JSON，避免默认错误页泄漏不同结构或异常细节。 */
@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("${server.error.path:${error.path:/error}}")
    ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = status instanceof Integer value && value >= 400 && value <= 599 ? value : 404;
        return ResponseEntity.status(code).contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(code, ApiResponse.messageFor(code)));
    }
}
