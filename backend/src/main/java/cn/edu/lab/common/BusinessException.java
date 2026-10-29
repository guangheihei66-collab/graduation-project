package cn.edu.lab.common;

public class BusinessException extends RuntimeException {
  public final ErrorCode code;

  public BusinessException(ErrorCode code) {
    super(code.message);
    this.code = code;
  }
}
