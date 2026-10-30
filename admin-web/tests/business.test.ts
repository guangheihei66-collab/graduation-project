import { describe, it, expect } from "vitest";
import {
  roles,
  categories,
  equipmentStatuses,
  reservationStatuses,
  allowedActions,
  validPassword,
  validReason,
  changedFields,
  nullableText,
} from "../src/utils/business";
import { shanghaiDate, addDays, validRange, dateTime } from "../src/utils/date";
import type { LabEditable, Reservation } from "../src/types";
const booking: Pick<Reservation, "status" | "startTime" | "endTime"> = {
  status: "PENDING",
  startTime: "2026-11-02T09:00:00+08:00",
  endTime: "2026-11-02T10:00:00+08:00",
};
describe("业务契约", () => {
  it("状态及角色映射为中文", () => {
    expect(roles.SYS_ADMIN).toBe("系统管理员");
    expect(categories.INSTRUMENT).toBe("仪器");
    expect(equipmentStatuses.MAINTENANCE).toBe("维修");
    expect(reservationStatuses.EXPIRED).toBe("已过期");
  });
  it("系统管理员和学生没有预约审批按钮", () => {
    expect(allowedActions("SYS_ADMIN", booking, 0)).toEqual([]);
    expect(allowedActions("STUDENT", booking, 0)).toEqual([]);
  });
  it("开始前的待审批只允许同意及驳回", () => {
    expect(
      allowedActions("LAB_ADMIN", booking, Date.parse(booking.startTime) - 1),
    ).toEqual(["APPROVE", "REJECT"]);
    expect(
      allowedActions("LAB_ADMIN", booking, Date.parse(booking.startTime)),
    ).toEqual([]);
  });
  it("已通过的撤销和完成遵守时间边界", () => {
    const row = { ...booking, status: "APPROVED" as const };
    expect(allowedActions("LAB_ADMIN", row, 0)).toEqual(["REVOKE"]);
    expect(allowedActions("LAB_ADMIN", row, Date.parse(row.startTime))).toEqual(
      [],
    );
    expect(allowedActions("LAB_ADMIN", row, Date.parse(row.endTime))).toEqual([
      "COMPLETE",
    ]);
  });
  it("终态不显示操作", () => {
    for (const status of [
      "CANCELLED",
      "REJECTED",
      "EXPIRED",
      "COMPLETED",
    ] as const)
      expect(allowedActions("LAB_ADMIN", { ...booking, status }, 0)).toEqual(
        [],
      );
  });
  it("密码验证遵守字符数和 UTF-8 字节限制", () => {
    expect(validPassword("abcdefgh")).toBe(true);
    expect(validPassword("a".repeat(64))).toBe(true);
    expect(validPassword("密".repeat(25))).toBe(false);
    expect(validPassword("short")).toBe(false);
    expect(validPassword(" ".repeat(8))).toBe(false);
  });
  it("拒绝空白或过长原因", () => {
    expect(validReason("设备维护")).toBe(true);
    expect(validReason("  ")).toBe(false);
    expect(validReason("原".repeat(501))).toBe(false);
  });
  it("PATCH 只发送变化字段，清空可空字段使用 null", () => {
    const before: LabEditable = {
      name: "实验室",
      location: "一楼",
      description: "旧描述",
    };
    const after = { ...before, description: nullableText("") };
    expect(
      changedFields(before, after, ["name", "location", "description"]),
    ).toEqual({ description: null });
    expect(changedFields(before, before, ["name"])).toEqual({});
  });
  it("北京时间自然日不依赖浏览器时区", () => {
    expect(shanghaiDate(new Date("2026-10-29T18:30:00Z"))).toBe("2026-10-30");
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28");
    expect(dateTime("2026-11-02T09:00:00+08:00")).toContain("09:00");
  });
  it("统计范围包含两端最多90天，拒绝无效日期", () => {
    expect(validRange(["2026-01-01", "2026-03-31"], 90)).toBe(true);
    expect(validRange(["2026-01-01", "2026-04-01"], 90)).toBe(false);
    expect(validRange(["2026-02-30", "2026-03-01"], 90)).toBe(false);
    expect(validRange(["2026-11-02", "2026-11-01"])).toBe(false);
  });
  it("日期控件清空或输入不合法时不会抛出异常", () => {
    expect(validRange(null, 90)).toBe(false);
    expect(validRange(undefined, 90)).toBe(false);
  });
});
