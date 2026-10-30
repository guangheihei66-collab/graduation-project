export function shanghaiDate(now = new Date()): string {
  const parts = new Intl.DateTimeFormat("en", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const value = (type: string): string =>
    parts.find((part) => part.type === type)?.value ?? "";
  return `${value("year")}-${value("month")}-${value("day")}`;
}
export function addDays(day: string, count: number): string {
  const date = new Date(`${day}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + count);
  return date.toISOString().slice(0, 10);
}
export function validRange(
  range: readonly string[] | null | undefined,
  max = Number.POSITIVE_INFINITY,
): boolean {
  if (!range || range.length !== 2 || !range[0] || !range[1]) return false;
  const [start, end] = range;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(start) || !/^\d{4}-\d{2}-\d{2}$/.test(end))
    return false;
  const a = Date.parse(`${start}T00:00:00Z`),
    b = Date.parse(`${end}T00:00:00Z`);
  return (
    Number.isFinite(a) &&
    Number.isFinite(b) &&
    new Date(a).toISOString().slice(0, 10) === start &&
    new Date(b).toISOString().slice(0, 10) === end &&
    b >= a &&
    (b - a) / 86400000 + 1 <= max
  );
}
export function dateTime(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : new Intl.DateTimeFormat("zh-CN", {
        timeZone: "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
      }).format(date);
}
