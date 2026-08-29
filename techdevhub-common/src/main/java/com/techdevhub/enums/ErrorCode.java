package com.techdevhub.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(200, "success"),
    VALIDATION_ERROR(400, "validation failed"),
    BAD_REQUEST(401, "bad request"),
    METHOD_NOT_ALLOWED(402, "method not allowed"),
    SYSTEM_ERROR(403, "system error"),
    TOKEN_GENGERATE_FAILED(404, "token generate failed"),
    UNAUTHORIZED(405, "unauthorized"),
    TOKENEXPIRED(406, "token expired"),
    TOKEN_INVALID(407, "token invalid"),
    FORBIDDEN(408, "forbidden"),
    ACCOUNT_IS_DELETE(409, "account deleted"),

    USER_USERNAME_IS_USED(1000, "username already used"),
    USER_EMAIL_IS_USED(1001, "email already used"),
    USER_REGISTER_FAILED(1002, "register failed"),
    USER_EMAIL_NOT_EXIST(1003, "email not exist"),
    USER_PASSWORD_IS_WORONG(1004, "password wrong"),
    USER_UPDATE_OTHERS(1005, "cannot update others"),
    USER_UPDATE_ALL_ARE_NULL(1006, "update payload empty"),
    USER_UPDATEINFORMATION_FAILED(1007, "update profile failed"),
    USER_NOT_EXISTS(1008, "user not exists"),
    USER_NEW_PASSWORD_SAME_AS_OLD(1009, "new password same as old"),
    USER_UPDATE_PASSWORD_FAILED(1010, "update password failed"),
    TOKEN_LOGGED_OUT(1011, "token logged out"),

    BLOG_INSERT_FAILED(1100, "create blog failed"),
    BLOG_NOT_FOUND(1101, "blog not found"),
    BLOG_FORBIDDEN(1102, "no permission for blog"),
    BLOG_CONTENT_NOT_CHANGED(1103, "blog not changed"),
    BLOG_NOT_PULL(1104, "blog not published"),

    CATEGORY_ID_ALREADY_EXISTS(1200, "category id already exists"),
    CATEGORY_NAME_ALREADY_EXISTS(1201, "category name already exists"),
    CATEGORY_NOT_FOUND(1202, "category not found"),
    CATEGORY_CREATE_FAILED(1203, "create category failed"),
    CATEGORY_UPDATE_FAILED(1204, "update category failed"),
    CATEGORY_DELETE_FAILED(1205, "delete category failed"),

    FOLLOW_SELF_NOT_ALLOWED(1300, "cannot follow yourself"),
    FOLLOW_RELATION_NOT_FOUND(1301, "follow relation not found"),
    FOLLOW_CREATE_FAILED(1302, "follow failed"),
    FOLLOW_CANCEL_FAILED(1303, "unfollow failed"),
    FOLLOW_USER_CLIENT_FAIL(1303,"get followers failed"),

    LIKE_ALREADY_CANCELLED(1400, "like relation already cancelled"),
    LIKE_RELATION_NOT_FOUND(1401, "like relation not found"),
    LIKE_CREATE_FAILED(1402, "like failed"),
    LIKE_CANCEL_FAILED(1403, "unlike failed"),

    COMMENT_NOT_FOUND(1500, "comment not found"),
    COMMENT_CREATE_FAILED(1501, "comment create failed"),
    COMMENT_DELETE_FAILED(1502, "comment delete failed"),
    COMMENT_PARENT_INVALID(1503, "comment parent invalid"),

    // ---- AI 集成段（1600+）：与 Python AI 微服务交互相关。
    // 数字码只用于 Java 内部 Result 信封；对端两段式字符串码（如 MODERATION_LLM_TEMPORARY）
    // 在 AiCallException.aiCode 中原样保留，两套体系不混用。
    AI_SERVICE_TEMPORARY(1600, "AI service temporarily unavailable, retry later"),
    AI_SERVICE_PERMANENT(1601, "AI service rejected the request, retry is meaningless"),
    AI_SERVICE_RATE_LIMITED(1602, "AI service rate limited, backoff longer"),
    AI_SERVICE_BAD_REQUEST(1603, "AI service rejected the payload (validation)"),
    AI_INTERNAL_UNAUTHORIZED(1604, "missing or wrong internal token"),
    AI_FORBIDDEN(1605, "forbidden by AI service (ownership/admin check failed)");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
