const auth = require("../../services/auth");
const session = require("../../utils/session");
const { bindError } = require("../../utils/business");
Page({
  data: { username: "", password: "", busy: false, error: "" },
  onShow() {
    if (!session.ticket())
      this.setData({ error: "绑定凭证不存在或已失效，请返回重新微信登录" });
  },
  input(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },
  async submit() {
    if (this.data.busy) return;
    const error = bindError(this.data.username, this.data.password);
    if (error) {
      this.setData({ error });
      return;
    }
    this.setData({ busy: true, error: "" });
    try {
      await auth.bind(this.data.username, this.data.password);
      wx.switchTab({ url: "/pages/home/index" });
    } catch (e) {
      this.setData({ error: e.message });
      if (e.code === "BIND_TICKET_INVALID") session.setTicket("");
    } finally {
      this.setData({ busy: false, password: "" });
    }
  },
  back() {
    session.setTicket("");
    wx.reLaunch({ url: "/pages/login/index" });
  },
  onHide() {
    this.setData({ password: "" });
  },
  onUnload() {
    this.setData({ password: "" });
  },
});
