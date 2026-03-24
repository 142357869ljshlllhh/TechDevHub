package com.techdevhub.exception;

import com.techdevhub.enums.ErrorCode;
import com.techdevhub.result.Result;
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
        return new Result(ErrorCode.SYSTEM_ERROR.getCode(), "系统异常，请稍后重试", null);
    }
}
