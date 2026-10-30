const api = require("../../services/api");
const p = require("../../utils/page");
const { statuses } = require("../../utils/business");
const base = p.list(
  api.mine,
  function () {
    return {
      status: this.data.status || undefined,
      fromDate: this.data.date || undefined,
      toDate: this.data.date || undefined,
      labId: this.data.labId || undefined,
      equipmentId: this.data.equipmentId || undefined,
    };
  },
  p.decorate,
);
Page({
  ...base,
  data: {
    ...base.data,
    status: "",
    statusIndex: 0,
    statusNames: ["全部状态", ...Object.values(statuses)],
    statusValues: ["", ...Object.keys(statuses)],
    date: "",
    labId: "",
    equipmentId: "",
  },
  status(e) {
    const index = Number(e.detail.value);
    this.setData({ statusIndex: index, status: this.data.statusValues[index] });
    this.load(true);
  },
  date(e) {
    this.setData({ date: e.detail.value });
    this.load(true);
  },
  input(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },
  search() {
    if (
      [this.data.labId, this.data.equipmentId].some(
        (s) => s && !/^[1-9][0-9]{0,18}$/.test(s),
      )
    ) {
      this.setData({ error: "实验室与设备标识须为正整数字符串" });
      return;
    }
    this.load(true);
  },
  clear() {
    this.setData({
      status: "",
      statusIndex: 0,
      date: "",
      labId: "",
      equipmentId: "",
    });
    this.load(true);
  },
  open(e) {
    wx.navigateTo({
      url: "/pages/reservation/index?id=" + e.currentTarget.dataset.id,
    });
  },
});
