const api = require("../../services/api");
const p = require("../../utils/page");
const b = require("../../utils/business");
Page({
  data: { item: null, error: "", loading: false },
  onLoad(q) {
    this.id = q.id;
  },
  async onShow() {
    if (await p.guard(this)) this.load();
  },
  onHide: p.close,
  onUnload: p.close,
  onPullDownRefresh() {
    this.load();
  },
  retry() {
    this.load();
  },
  async load() {
    const g = p.alive(this);
    this.setData({ loading: true, error: "" });
    try {
      const item = await api.equipment(this.id);
      if (g === this._generation)
        this.setData({
          item: {
            ...item,
            categoryText: b.categories[item.category],
            statusText: b.equipmentStatus[item.status],
          },
        });
    } catch (e) {
      if (g === this._generation) p.error(this, e);
    } finally {
      if (g === this._generation) this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },
  reserve() {
    if (this.data.item && this.data.item.status === "AVAILABLE")
      wx.navigateTo({ url: "/pages/slots/index?id=" + this.id });
  },
});
