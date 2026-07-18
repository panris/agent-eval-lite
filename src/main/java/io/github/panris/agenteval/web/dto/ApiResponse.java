package io.github.panris.agenteval.web.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一的 API 响应构建器
 */
public class ApiResponse {
    
    private ApiResponse() {}
    
    /**
     * 成功响应
     */
    public static Map<String, Object> success() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }
    
    /**
     * 成功响应（带数据）
     */
    public static Map<String, Object> success(String key, Object value) {
        Map<String, Object> result = success();
        result.put(key, value);
        return result;
    }
    
    /**
     * 成功响应（带多个数据）
     */
    public static Map<String, Object> success(Map<String, Object> data) {
        Map<String, Object> result = success();
        result.putAll(data);
        return result;
    }
    
    /**
     * 失败响应
     */
    public static Map<String, Object> error(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }
    
    /**
     * 失败响应（带额外数据）
     */
    public static Map<String, Object> error(String message, String key, Object value) {
        Map<String, Object> result = error(message);
        result.put(key, value);
        return result;
    }
}
