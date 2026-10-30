package cn.edu.lab.auth.wechat;

/** 只返回服务端验证的身份；实现不得记录微信凭据或响应。 */
public interface WechatClient {
  String exchange(String code);
}
