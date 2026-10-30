const DAY = 86400000;
let offset = 0;
const pad = (v) => String(v).padStart(2, "0");
function sync(header) {
  const text = header && (header.Date || header.date);
  const value = Date.parse(text);
  if (Number.isFinite(value)) offset = value + 1000 - Date.now();
}
function now() {
  return Date.now() + offset;
}
function date(value = now()) {
  return new Date(value + 8 * 3600000).toISOString().slice(0, 10);
}
function addDays(day, days) {
  return new Date(
    Date.parse(day + "T00:00:00+08:00") + days * DAY + 8 * 3600000,
  )
    .toISOString()
    .slice(0, 10);
}
function iso(day, minute) {
  return `${day}T${pad(Math.floor(minute / 60))}:${pad(minute % 60)}:00+08:00`;
}
function label(minute) {
  return `${pad(Math.floor(minute / 60))}:${pad(minute % 60)}`;
}
function format(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "—";
}
function valid(day, start, end, occupied = [], current = now()) {
  const begin = Date.parse(iso(day, start));
  const finish = Date.parse(iso(day, end));
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(day) ||
    (Number.isFinite(begin) && date(begin) !== day) ||
    !Number.isFinite(begin) ||
    !Number.isFinite(finish) ||
    !Number.isInteger(start) ||
    !Number.isInteger(end)
  )
    return "请选择正确的日期和时间";
  if (start < 480 || end > 1320 || start % 30 || end % 30)
    return "开放时间为08:00—22:00，按半小时选择";
  if (end - start < 30 || end - start > 240) return "单次预约为30分钟至4小时";
  if (begin <= current) return "开始时间必须晚于当前时间";
  if (begin > current + 7 * DAY) return "开始时间不能晚于未来7天";
  if (
    occupied.some(
      (s) => Date.parse(s.startTime) < finish && Date.parse(s.endTime) > begin,
    )
  )
    return "所选区间包含已占用时段";
  return "";
}
function cells(day, occupied = [], selected = [], current = now()) {
  return Array.from({ length: 28 }, (_, i) => {
    const minute = 480 + i * 30;
    const start = Date.parse(iso(day, minute));
    const end = start + 1800000;
    const used = occupied.some(
      (s) => Date.parse(s.startTime) < end && Date.parse(s.endTime) > start,
    );
    const past = start <= current || start > current + 7 * DAY;
    return {
      minute,
      label: label(minute),
      blocked: used || past,
      state: used
        ? "used"
        : past
          ? "past"
          : selected.length && minute >= selected[0] && minute < selected[1]
            ? "selected"
            : "free",
    };
  });
}
module.exports = { date, addDays, iso, label, format, valid, cells, now, sync };
