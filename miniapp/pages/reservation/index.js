const api = require("../../services/api");
const p = require("../../utils/page");
const b = require("../../utils/business");
const time = require("../../utils/time");
const session = require("../../utils/session");
Page({
  data: {
    item: null,
    loading: false,
    busy: false,
    error: "",
    reason: "",
    canCancel: false,
  },
  onLoad(q) {
    this.id = q.id;
  },
  async onShow() {
    this._visible = true;
    if (await p.guard(this)) this.load();
    this._timer = setInterval(() => this.updateCancel(), 10000);
  },
  onHide() {
    this._visible = false;
    p.close.call(this);
    clearInterval(this._timer);
  },
  onUnload() {
    this._visible = false;
    p.close.call(this);
    clearInterval(this._timer);
  },
  onPullDownRefresh() {
    this.load();
  },
  retry() {
    this.load();
  },
  updateCancel() {
    this.setData({
      canCancel: b.canCancel(this.data.item, session.user(), time.now()),
    });
  },
  async load() {
    const g = p.alive(this);
    this.setData({ loading: true, error: "" });
    try {
      const item = await api.reservation(this.id);
      if (g !== this._generation) return;
      if (!session.user() || item.studentId !== session.user().id) {
        this.setData({ item: null, error: "无权读取其他学生预约" });
        return;
      }
      this.setData({
        item: {
          ...p.decorate(item),
          logs: item.logs.map((l) => ({
            ...l,
            actionText: b.actions[l.action] || l.action,
            statusText: b.statuses[l.toStatus],
            timeText: time.format(l.createdAt),
          })),
        },
      });
      this.updateCancel();
    } catch (e) {
      if (g === this._generation) {
        this.setData({ item: null, canCancel: false });
        p.error(this, e);
      }
    } finally {
      if (g === this._generation) this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },
  reason(e) {
    this.setData({ reason: e.detail.value });
  },
  async cancel() {
    if (
      this.data.busy ||
      !b.canCancel(this.data.item, session.user(), time.now())
    )
      return;
    if (this.data.reason.length > 500) {
      this.setData({ error: "取消原因最多500字符" });
      return;
    }
    if (!(await p.confirm("确定取消此预约？取消后时段将释放，不能恢复。")))
      return;
    if (this.data.busy) return;
    this.setData({ busy: true, error: "" });
    try {
      await api.cancel(this.id, this.data.reason.trim());
      if (this._visible === false) return;
      wx.showToast({ title: "预约已取消", icon: "success" });
      this.setData({ reason: "" });
      await this.load();
    } catch (e) {
      if (this._visible === false) return;
      if (e.code === "INVALID_STATE") {
        await this.load();
        this.setData({ error: e.message });
      } else p.error(this, e);
    } finally {
      this.setData({ busy: false });
    }
  },
});
