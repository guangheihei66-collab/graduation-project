package cn.edu.lab.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  VALIDATION_ERROR(400, "请求参数不正确"),
  WECHAT_CODE_INVALID(401, "微信登录凭据无效，请重新登录"),
  BIND_TICKET_INVALID(401, "绑定凭证无效或已过期，请重新微信登录"),
  WECHAT_BIND_CONFLICT(409, "账号或微信已有绑定，请联系管理员"),
  UNAUTHORIZED(401, "凭据不正确或登录已失效"),
  FORBIDDEN(403, "无权访问"),
  NOT_FOUND(404, "资源不存在"),
  CONFLICT(409, "数据冲突"),
  TIME_CONFLICT(409, "设备时段已占用"),
  INVALID_STATE(409, "当前状态或时间不允许此操作"),
  QUOTA_EXCEEDED(409, "有效预约最多三条"),
  RESOURCE_UNAVAILABLE(409, "实验室或设备不可用，或存在有效预约"),
  IDEMPOTENCY_CONFLICT(409, "幂等键已用于不同请求"),
  RETRYABLE_ERROR(503, "服务暂不可用，请稍后重试"),
  METHOD_NOT_ALLOWED(405, "请求方法不支持"),
  UNSUPPORTED_MEDIA_TYPE(415, "请求内容类型不支持"),
  INTERNAL_ERROR(500, "服务器内部错误");
  public final HttpStatus status;
  public final String message;

  ErrorCode(int status, String message) {
    this.status = HttpStatus.valueOf(status);
    this.message = message;
  }
}
