package io.github.panris.agenteval.web;

import java.util.List;

/**
 * 全局常量定义
 */
public final class Constants {
    
    private Constants() {}
    
    // 评测相关
    public static final int MAX_CASES_PER_EVAL = 100;
    public static final int MAX_INPUT_LENGTH = 10000;
    
    public static final List<String> VALID_METRICS = List.of(
        "correctness",
        "safety",
        "response_time",
        "bleu",
        "rouge",
        "similarity"
    );
    
    // 批量导入
    public static final int MAX_BATCH_SIZE = 100;
}
