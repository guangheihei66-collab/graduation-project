const auth = require("../../services/auth");
Page({
  data: { busy: false, error: "" },
  async login() {
    if (this.data.busy) return;
    this.setData({ busy: true, error: "" });
    try {
      const next = await auth.login();
      if (next === "bind") wx.navigateTo({ url: "/pages/bind/index" });
      else wx.switchTab({ url: "/pages/home/index" });
    } catch (e) {
      this.setData({ error: e.message });
    } finally {
      this.setData({ busy: false });
    }
  },
});
