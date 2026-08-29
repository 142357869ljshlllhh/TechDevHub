package com.techdevhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Python AI 微服务连接配置。
 *
 * 为什么单独建 Properties 而不是散在 yml：
 * 与 AiMemoryProperties 同构，集中管理对端契约三项——地址、门禁 token、管理 token，
 * 便于启动时做存在性校验（生产环境 token 留空应在日志中显式告警而非静默放行）。
 *
 * 对应 Python 侧 .env：INTERNAL_TOKEN / ADMIN_TOKEN 必须与这里同值。
 */
@Data
@ConfigurationProperties(prefix = "techdevhub.ai.python")
public class AiPythonProperties {

    /** Python AI 服务地址（FastAPI，默认本机 8000） */
    private String baseUrl = "http://localhost:8000";

    /**
     * 服务间门禁 token（X-Internal-Token）。
     * 留空 = 门禁关闭，仅限本地联调——与 Python 侧同规则，生产必配。
     */
    private String internalToken = "";

    /** 管理端 token（X-Admin-Token），仅 recheck 端点附加 */
    private String adminToken = "";
}
