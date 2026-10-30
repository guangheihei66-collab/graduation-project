package cn.edu.lab.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WechatLoginResponse(
    boolean needBind, String bindTicket, String accessToken, int expiresIn, UserView user) {
  @Override
  public String toString() {
    return "WechatLoginResponse[凭据已隐藏]";
  }
}
