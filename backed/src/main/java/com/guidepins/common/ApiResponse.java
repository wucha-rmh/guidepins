package com.guidepins.common;

/** JSON 接口的统一契约：code 与 HTTP 状态一致，业务内容放在 data 中。 */
public record ApiResponse<T>(int code, String message, T data) {
    /** 创建成功响应，保留调用方确定的状态码、提示和业务数据。 */
    public static <T> ApiResponse<T> success(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

    /** 创建失败响应，统一不携带业务数据。 */
    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static String messageFor(int code) {
        return switch (code) {
            case 201 -> "创建成功";
            case 400 -> "请求参数不符合要求";
            case 401 -> "请先登录";
            case 403 -> "请求校验失败或无操作权限";
            case 404 -> "资源不存在或不可访问";
            case 405 -> "请求方法不支持";
            case 406 -> "不支持请求的响应格式";
            case 409 -> "数据冲突，请刷新后重试";
            case 415 -> "请求内容类型不支持";
            default -> code >= 500 ? "服务器暂时无法处理请求，请稍后重试"
                    : code >= 400 ? "请求失败" : "操作成功";
        };
    }
}
