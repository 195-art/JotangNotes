package org.example.jotangnote.common;

import io.jsonwebtoken.JwtException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

/**
 * 全局异常处理，将登录校验、参数校验和运行时异常封装为统一结果
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理登录令牌校验异常
     *
     * @param e 令牌校验异常
     * @return 登录失效提示，同时返回HTTP 401
     */
    @ExceptionHandler(JwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result handleJwtException(JwtException e) {
        return Result.fail(401, "登录已失效，请重新登录");
    }

    /**
     * 处理必需请求头缺失异常
     *
     * @param e 请求头缺失异常，鉴权接口中通常为缺少Authorization
     * @return 登录失效提示，同时返回HTTP 401
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result handleMissingHeader(MissingRequestHeaderException e) {
        return Result.fail(401, "登录已失效，请重新登录");
    }

    /**
     * 处理请求参数校验异常
     *
     * @param e 参数校验异常
     * @return 首个字段校验错误的提示
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数错误";
        return Result.fail(message);
    }

    /**
     * 处理运行时异常
     *
     * @param e 运行时异常
     * @return 异常信息对应的失败结果
     */
    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        return Result.fail(e.getMessage());
    }
}
