const submission = require("../../services/submission");
const p = require("../../utils/page");
const time = require("../../utils/time");
Page({
  data: { draft: null, busy: false, error: "", reselect: false },
  async onShow() {
    this._visible = true;
    if ((await p.guard(this)) && this._visible) {
      const d = submission.read();
      this.setData({
        draft: d
          ? {
              ...d,
              start: time.format(d.body.startTime),
              end: time.format(d.body.endTime),
            }
          : null,
        error: d ? "" : "提交内容已失效，请返回选择时段",
      });
    }
  },
  async submit() {
    if (this.data.busy || !this.data.draft || this.data.reselect) return;
    if (!(await p.confirm("确认提交此预约？待审批申请也会占用设备时段。")))
      return;
    if (this.data.busy) return;
    this.setData({ busy: true, error: "" });
    try {
      const result = await submission.submit();
      if (this._visible === false) return;
      wx.showToast({ title: "预约提交成功", icon: "success" });
      wx.redirectTo({ url: "/pages/reservation/index?id=" + result.id });
    } catch (e) {
      if (this._visible === false) return;
      this.setData({
        error: e.message,
        reselect: [
          "TIME_CONFLICT",
          "IDEMPOTENCY_CONFLICT",
          "RESOURCE_UNAVAILABLE",
          "VALIDATION_ERROR",
        ].includes(e.code),
      });
    } finally {
      this.setData({ busy: false });
    }
  },
  back() {
    wx.navigateBack();
  },
  onHide() {
    this._visible = false;
  },
  onUnload() {
    this._visible = false;
    this.setData({ draft: null });
  },
});
