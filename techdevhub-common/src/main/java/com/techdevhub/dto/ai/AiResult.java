package com.techdevhub.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Java 内部 Result 的泛型视图 —— 仅供 Feign 客户端反序列化 ai-service 响应用。
 * 为什么不直接用 com.techdevhub.result.Result：其 data 是 Object，
 * Feign 的 Jackson 解码会得到 LinkedHashMap；泛型版本让解码直接得到目标 DTO。
 * 与 Result 的字段一一对应（code/message/data），不引入行为。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiResult<T> {

    private Integer code;

    private String message;

    private T data;

    public boolean isSuccess() {
        return code != null && code == 200;
    }
}
