const api = require("../../services/api");
const { list, error } = require("../../utils/page");
const { categories, equipmentStatus } = require("../../utils/business");
const base = list(
  api.equipmentList,
  function () {
    return {
      labId: this.id,
      keyword: this.data.keyword || undefined,
      category: this.data.category || undefined,
    };
  },
  (r) => ({
    ...r,
    categoryText: categories[r.category],
    statusText: equipmentStatus[r.status],
  }),
);
Page({
  ...base,
  data: {
    ...base.data,
    lab: null,
    keyword: "",
    category: "",
    categoryIndex: 0,
    categoryNames: ["全部分类", ...Object.values(categories)],
    categoryValues: ["", ...Object.keys(categories)],
  },
  onLoad(q) {
    this.id = q.id;
  },
  async onShow() {
    await base.onShow.call(this);
    const g = this._generation;
    try {
      const lab = await api.lab(this.id);
      if (g === this._generation) this.setData({ lab });
    } catch (e) {
      if (g === this._generation) error(this, e);
    }
  },
  input(e) {
    this.setData({ keyword: e.detail.value });
  },
  search() {
    this.load(true);
  },
  category(e) {
    const index = Number(e.detail.value);
    this.setData({
      categoryIndex: index,
      category: this.data.categoryValues[index],
    });
    this.load(true);
  },
  open(e) {
    wx.navigateTo({
      url: "/pages/equipment/index?id=" + e.currentTarget.dataset.id,
    });
  },
});
