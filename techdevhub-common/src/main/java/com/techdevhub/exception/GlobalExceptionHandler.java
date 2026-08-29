package com.techdevhub.exception;

import com.techdevhub.enums.ErrorCode;
import com.techdevhub.result.Result;
import org.springframework.http.ResponseEntity;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Result BusinessException(BusinessException businessException){
        return Result.fail(businessException.getErrorCode());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        return new Result(ErrorCode.VALIDATION_ERROR.getCode(), msg, null);
    }

    @ExceptionHandler(BindException.class)
    public Result handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数绑定失败";
        return new Result(ErrorCode.VALIDATION_ERROR.getCode(), msg, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result handleConstraintViolationException(ConstraintViolationException e) {
        return new Result(ErrorCode.VALIDATION_ERROR.getCode(), e.getMessage(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return new Result(ErrorCode.BAD_REQUEST.getCode(), "请求体格式错误", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return new Result(ErrorCode.METHOD_NOT_ALLOWED.getCode(), "请求方法不支持", null);
    }

    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        e.printStackTrace();
        return new Result(ErrorCode.SYSTEM_ERROR.getCode(), "系统异常，请稍后重试", null);
    }

    /**
     * AI 服务调用失败：把对端语义翻译成 HTTP 状态 + 数字码。
     * 为什么不用 200+Result 兜底：blog 侧重试状态机依赖"HTTP 状态/数字码 → retryable"
     * 这条映射来决定退避重试还是 GIVEUP，语义丢失会破坏重试分层设计。
     * 对端 detail 若已是人类安全文案则附在 message 尾部，前端可直接展示。
     */
    @ExceptionHandler(AiCallException.class)
    public ResponseEntity<Result> handleAiCallException(AiCallException e) {
        ErrorCode code;
        int httpStatus;
        if (e.getHttpStatus() == 429) {
            code = ErrorCode.AI_SERVICE_RATE_LIMITED;
            httpStatus = 429;
        } else if (e.isRetryable()) {
            code = ErrorCode.AI_SERVICE_TEMPORARY;
            httpStatus = 503;
        } else {
            code = ErrorCode.AI_SERVICE_PERMANENT;
            httpStatus = 502;
        }
        String message = e.getAiCode() != null ? e.getMessage() + " [" + e.getAiCode() + "]" : e.getMessage();
        return ResponseEntity.status(httpStatus).body(new Result(code.getCode(), message, null));
    }
}
