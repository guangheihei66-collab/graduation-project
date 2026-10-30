import type { Role, Reservation, ReservationAction } from "../types";
export const roles = {
  SYS_ADMIN: "系统管理员",
  LAB_ADMIN: "实验室管理员",
  STUDENT: "学生",
};
export const categories = {
  INSTRUMENT: "仪器",
  COMPUTER: "计算机",
  TOOL: "实验工具",
  OTHER: "其他",
};
export const equipmentStatuses = {
  AVAILABLE: "可用",
  MAINTENANCE: "维修",
  DISABLED: "停用",
};
export const reservationStatuses = {
  PENDING: "待审批",
  APPROVED: "已通过",
  REJECTED: "已驳回",
  CANCELLED: "已取消",
  EXPIRED: "已过期",
  COMPLETED: "已完成",
};
export const actionLabels = {
  CREATE: "提交预约",
  APPROVE: "同意",
  REJECT: "驳回",
  CANCEL: "学生取消",
  REVOKE: "管理员撤销",
  EXPIRE: "系统过期",
  COMPLETE: "完成登记",
};
export function allowedActions(
  role: Role | undefined,
  row: Pick<Reservation, "status" | "startTime" | "endTime">,
  now = Date.now(),
): ReservationAction[] {
  if (role !== "LAB_ADMIN") return [];
  if (row.status === "PENDING" && now < Date.parse(row.startTime))
    return ["APPROVE", "REJECT"];
  if (row.status === "APPROVED" && now < Date.parse(row.startTime))
    return ["REVOKE"];
  if (row.status === "APPROVED" && now >= Date.parse(row.endTime))
    return ["COMPLETE"];
  return [];
}
export const validPassword = (value: string): boolean =>
  !/^\s*$/.test(value) &&
  value.length >= 8 &&
  value.length <= 64 &&
  new TextEncoder().encode(value).length <= 72;
export const validReason = (value: string): boolean =>
  value.trim().length > 0 && value.length <= 500;
export const nullableText = (value: string): string | null =>
  value === "" ? null : value;
export function changedFields<T extends object>(
  before: T,
  after: T,
  keys: readonly (keyof T)[],
): Partial<T> {
  const result: Partial<T> = {};
  for (const key of keys)
    if (before[key] !== after[key]) result[key] = after[key];
  return result;
}
