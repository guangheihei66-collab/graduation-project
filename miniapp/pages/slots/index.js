const api = require("../../services/api");
const p = require("../../utils/page");
const time = require("../../utils/time");
const submission = require("../../services/submission");
Page({
  data: {
    equipment: null,
    date: time.date(),
    minDate: time.date(),
    maxDate: time.addDays(time.date(), 7),
    cells: [],
    occupied: [],
    start: null,
    end: null,
    selection: "请选择起始半小时，再点结束半小时",
    purpose: "",
    loading: false,
    busy: false,
    error: "",
  },
  onLoad(q) {
    this.id = q.id;
    const d = submission.read();
    if (d && d.body.equipmentId === q.id)
      this.setData({
        date: d.body.startTime.slice(0, 10),
        purpose: d.body.purpose,
      });
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
    this.setData({
      loading: true,
      error: "",
      start: null,
      end: null,
      selection: "请选择起始半小时，再点结束半小时",
      cells: [],
    });
    try {
      const [equipment, slots] = await Promise.all([
        api.equipment(this.id),
        api.slots(this.id, this.data.date),
      ]);
      if (g === this._generation)
        this.setData({
          equipment,
          occupied: slots.slots,
          cells: time.cells(this.data.date, slots.slots),
          minDate: time.date(),
          maxDate: time.addDays(time.date(), 7),
        });
    } catch (e) {
      if (g === this._generation) p.error(this, e);
    } finally {
      if (g === this._generation) this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },
  date(e) {
    this.setData({ date: e.detail.value });
    this.load();
  },
  purpose(e) {
    this.setData({ purpose: e.detail.value });
  },
  select(e) {
    const minute = Number(e.currentTarget.dataset.minute);
    const cell = this.data.cells.find((c) => c.minute === minute);
    if (!cell || cell.blocked || this.data.loading) return;
    let start = this.data.start,
      end = this.data.end;
    if (start === null || end !== null || minute < start) {
      start = minute;
      end = null;
    } else end = minute + 30;
    const error =
      end === null
        ? ""
        : time.valid(this.data.date, start, end, this.data.occupied);
    this.setData({
      start,
      end,
      error,
      selection:
        end === null
          ? time.label(start) + " 开始，请再点击结束时段"
          : time.label(start) +
            "—" +
            time.label(end) +
            " · " +
            (end - start) +
            "分钟",
      cells: time.cells(this.data.date, this.data.occupied, [
        start,
        end === null ? start + 30 : end,
      ]),
    });
  },
  async next() {
    if (this.data.busy || this.data.loading) return;
    let error =
      this.data.start === null || this.data.end === null
        ? "请完成开始和结束时段选择"
        : time.valid(
            this.data.date,
            this.data.start,
            this.data.end,
            this.data.occupied,
          );
    const purpose = this.data.purpose.trim();
    if (!error && (!purpose || purpose.length > 500))
      error = "请输入1—500字符预约用途";
    if (error) {
      this.setData({ error });
      return;
    }
    this.setData({ busy: true, error: "" });
    try {
      await submission.prepare(
        {
          equipmentId: this.id,
          startTime: time.iso(this.data.date, this.data.start),
          endTime: time.iso(this.data.date, this.data.end),
          purpose,
        },
        this.data.equipment.name,
      );
      wx.navigateTo({ url: "/pages/confirm/index" });
    } catch (e) {
      p.error(this, e);
    } finally {
      this.setData({ busy: false });
    }
  },
});
