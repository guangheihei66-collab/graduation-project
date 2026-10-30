const auth = require("./services/auth");
App({
  onLaunch() {
    // 启动校验失败由页面呈现；不记录凭据，网络错误不伪造身份或清空有效令牌。
    this.ready = auth.restore().catch(() => null);
  },
});
