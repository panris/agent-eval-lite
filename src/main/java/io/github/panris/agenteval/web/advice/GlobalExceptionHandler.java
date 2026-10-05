package io.github.panris.agenteval.web.advice;

import io.github.panris.agenteval.web.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理：统一将未捕获异常转换为 {success:false, error:...} 格式，
 * 避免 Spring 默认 500 页泄露堆栈，并保证前后端错误契约一致。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 参数非法（如 Agent 端点缺失） → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数非法: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }

    /** @Valid 校验失败（Controller @RequestBody） → 400 + 字段级错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.badRequest().body(ApiResponse.error(msg));
    }

    /** Bean Validation 约束违反（如 Service 层 @Validated） → 400 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("约束校验失败: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }

    /** 请求体 JSON 解析失败 → 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error("请求体格式错误 (JSON 解析失败)"));
    }

    /** 其余未捕获异常 → 500（详细堆栈仅记录日志，不返回客户端） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("未处理的服务器异常", e);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "服务器内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
