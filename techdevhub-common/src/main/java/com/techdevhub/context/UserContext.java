package com.techdevhub.context;

/**
 * 请求级上下文，基于 ThreadLocal。
 *
 * <p>在 {@code TraceInterceptor} / {@code JwtInterceptor} 的 preHandle 中填充，
 * 一次请求内的任意位置（Controller / Service / Mapper 之外的同步调用链）都可以直接读取，
 * 在 {@code TraceInterceptor#afterCompletion} 中调用 {@link #clear()} 清理，避免线程池复用导致串号。
 *
 * <p>存放内容：
 * <ul>
 *   <li>{@code userId}   —— 当前登录用户 id（来自 JWT subject）</li>
 *   <li>{@code isAdmin}  —— 是否管理员（来自 JWT 的 isAdmin claim，登录时按 user_info.status==1 写入）</li>
 *   <li>{@code traceId}  —— 全链路追踪 id（网关/入口生成，随请求透传）</li>
 * </ul>
 */
public class UserContext {

    private static final ThreadLocal<UserContext> CONTEXT = ThreadLocal.withInitial(UserContext::new);

    private Long userId;
    private boolean isAdmin = false;
    private String traceId;

    public static UserContext current() {
        return CONTEXT.get();
    }

    public static void setUserId(Long userId) {
        CONTEXT.get().userId = userId;
    }

    public static Long getUserId() {
        return CONTEXT.get().userId;
    }

    public static void setIsAdmin(boolean isAdmin) {
        CONTEXT.get().isAdmin = isAdmin;
    }

    public static boolean isAdmin() {
        return CONTEXT.get().isAdmin;
    }

    public static void setTraceId(String traceId) {
        CONTEXT.get().traceId = traceId;
    }

    public static String getTraceId() {
        return CONTEXT.get().traceId;
    }

    /** 请求结束必须调用，否则线程池复用会串号。 */
    public static void clear() {
        CONTEXT.remove();
    }
}
