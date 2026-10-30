import {
  test,
  expect,
  type Page,
  type APIRequestContext,
} from "@playwright/test";
import { randomUUID, createHmac } from "node:crypto";
import { execFileSync } from "node:child_process";
import type {
  ApiResponse,
  Lab,
  Equipment,
  Reservation,
  ManagedUser,
} from "../src/types";
import { shanghaiDate, addDays } from "../src/utils/date";
// 仅连接显式指定的隔离开发服务；测试密钥不进入 Vite 或生产模块。
const baseUser = process.env.E2E_SYS_USERNAME;
const basePassword = process.env.E2E_SYS_PASSWORD;
const jwtSecret = process.env.E2E_TEST_JWT_SECRET;
let sysToken = "",
  labToken = "",
  labUsername = "",
  labPassword = "";
let laboratory: Lab, equipment: Equipment, otherLab: Lab;
let approveId = "",
  rejectId = "",
  revokeId = "",
  completeId = "";
const prefix = `浏览器联调-${randomUUID().slice(0, 8)}`;
async function api<T>(
  request: APIRequestContext,
  method: string,
  path: string,
  token: string,
  data?: unknown,
): Promise<T> {
  const response = await request.fetch(`/api/v1${path}`, {
    method,
    headers: { Authorization: `Bearer ${token}` },
    data,
  });
  expect(response.status()).toBeLessThan(300);
  const result = (await response.json()) as ApiResponse<T>;
  expect(result.code).toBe("OK");
  return result.data;
}
async function login(
  page: Page,
  username: string,
  password: string,
): Promise<void> {
  await page.goto("/login");
  await page.getByLabel("账号", { exact: true }).fill(username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  await page.getByRole("button", { name: "登录管理后台", exact: true }).click();
  await expect(page).toHaveURL(/dashboard/);
  await expect(
    page.getByRole("heading", { name: "统计看板", exact: true }),
  ).toBeVisible();
}
function row(page: Page, value: string) {
  return page
    .getByRole("row")
    .filter({ has: page.getByRole("cell", { name: value, exact: true }) });
}
async function confirm(page: Page, title = "请确认操作"): Promise<void> {
  await page
    .getByRole("dialog", { name: title, exact: true })
    .getByRole("button", { name: "确认", exact: true })
    .click();
}
async function processReservation(
  page: Page,
  id: string,
  action: string,
  reason?: string,
): Promise<void> {
  await row(page, id)
    .getByRole("button", { name: action, exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: action, exact: true });
  if (reason) await dialog.getByPlaceholder("请填写明确原因").fill(reason);
  await dialog.getByRole("button", { name: "提交处理" }).click();
  const refreshed = page.waitForResponse(
    (response) =>
      response.url().includes("/statistics/overview") &&
      response.status() === 200,
  );
  await confirm(page, "确认预约处理");
  await refreshed;
  await expect(dialog).not.toBeVisible();
}
test.describe.configure({ mode: "serial" });
test.beforeAll(async ({ request }) => {
  if (!baseUser || !basePassword || !jwtSecret)
    throw new Error(
      "真实联调需要 E2E_SYS_USERNAME / E2E_SYS_PASSWORD / E2E_TEST_JWT_SECRET，仅在隔离环境提供",
    );
  const response = await request.post("/api/v1/auth/login", {
    data: { username: baseUser, password: basePassword },
  });
  expect(response.status()).toBe(200);
  sysToken = ((await response.json()) as ApiResponse<{ accessToken: string }>)
    .data.accessToken;
  labUsername = `e2e_lab_${randomUUID().slice(0, 8)}`;
  labPassword = randomUUID();
  const labUser = await api<ManagedUser>(request, "POST", "/users", sysToken, {
    username: labUsername,
    realName: prefix + "管理员",
    role: "LAB_ADMIN",
    initialPassword: labPassword,
  });
  const labLogin = await request.post("/api/v1/auth/login", {
    data: { username: labUsername, password: labPassword },
  });
  labToken = ((await labLogin.json()) as ApiResponse<{ accessToken: string }>)
    .data.accessToken;
  laboratory = await api<Lab>(request, "POST", "/labs", sysToken, {
    code: `E2E-${randomUUID().slice(0, 8)}`,
    name: prefix + "授权实验室",
    location: "隔离测试楼",
  });
  otherLab = await api<Lab>(request, "POST", "/labs", sysToken, {
    code: `E2E-${randomUUID().slice(0, 8)}`,
    name: prefix + "未授权实验室",
    location: "隔离测试楼",
  });
  await api(request, "PUT", `/labs/${laboratory.id}/admins`, sysToken, {
    userIds: [labUser.id],
  });
  equipment = await api<Equipment>(request, "POST", "/equipment", sysToken, {
    labId: laboratory.id,
    assetNo: `E2E-${randomUUID().slice(0, 8)}`,
    name: prefix + "预约设备",
    category: "TOOL",
    location: "测试台",
  });
  const student = await api<ManagedUser>(request, "POST", "/users", sysToken, {
    username: `e2e_s_${randomUUID().slice(0, 8)}`,
    realName: prefix + "学生",
    role: "STUDENT",
    initialPassword: randomUUID(),
  });
  const now = Math.floor(Date.now() / 1000),
    header = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString(
      "base64url",
    ),
    payload = Buffer.from(
      JSON.stringify({
        sub: student.id,
        iat: now,
        exp: now + 1800,
        jti: randomUUID(),
      }),
    ).toString("base64url");
  const signing = `${header}.${payload}`,
    token = `${signing}.${createHmac("sha256", jwtSecret).update(signing).digest("base64url")}`;
  const day = addDays(shanghaiDate(), 1);
  for (const [index, hour] of [9, 10, 11].entries()) {
    const result = await request.post("/api/v1/reservations", {
      headers: {
        Authorization: `Bearer ${token}`,
        "Idempotency-Key": randomUUID(),
      },
      data: {
        equipmentId: equipment.id,
        startTime: `${day}T${String(hour).padStart(2, "0")}:00:00+08:00`,
        endTime: `${day}T${String(hour + 1).padStart(2, "0")}:00:00+08:00`,
        purpose: prefix + ["同意样本", "驳回样本", "撤销样本"][index],
      },
    });
    expect(result.status()).toBe(201);
    const reservation = ((await result.json()) as ApiResponse<Reservation>)
      .data;
    if (index === 0) approveId = reservation.id;
    else if (index === 1) rejectId = reservation.id;
    else revokeId = reservation.id;
  }
  await api(request, "POST", `/reservations/${revokeId}/review`, labToken, {
    decision: "APPROVE",
  });
  const container = process.env.E2E_MYSQL_CONTAINER;
  if (container) {
    const yesterday = addDays(shanghaiDate(), -1),
      key = randomUUID();
    // 只向显式指定的隔离库准备已结束预约及真实结构日志，以验证完成登记；不改后端时钟或生产接口。
    const sql = `INSERT INTO reservation(equipment_id,student_id,start_time,end_time,purpose,status,idempotency_key,request_hash,created_at,updated_at) VALUES (${equipment.id},${student.id},'${yesterday} 09:00:00','${yesterday} 10:00:00','${prefix}完成样本','APPROVED','${key}',REPEAT('c',64),'${yesterday} 08:00:00','${yesterday} 08:10:00'); SET @rid=LAST_INSERT_ID(); INSERT INTO reservation_log(reservation_id,operator_id,action,from_status,to_status,created_at) VALUES (@rid,${student.id},'CREATE',NULL,'PENDING','${yesterday} 08:00:00'),(@rid,${labUser.id},'APPROVE','PENDING','APPROVED','${yesterday} 08:10:00'); SELECT @rid;`;
    completeId = execFileSync(
      "docker",
      [
        "exec",
        container,
        "sh",
        "-c",
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names -e "$1"',
        "--",
        sql,
      ],
      { encoding: "utf8" },
    ).trim();
    expect(completeId).toMatch(/^\d+$/);
  }
});
test("系统管理员完成实验室、授权、设备和用户真实操作", async ({
  page,
  request,
}) => {
  await login(page, baseUser!, basePassword!);
  await expect(
    page.getByRole("navigation").getByText("用户管理"),
  ).toBeVisible();
  await page.getByRole("link", { name: "实验室管理", exact: true }).click();
  await page.getByRole("button", { name: "新增实验室" }).click();
  let dialog = page.getByRole("dialog", { name: "新增实验室" });
  const code = `UI-${randomUUID().slice(0, 8)}`,
    name = prefix + "界面实验室";
  await dialog.getByLabel("实验室编号", { exact: true }).fill(code);
  await dialog.getByLabel("实验室名称", { exact: true }).fill(name);
  await dialog.getByLabel("地点", { exact: true }).fill("前端创建地点");
  await dialog.getByLabel("描述", { exact: true }).fill("原描述");
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect(row(page, code)).toBeVisible();
  await row(page, code)
    .getByRole("button", { name: "编辑", exact: true })
    .click();
  dialog = page.getByRole("dialog", { name: "编辑实验室" });
  await expect(dialog.getByLabel("实验室编号", { exact: true })).toBeDisabled();
  await dialog.getByLabel("描述", { exact: true }).fill("");
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect(dialog).not.toBeVisible();
  const list = await api<{ records: Lab[] }>(
    request,
    "GET",
    `/labs?name=${encodeURIComponent(name)}`,
    sysToken,
  );
  const created = list.records[0]!;
  expect(created.description).toBeNull();
  await row(page, code).getByRole("button", { name: "分配管理员" }).click();
  dialog = page.getByRole("dialog", { name: "分配实验室管理员" });
  await expect(dialog.getByText(/整体替换/)).toBeVisible();
  await dialog.getByRole("combobox").focus();
  await dialog.getByRole("combobox").press("ArrowDown");
  await page
    .getByRole("option", {
      name: `${prefix}管理员（${labUsername}）`,
      exact: true,
    })
    .click();
  await dialog.getByRole("heading", { name: "分配实验室管理员" }).click();
  await dialog.getByRole("button", { name: "替换授权" }).click();
  await confirm(page, "替换实验室授权");
  await expect(dialog).not.toBeVisible();
  await page.getByRole("link", { name: "设备管理", exact: true }).click();
  await page.getByRole("button", { name: "新增设备" }).click();
  dialog = page.getByRole("dialog", { name: "新增设备" });
  await dialog.getByLabel("所属实验室", { exact: true }).focus();
  await dialog.getByLabel("所属实验室", { exact: true }).press("ArrowDown");
  await page.getByRole("option", { name, exact: true }).click();
  const asset = `UI-EQ-${randomUUID().slice(0, 8)}`;
  await dialog.getByLabel("资产编号", { exact: true }).fill(asset);
  await dialog
    .getByLabel("设备名称", { exact: true })
    .fill(prefix + "界面设备");
  await dialog.getByLabel("型号", { exact: true }).fill("原型号");
  await dialog.getByLabel("存放位置", { exact: true }).fill("测试台");
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect(row(page, asset)).toBeVisible();
  await row(page, asset)
    .getByRole("button", { name: "编辑", exact: true })
    .click();
  dialog = page.getByRole("dialog", { name: "编辑设备" });
  await expect(dialog.getByLabel("资产编号", { exact: true })).toBeDisabled();
  await expect(dialog.getByLabel("所属实验室", { exact: true })).toBeDisabled();
  await dialog.getByLabel("型号", { exact: true }).fill("");
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect(dialog).not.toBeVisible();
  await row(page, asset).getByRole("button", { name: "变更状态" }).click();
  await page.getByRole("menuitem", { name: "设为维修" }).click();
  await confirm(page);
  await expect(
    row(page, asset).getByText("维修", { exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "用户管理", exact: true }).click();
  await page.getByRole("button", { name: "创建用户" }).click();
  dialog = page.getByRole("dialog", { name: "创建用户" });
  const username = `ui_user_${randomUUID().slice(0, 8)}`;
  await dialog.getByLabel("账号 / 学号", { exact: true }).fill(username);
  await dialog.getByLabel("姓名", { exact: true }).fill(prefix + "新用户");
  await dialog.getByLabel("角色", { exact: true }).focus();
  await dialog.getByLabel("角色", { exact: true }).press("ArrowDown");
  await page.getByRole("option", { name: "实验室管理员", exact: true }).click();
  await dialog.getByLabel("初始密码", { exact: true }).fill(randomUUID());
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect(row(page, username)).toBeVisible();
  await row(page, username)
    .getByRole("button", { name: "编辑", exact: true })
    .click();
  dialog = page.getByRole("dialog", { name: "编辑用户" });
  await dialog.getByLabel("姓名", { exact: true }).fill(prefix + "改名用户");
  await dialog.getByRole("button", { name: "保存", exact: true }).click();
  await expect(
    row(page, username).getByText(prefix + "改名用户"),
  ).toBeVisible();
  await row(page, username)
    .getByRole("button", { name: "重置密码", exact: true })
    .click();
  dialog = page.getByRole("dialog", { name: "重置用户密码" });
  const newPassword = randomUUID();
  await dialog.getByLabel("新密码", { exact: true }).fill(newPassword);
  await dialog.getByRole("button", { name: "确认重置" }).click();
  await confirm(page);
  await expect(dialog).not.toBeVisible();
  expect(
    (
      await request.post("/api/v1/auth/login", {
        data: { username, password: newPassword },
      })
    ).status(),
  ).toBe(200);
  await row(page, username)
    .getByRole("button", { name: "停用", exact: true })
    .click();
  await confirm(page);
  await expect(
    row(page, username).getByText("停用", { exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "预约管理", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "同意", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: "统计看板", exact: true }).click();
  await expect(page.locator("canvas").first()).toBeVisible();
  await page.screenshot({ path: "/tmp/stage4-dashboard.png", fullPage: true });
});
test("实验室管理员仅操作授权范围，并完成预约状态流转", async ({
  page,
  request,
}) => {
  await login(page, labUsername, labPassword);
  await expect(page.getByRole("navigation").getByText("用户管理")).toHaveCount(
    0,
  );
  await page.goto("/users");
  await expect(page).toHaveURL(/forbidden/);
  await page.getByRole("link", { name: "实验室管理", exact: true }).click();
  await expect(page.getByRole("button", { name: "新增实验室" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "分配管理员" })).toHaveCount(0);
  await expect(
    page.getByRole("cell", { name: otherLab.name, exact: true }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: "预约管理", exact: true }).click();
  await processReservation(page, approveId, "同意");
  await expect(
    row(page, approveId).getByText("已通过", { exact: true }),
  ).toBeVisible();
  await row(page, rejectId)
    .getByRole("button", { name: "驳回", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "驳回", exact: true });
  await dialog.getByRole("button", { name: "提交处理" }).click();
  await expect(dialog.getByText("请填写 1—500 字的处理原因")).toBeVisible();
  await dialog.getByRole("button", { name: "取消", exact: true }).click();
  await processReservation(page, rejectId, "驳回", "用途说明不完整");
  await processReservation(page, revokeId, "管理员撤销", "设备计划维护");
  expect(
    (
      await api<Reservation>(
        request,
        "GET",
        `/reservations/${rejectId}`,
        labToken,
      )
    ).status,
  ).toBe("REJECTED");
  expect(
    (
      await api<Reservation>(
        request,
        "GET",
        `/reservations/${revokeId}`,
        labToken,
      )
    ).status,
  ).toBe("CANCELLED");
  await row(page, approveId)
    .getByRole("button", { name: "详情", exact: true })
    .click();
  await expect(page.getByText("操作日志", { exact: true })).toBeVisible();
});
test("已结束的真实结构预约可以完成登记", async ({ page, request }) => {
  test.skip(!completeId, "未提供隔离历史样本，完成登记需单独准备");
  await login(page, labUsername, labPassword);
  await page.getByRole("link", { name: "预约管理", exact: true }).click();
  await processReservation(page, completeId, "完成登记");
  expect(
    (
      await api<Reservation>(
        request,
        "GET",
        `/reservations/${completeId}`,
        labToken,
      )
    ).status,
  ).toBe("COMPLETED");
});
test("移动端导航、退出、错误登录和404真实渲染", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, labUsername, labPassword);
  await page.getByRole("button", { name: "展开导航" }).click();
  await expect(
    page.getByRole("navigation").getByText("设备管理"),
  ).toBeVisible();
  await page.getByRole("link", { name: "设备管理", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "设备管理", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.getByRole("button", { name: "退出登录" }).click();
  await expect(page).toHaveURL(/login/);
  expect(
    await page.evaluate(() => sessionStorage.getItem("lab-admin-token")),
  ).toBeNull();
  await page.getByLabel("账号", { exact: true }).fill(labUsername);
  await page.getByLabel("密码", { exact: true }).fill("invalid-test-password");
  await page.getByRole("button", { name: "登录管理后台" }).click();
  await expect(
    page.getByText("账号凭据不正确或会话已失效，请重新登录"),
  ).toBeVisible();
  await page.goto("/missing-page");
  await expect(page.getByRole("heading", { name: "页面未找到" })).toBeVisible();
});
