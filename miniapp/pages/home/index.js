const api = require("../../services/api");
const { list } = require("../../utils/page");
Page({
  ...list(api.labs, function () {
    return { name: this.data.keyword || undefined };
  }),
  data: {
    records: [],
    page: 0,
    total: 0,
    loading: false,
    error: "",
    keyword: "",
  },
  input(e) {
    this.setData({ keyword: e.detail.value });
  },
  search() {
    this.load(true);
  },
  open(e) {
    wx.navigateTo({ url: "/pages/lab/index?id=" + e.currentTarget.dataset.id });
  },
});
