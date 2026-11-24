package com.guidepins.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidepins.common.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 在 JSON 序列化前集中包装 API 响应，业务控制器只返回业务数据。 */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
    @Autowired
    private ObjectMapper json;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 文件和 Range 仍交给 Resource 转换器，避免影响 PMTiles 的二进制内容。
        return AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType)
                || StringHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String path = request.getURI().getPath();
        if (!(path.equals("/api") || path.startsWith("/api/")) || body instanceof ApiResponse<?>
                || !(MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || contentType.getSubtype().endsWith("+json"))) {
            return body;
        }
        int code = response instanceof ServletServerHttpResponse servlet
                ? servlet.getServletResponse().getStatus() : 200;
        String message = code == 200 && (request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.HEAD)
                ? "查询成功" : ApiResponse.messageFor(code);
        ApiResponse<?> result = code >= 400
                ? ApiResponse.error(code, message)
                : ApiResponse.success(code, message, body);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (StringHttpMessageConverter.class.isAssignableFrom(converterType)) {
            // String 转换器只接收字符串，显式序列化，避免泛型响应引发类型转换异常。
            try {
                return json.writeValueAsString(result);
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("统一响应序列化失败", error);
            }
        }
        return result;
    }
}
