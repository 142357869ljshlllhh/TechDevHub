package com.techdevhub.exception;

import lombok.Getter;

/**
 * 调用 Python AI 服务失败的业务异常（Java 侧唯一出槽口径）。
 *
 * 为什么不复用 BusinessException：审核重试状态机需要区分"可重试/不可重试"，
 * 而 ErrorCode 枚举没有 retryable 维度；AiCallException 把对端信封三元组
 * (detail, code, retryable) + HTTP 状态完整带到编排层，Feign ErrorDecoder 还原后
 * blog 侧无需再解析 JSON 信封。
 */
@Getter
public class AiCallException extends RuntimeException {

    /** 对端 HTTP 状态码（503/429/502/422...） */
    private final int httpStatus;

    /** 对端两段式错误码，如 MODERATION_LLM_TEMPORARY；网络层故障无信封时为 null */
    private final String aiCode;

    /** true=退避重试可能恢复；false=重试无意义，直接 GIVEUP */
    private final boolean retryable;

    public AiCallException(String message, int httpStatus, String aiCode, boolean retryable) {
        super(message);
        this.httpStatus = httpStatus;
        this.aiCode = aiCode;
        this.retryable = retryable;
    }

    /** 网络层故障（连不上/超时）——视为可重试的临时故障 */
    public static AiCallException transport(String message) {
        return new AiCallException(message, 503, null, true);
    }
}
