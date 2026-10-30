const auth = require("../services/auth");
const session = require("./session");
const time = require("./time");
const business = require("./business");
async function guard(page) {
  try {
    if (!session.token()) {
      wx.reLaunch({ url: "/pages/login/index" });
      return false;
    }
    await auth.restore();
    return true;
  } catch (e) {
    page.setData({ error: e.message });
    return false;
  }
}
function alive(page) {
  page._generation = (page._generation || 0) + 1;
  return page._generation;
}
function close() {
  alive(this);
}
function error(page, e) {
  page.setData({ error: e.message || "操作失败，请重试" });
}
function decorate(row) {
  return {
    ...row,
    statusText: business.statuses[row.status] || row.status,
    timeText: `${time.format(row.startTime)} — ${time.format(row.endTime)}`,
  };
}
function list(fetcher, params, decorateRow = (value) => value) {
  return {
    data: { records: [], page: 0, total: 0, loading: false, error: "" },
    onHide: close,
    onUnload: close,
    async load(reset = false) {
      if (
        !reset &&
        (this.data.loading ||
          (this.data.page > 0 && this.data.records.length >= this.data.total))
      )
        return;
      const generation = alive(this);
      const next = reset ? 1 : this.data.page + 1;
      this.setData({
        loading: true,
        error: "",
        ...(reset ? { records: [], page: 0, total: 0 } : {}),
      });
      try {
        const result = await fetcher({
          ...params.call(this),
          page: next,
          size: 20,
        });
        if (generation !== this._generation) return;
        this.setData({
          records: (reset ? [] : this.data.records).concat(
            result.records.map(decorateRow),
          ),
          page: result.page,
          total: result.total,
        });
      } catch (e) {
        if (generation === this._generation) error(this, e);
      } finally {
        if (generation === this._generation) this.setData({ loading: false });
        wx.stopPullDownRefresh();
      }
    },
    async onShow() {
      if (await guard(this)) this.load(true);
    },
    onPullDownRefresh() {
      this.load(true);
    },
    onReachBottom() {
      this.load(false);
    },
    retry() {
      this.load(this.data.page === 0);
    },
  };
}
function confirm(content) {
  return new Promise((resolve) =>
    wx.showModal({
      title: "请确认",
      content,
      success: (r) => resolve(r.confirm),
      fail: () => resolve(false),
    }),
  );
}
module.exports = { guard, alive, close, error, decorate, list, confirm };
