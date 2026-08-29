package com.techdevhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdevhub.exception.AiCallException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;

/**
 * SSE 桥：把 Python 侧的 Flux&lt;ServerSentEvent&gt; 逐帧泵给 MVC 的 SseEmitter。
 *
 * 关键决策（对端 java_integration_deploy.md §3 逐条对应）：
 * 1. 原样转发、不重新序列化——data 帧内容按原始字符串直接透传；
 * 2. [DONE] 哨兵帧 → complete；流内 error 帧（Python 已发的）自然透传后随流关闭；
 * 3. 流建立前故障（401/403/429/503）在 subscribe 时抛 AiCallException → 补发一帧
 *    {"type":"error"} 再 complete，让前端用同一套事件解析逻辑拿到可读提示；
 * 4. onTimeout/onError/onCompletion 里 dispose() 取消上游订阅——客户端断开不再空打 Python。
 */
@Slf4j
public final class SseBridge {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SseBridge() { }

    /**
     * 以字节直写绕开 String→bytes 的隐式编码环节。
     * 为什么不用 data(String)：SseEmitter 对 String 默认按 ISO-8859-1 序列化，
     * 中文 delta 帧会变 '?'；UTF-8 字节 + octet-stream 让转换器零转码落盘，
     * EventSource 前端按 UTF-8 解析，与直连 Python 行为一致。
     */
    private static SseEmitter.SseEventBuilder frame(String data) {
        return SseEmitter.event().data(data.getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_OCTET_STREAM);
    }

    public static SseEmitter bridge(Flux<ServerSentEvent<String>> upstream) {
        // timeout=0 表示不超时，判活靠 Python 侧 15s 心跳帧（对端契约）
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean finished = new AtomicBoolean(false);

        Disposable disposable = upstream.subscribe(
                event -> {
                    if (finished.get()) {
                        return;
                    }
                    String data = event.data();
                    if (data == null) {
                        return; // 心跳注释行等无 data 事件，跳过
                    }
                    try {
                        if ("[DONE]".equals(data)) {
                            // 正常结束哨兵：转发 [DONE] 帧后关闭，前端按帧协议收尾
                            emitter.send(frame(data));
                            finished.set(true);
                            emitter.complete();
                        } else {
                            emitter.send(frame(data));
                        }
                    } catch (Exception e) {
                        // 客户端已断开时 send 会抛错——语义等同断连，走 complete 清理
                        log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
                        if (finished.compareAndSet(false, true)) {
                            emitter.complete();
                        }
                    }
                },
                err -> {
                    if (finished.compareAndSet(false, true)) {
                        try {
                            String detail = (err instanceof AiCallException ace)
                                    ? ace.getMessage()
                                    : "AI 服务暂时不可用，请稍后重试";
                            // 流建立前故障：补发标准 error 帧，前端无需特判连接错误
                            emitter.send(frame(MAPPER.writeValueAsString(
                                    Map.of("type", "error", "detail", detail))));
                        } catch (Exception ignored) {
                            // 连错误帧都发不出去（连接已死），只做收尾
                        }
                        emitter.complete();
                    }
                },
                () -> {
                    // Python 正常关流（未发 [DONE] 的异常路径兜底）
                    if (finished.compareAndSet(false, true)) {
                        emitter.complete();
                    }
                });

        Runnable cleanup = () -> {
            if (finished.compareAndSet(false, true)) {
                // 客户端断开/超时 → 取消上游订阅，Python 侧收到连接关闭
                disposable.dispose();
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());
        return emitter;
    }
}
