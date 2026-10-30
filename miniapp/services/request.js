const session = require("../utils/session");
const time = require("../utils/time");
const config = require("../config");
const messages = {
  TIME_CONFLICT: "设备时段已被占用，请返回重新选择",
  INVALID_STATE: "状态或时间已变化，请刷新后操作",
  QUOTA_EXCEEDED: "有效预约已达到3条上限",
  RESOURCE_UNAVAILABLE: "实验室或设备暂不可预约",
  IDEMPOTENCY_CONFLICT: "提交内容与原幂等键不一致，请刷新检查",
  WECHAT_BIND_CONFLICT: "学生或微信已有绑定，请联系管理员",
  BIND_TICKET_INVALID: "绑定凭证已失效，请重新微信登录",
  WECHAT_CODE_INVALID: "微信登录凭据失效，请重新登录",
};
class ApiError extends Error {
  constructor(status, code, message, requestId) {
    super(message);
    this.status = status;
    this.code = code;
    this.requestId = requestId;
  }
}
let redirecting = false;
function loginRequired() {
  session.clear();
  if (!redirecting) {
    redirecting = true;
    wx.reLaunch({
      url: "/pages/login/index",
      complete: () => {
        redirecting = false;
      },
    });
  }
}
function request(
  path,
  { method = "GET", data, header = {}, anonymous = false } = {},
) {
  return new Promise((resolve, reject) => {
    let base;
    try {
      base = config.baseUrl();
    } catch (e) {
      reject(e);
      return;
    }
    const headers = { "Content-Type": "application/json", ...header };
    const requestToken = session.token();
    if (!anonymous && requestToken)
      headers.Authorization = `Bearer ${requestToken}`;
    wx.request({
      url: base + path,
      method,
      data,
      header: headers,
      timeout: 12000,
      success(response) {
        time.sync(response.header);
        const status = response.statusCode;
        const body = response.data || {};
        if (status >= 200 && status < 300 && body.code === "OK") {
          resolve(body.data);
          return;
        }
        if (status === 401 && !anonymous && session.token() === requestToken)
          loginRequired();
        const fallback = {
          400: "参数不正确，请检查输入",
          401: "登录失效或凭据不正确",
          403: "无权访问此资源",
          404: "资源不存在或不可访问",
          409: "数据已变化，请刷新后重试",
          503: "服务暂不可用，请保留原提交稍后重试",
        };
        reject(
          new ApiError(
            status,
            body.code || "HTTP_ERROR",
            messages[body.code] || fallback[status] || "服务请求失败",
            body.requestId,
          ),
        );
      },
      fail(error) {
        reject(
          new ApiError(
            0,
            "NETWORK_ERROR",
            /timeout/i.test(error.errMsg || "")
              ? "请求超时，结果可能已提交；重试同一内容会复用原提交标识"
              : "网络连接失败，请检查网络后重试",
          ),
        );
      },
    });
  });
}
module.exports = { request, ApiError, loginRequired };
