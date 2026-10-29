package cn.edu.lab.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private ResponseEntity<ApiResponse<Void>> error(ErrorCode c) {
    return ResponseEntity.status(c.status).body(ApiResponse.error(c));
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
    return error(e.code);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    ConstraintViolationException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.validation.BindException.class
  })
  ResponseEntity<ApiResponse<Void>> invalid(Exception e) {
    return error(ErrorCode.VALIDATION_ERROR);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiResponse<Void>> denied() {
    return error(ErrorCode.FORBIDDEN);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiResponse<Void>> conflict() {
    return error(ErrorCode.CONFLICT);
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ApiResponse<Void>> database(DataAccessException e) {
    if (e.getMostSpecificCause() instanceof java.sql.SQLException sql && sql.getErrorCode() == 3819)
      return error(ErrorCode.CONFLICT);
    log.error("数据库操作失败，类型={}", e.getClass().getSimpleName());
    return error(ErrorCode.RETRYABLE_ERROR);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiResponse<Void>> missing() {
    return error(ErrorCode.NOT_FOUND);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiResponse<Void>> method() {
    return error(ErrorCode.METHOD_NOT_ALLOWED);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiResponse<Void>> media() {
    return error(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> unknown(Exception e) {
    log.error("未处理异常，类型={}", e.getClass().getSimpleName());
    return error(ErrorCode.INTERNAL_ERROR);
  }
}
