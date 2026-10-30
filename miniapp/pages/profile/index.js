const auth = require("../../services/auth");
const session = require("../../utils/session");
const p = require("../../utils/page");
Page({
  data: { user: null, error: "" },
  async onShow() {
    if (await p.guard(this)) this.setData({ user: session.user(), error: "" });
  },
  retry() {
    this.onShow();
  },
  async logout() {
    if (
      await p.confirm(
        "确定退出当前学生账号？本机令牌、用户信息和预约草稿将清除。",
      )
    )
      auth.logout();
  },
});
