const { test } = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const root = require("node:path").resolve(__dirname, "..");
let storage, queue, calls, navigation, modal, loginResult, randomCount;
function fresh() {
  for (const key of Object.keys(require.cache))
    if (key.startsWith(root) && !key.includes("/tests/"))
      delete require.cache[key];
  storage = new Map();
  queue = [];
  calls = [];
  navigation = [];
  modal = true;
  loginResult = { code: "unit-code-only" };
  randomCount = 0;
  global.wx = {
    getStorageSync: (k) => storage.get(k),
    setStorageSync: (k, v) => storage.set(k, structuredClone(v)),
    removeStorageSync: (k) => storage.delete(k),
    getAccountInfoSync: () => ({ miniProgram: { envVersion: "develop" } }),
    request(o) {
      calls.push(o);
      const next = queue.shift();
      if (!next) throw new Error("缺少测试响应");
      if (next instanceof Error) o.fail({ errMsg: next.message });
      else if (typeof next === "function") next(o);
      else
        o.success({
          statusCode: next.status || 200,
          data: { code: next.code || "OK", data: next.data },
          header: {},
        });
    },
    reLaunch: (o) => {
      navigation.push(o.url);
      if (o.complete) o.complete();
    },
    switchTab: (o) => navigation.push(o.url),
    navigateTo: (o) => navigation.push(o.url),
    redirectTo: (o) => navigation.push(o.url),
    navigateBack: () => navigation.push("back"),
    login: (o) => o.success(loginResult),
    stopPullDownRefresh() {},
    showToast() {},
    showModal: (o) => o.success({ confirm: modal }),
    getRandomValues: (o) => {
      randomCount++;
      const b = crypto.randomBytes(16);
      o.success({
        randomValues: b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength),
      });
    },
  };
}
function session() {
  return require("../utils/session");
}
function student() {
  const user = {
    id: "9223372036854775806",
    username: "unit-student",
    role: "STUDENT",
    realName: "测试学生",
  };
  session().save({ user, accessToken: "unit-token" });
  return user;
}
function page(name) {
  let definition;
  global.Page = (p) => {
    definition = p;
  };
  require(`../pages/${name}/index.js`);
  const instance = {
    ...definition,
    data: structuredClone(definition.data),
    setData(values) {
      Object.assign(this.data, values);
    },
  };
  for (const [key, value] of Object.entries(definition))
    if (typeof value === "function") instance[key] = value.bind(instance);
  return instance;
}
const body = () => ({
  equipmentId: "9223372036854775805",
  startTime: "2026-11-02T09:00:00+08:00",
  endTime: "2026-11-02T10:00:00+08:00",
  purpose: "  课程实验  ",
});
const reservation = () => ({
  id: "24",
  studentId: session().user().id,
  equipmentName: "示波器",
  startTime: new Date(Date.now() + 86400000).toISOString(),
  endTime: new Date(Date.now() + 90000000).toISOString(),
  status: "PENDING",
  logs: [],
});
test("解析200统一响应并附带JWT", async () => {
  fresh();
  student();
  queue.push({ data: { id: "9223372036854775805" } });
  const result = await require("../services/api").equipment(
    "9223372036854775805",
  );
  assert.equal(result.id, "9223372036854775805");
  assert.equal(calls[0].header.Authorization, "Bearer unit-token");
});
test("解析201真实创建结果", async () => {
  fresh();
  student();
  queue.push({ status: 201, data: { id: "1" } });
  assert.equal(
    (await require("../services/api").create(body(), "test-key")).id,
    "1",
  );
});
test("401清除会话并跳转登录", async () => {
  fresh();
  student();
  queue.push({ status: 401, code: "UNAUTHORIZED" });
  await assert.rejects(require("../services/api").me());
  assert.equal(session().token(), "");
  assert.equal(navigation[0], "/pages/login/index");
});
test("403保留有效会话", async () => {
  fresh();
  student();
  queue.push({ status: 403, code: "FORBIDDEN" });
  await assert.rejects(require("../services/api").me());
  assert.equal(session().token(), "unit-token");
  assert.equal(navigation.length, 0);
});
test("409根据冲突码显示中文", async () => {
  fresh();
  queue.push({ status: 409, code: "TIME_CONFLICT" });
  await assert.rejects(
    require("../services/api").create(body(), "key"),
    (e) => e.status === 409 && e.message.includes("重新选择"),
  );
});
test("超时不伪造成功或自动重试", async () => {
  fresh();
  queue.push(new Error("request:fail timeout"));
  await assert.rejects(require("../services/api").me(), /请求超时/);
  assert.equal(calls.length, 1);
});
test("503保留会话且不自动重试", async () => {
  fresh();
  student();
  queue.push({ status: 503 });
  await assert.rejects(require("../services/api").me(), /服务暂不可用/);
  assert.equal(session().token(), "unit-token");
  assert.equal(calls.length, 1);
});
test("已绑定微信登录保存学生令牌", async () => {
  fresh();
  const u = { id: "1", role: "STUDENT" };
  queue.push({
    data: { needBind: false, accessToken: "student-token", user: u },
  });
  assert.equal(await require("../services/auth").login(), "home");
  assert.equal(session().token(), "student-token");
  assert.deepEqual(calls[0].data, { code: "unit-code-only" });
  assert.equal(calls[0].header.Authorization, undefined);
});
test("未绑定微信只在内存保存票据，不进入URL和存储", async () => {
  fresh();
  queue.push({
    data: { needBind: true, bindTicket: "unit-ticket", expiresIn: 300 },
  });
  assert.equal(await require("../services/auth").login(), "bind");
  assert.equal(session().ticket(), "unit-ticket");
  assert.equal(storage.size, 0);
  assert.ok(!calls[0].url.includes("unit-ticket"));
});
test("绑定通过POST提交票据和密码并清空票据", async () => {
  fresh();
  session().setTicket("unit-ticket");
  queue.push({
    data: { accessToken: "new-token", user: { id: "1", role: "STUDENT" } },
  });
  await require("../services/auth").bind("student", "test-password");
  assert.equal(calls[0].data.bindTicket, "unit-ticket");
  assert.equal(session().ticket(), "");
  assert.ok(
    ![...storage.values()].some((v) =>
      JSON.stringify(v).includes("test-password"),
    ),
  );
});
test("微信没有返回code时拒绝请求", async () => {
  fresh();
  loginResult = {};
  await assert.rejects(require("../services/auth").login(), /未返回/);
  assert.equal(calls.length, 0);
});
test("拒绝管理员会话", () => {
  fresh();
  assert.throws(
    () =>
      session().save({
        accessToken: "token",
        user: { id: "1", role: "SYS_ADMIN" },
      }),
    /仅学生/,
  );
  assert.equal(session().token(), "");
});
test("退出清除令牌、用户、票据和草稿", () => {
  fresh();
  student();
  session().setTicket("ticket");
  storage.set("lab-reservation-draft", {});
  require("../services/auth").logout();
  assert.equal(storage.size, 0);
  assert.equal(session().ticket(), "");
});
test("启动身份恢复调用me，网络失败保留令牌", async () => {
  fresh();
  student();
  queue.push(new Error("network"));
  await assert.rejects(require("../services/auth").restore());
  assert.equal(session().token(), "unit-token");
  assert.ok(calls[0].url.endsWith("/auth/me"));
});
test("绑定账号密码边界校验", () => {
  fresh();
  const { bindError } = require("../utils/business");
  assert.equal(bindError("student", "valid-password"), "");
  assert.ok(bindError(" student", "valid-password"));
  assert.ok(bindError("student", "短"));
  assert.ok(bindError("student", "中".repeat(25)));
});
test("绑定失败清空密码而不保存", async () => {
  fresh();
  session().setTicket("ticket");
  const p = page("bind");
  p.setData({ username: "student", password: "wrong-password" });
  queue.push({ status: 401 });
  await p.submit();
  assert.equal(p.data.password, "");
  assert.ok(p.data.error);
  assert.equal(storage.size, 0);
});
test("北京时间跨UTC自然日处理", () => {
  fresh();
  const t = require("../utils/time");
  assert.equal(t.date(Date.parse("2026-10-30T17:00:00Z")), "2026-10-31");
  assert.equal(t.addDays("2026-12-31", 1), "2027-01-01");
  assert.equal(t.iso("2026-11-02", 570), "2026-11-02T09:30:00+08:00");
});
test("半小时时段28格，左闭右开相邻允许", () => {
  fresh();
  const t = require("../utils/time");
  const now = Date.parse("2026-11-01T00:00:00+08:00");
  const used = [
    { startTime: t.iso("2026-11-02", 540), endTime: t.iso("2026-11-02", 600) },
  ];
  const cells = t.cells("2026-11-02", used, [], now);
  assert.equal(cells.length, 28);
  assert.equal(cells[2].state, "used");
  assert.equal(cells[4].state, "free");
  assert.equal(t.valid("2026-11-02", 600, 630, used, now), "");
});
test("拒绝跨开放时间、粒度、超长及占用", () => {
  fresh();
  const t = require("../utils/time");
  const now = Date.parse("2026-11-01T08:00:00+08:00");
  for (const [a, b] of [
    [470, 510],
    [540, 1330],
    [541, 600],
    [540, 810],
    [600, 540],
  ])
    assert.ok(t.valid("2026-11-02", a, b, [], now));
  assert.ok(
    t.valid(
      "2026-11-02",
      540,
      600,
      [
        {
          startTime: t.iso("2026-11-02", 570),
          endTime: t.iso("2026-11-02", 630),
        },
      ],
      now,
    ),
  );
});
test("开始严格晚于当前且最多未来7天", () => {
  fresh();
  const t = require("../utils/time");
  const now = Date.parse("2026-11-01T09:00:00+08:00");
  assert.ok(t.valid("2026-11-01", 540, 600, [], now));
  assert.equal(t.valid("2026-11-08", 540, 600, [], now), "");
  assert.ok(t.valid("2026-11-08", 570, 600, [], now));
});
test("安全随机UUID与相同内容保留原键", async () => {
  fresh();
  student();
  const s = require("../services/submission");
  const a = await s.prepare(body(), "设备");
  const b = await s.prepare(body(), "设备");
  assert.match(
    a.key,
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
  );
  assert.equal(a.key, b.key);
  assert.equal(randomCount, 1);
});
test("修改设备、时间、用途分别建立新逻辑提交", async () => {
  fresh();
  student();
  const s = require("../services/submission");
  let d = await s.prepare(body(), "设备");
  for (const patch of [
    { equipmentId: "9" },
    { startTime: "2026-11-02T10:00:00+08:00" },
    { purpose: "另一用途" },
  ]) {
    const next = await s.prepare({ ...body(), ...patch }, "设备");
    assert.notEqual(d.key, next.key);
    d = next;
  }
});
test("超时重试复用持久化键，成功才清空", async () => {
  fresh();
  student();
  const s = require("../services/submission");
  const d = await s.prepare(body(), "设备");
  queue.push(new Error("timeout"));
  await assert.rejects(s.submit());
  assert.equal(s.read().key, d.key);
  queue.push({ data: { id: "25" } });
  assert.equal((await s.submit()).id, "25");
  assert.equal(
    calls[0].header["Idempotency-Key"],
    calls[1].header["Idempotency-Key"],
  );
  assert.equal(s.read(), null);
});
test("并发点击提交合并同一个在途Promise", async () => {
  fresh();
  student();
  const s = require("../services/submission");
  await s.prepare(body(), "设备");
  let finish;
  queue.push((o) => {
    finish = o;
  });
  const a = s.submit(),
    b = s.submit();
  assert.equal(a, b);
  assert.equal(calls.length, 1);
  finish.success({ statusCode: 201, data: { code: "OK", data: { id: "3" } } });
  await a;
});
test("503保留提交供手动重试，重启服务模块也保持同键", async () => {
  fresh();
  student();
  let s = require("../services/submission");
  const d = await s.prepare(body(), "设备");
  queue.push({ status: 503 });
  await assert.rejects(s.submit());
  delete require.cache[require.resolve("../services/submission")];
  s = require("../services/submission");
  assert.equal(s.read().key, d.key);
});
test("不同学生不能读取前一账号预约草稿", async () => {
  fresh();
  student();
  const s = require("../services/submission");
  await s.prepare(body(), "设备");
  session().save({
    accessToken: "other-token",
    user: { id: "2", role: "STUDENT" },
  });
  assert.equal(s.read(), null);
});
test("实验室分页与字符串设备参数不丢精度", async () => {
  fresh();
  const p = page("home");
  queue.push({
    data: { records: [{ id: "9007199254740993" }], page: 1, total: 2 },
  });
  await p.load(true);
  queue.push({
    data: { records: [{ id: "9007199254740994" }], page: 2, total: 2 },
  });
  await p.load(false);
  assert.equal(p.data.records.length, 2);
  assert.equal(p.data.records[1].id, "9007199254740994");
  assert.ok(calls[1].url.includes("page=2"));
});
test("设备分类搜索分页调用真实GET契约", async () => {
  fresh();
  const api = require("../services/api");
  queue.push({ data: { records: [], total: 0 } });
  await api.equipmentList({
    labId: "9007199254740993",
    category: "TOOL",
    keyword: "示波器",
    page: 1,
    size: 20,
  });
  assert.ok(calls[0].url.includes("labId=9007199254740993"));
  assert.ok(calls[0].url.includes("category=TOOL"));
});
test("页面离开后旧请求不能覆盖列表", async () => {
  fresh();
  const p = page("home");
  let finish;
  queue.push((o) => {
    finish = o;
  });
  const loading = p.load(true);
  p.onHide();
  finish.success({
    statusCode: 200,
    data: { code: "OK", data: { records: [{ id: "old" }], page: 1, total: 1 } },
  });
  await loading;
  assert.equal(p.data.records.length, 0);
});
test("列表空数据与失败提示不伪造记录", async () => {
  fresh();
  const p = page("home");
  queue.push({ data: { records: [], page: 1, total: 0 } });
  await p.load(true);
  assert.equal(p.data.records.length, 0);
  queue.push(new Error("network"));
  await p.load(true);
  assert.ok(p.data.error);
  assert.equal(p.data.records.length, 0);
});
test("六种预约状态中文映射", () => {
  fresh();
  assert.deepEqual(Object.values(require("../utils/business").statuses), [
    "待审批",
    "已通过",
    "已驳回",
    "已取消",
    "已过期",
    "已完成",
  ]);
});
test("仅本人开始前有效前态允许取消", () => {
  fresh();
  student();
  const b = require("../utils/business");
  const row = reservation(),
    u = session().user();
  assert.ok(b.canCancel(row, u, Date.now()));
  assert.ok(!b.canCancel({ ...row, studentId: "2" }, u, Date.now()));
  assert.ok(!b.canCancel({ ...row, status: "REJECTED" }, u, Date.now()));
  assert.ok(!b.canCancel(row, u, Date.now() + 172800000));
});
test("取消二次确认拒绝时不发送请求", async () => {
  fresh();
  student();
  const p = page("reservation");
  p.id = "24";
  p.setData({ item: reservation() });
  modal = false;
  await p.cancel();
  assert.equal(calls.length, 0);
});
test("取消成功从真实服务器刷新详情", async () => {
  fresh();
  student();
  const p = page("reservation");
  p.id = "24";
  const row = reservation();
  p.setData({ item: row });
  queue.push(
    { data: { ...row, status: "CANCELLED" } },
    { data: { ...row, status: "CANCELLED" } },
  );
  await p.cancel();
  assert.equal(p.data.item.status, "CANCELLED");
  assert.equal(p.data.canCancel, false);
  assert.ok(calls[0].url.endsWith("/24/cancel"));
  assert.ok(calls[1].url.endsWith("/24"));
});
test("INVALID_STATE提示并刷新，不覆盖服务端状态", async () => {
  fresh();
  student();
  const p = page("reservation");
  p.id = "24";
  const row = reservation();
  p.setData({ item: row });
  queue.push(
    { status: 409, code: "INVALID_STATE" },
    { data: { ...row, status: "EXPIRED" } },
  );
  await p.cancel();
  assert.equal(p.data.item.status, "EXPIRED");
  assert.ok(p.data.error.includes("变化"));
});
test("不展示其他学生预约数据", async () => {
  fresh();
  student();
  const p = page("reservation");
  p.id = "24";
  queue.push({ data: { ...reservation(), studentId: "2" } });
  await p.load();
  assert.equal(p.data.item, null);
  assert.ok(p.data.error.includes("其他学生"));
});
test("非法ID不会发请求，服务层无管理员审批方法", () => {
  fresh();
  const api = require("../services/api");
  assert.throws(() => api.reservation(9007199254740993), /标识/);
  assert.equal(api.review, undefined);
  assert.equal(api.revoke, undefined);
  assert.equal(api.complete, undefined);
});

test("旧请求401不能清除新登录的会话", async () => {
  fresh();
  student();
  let finish;
  queue.push((o) => {
    finish = o;
  });
  const call = require("../services/api").me();
  session().save({
    accessToken: "new-token",
    user: { id: "2", role: "STUDENT" },
  });
  finish.success({ statusCode: 401, data: { code: "UNAUTHORIZED" } });
  await assert.rejects(call);
  assert.equal(session().token(), "new-token");
  assert.equal(navigation.length, 0);
});
test("旧me结果不能覆盖新学生身份", async () => {
  fresh();
  const old = student();
  let finish;
  queue.push((o) => {
    finish = o;
  });
  const call = require("../services/auth").restore();
  session().save({
    accessToken: "new-token",
    user: { id: "2", role: "STUDENT" },
  });
  finish.success({ statusCode: 200, data: { code: "OK", data: old } });
  await assert.rejects(call, /会话已变化/);
  assert.equal(session().user().id, "2");
  assert.equal(session().token(), "new-token");
});
test("离开确认页后提交成功不强制跳回旧详情", async () => {
  fresh();
  student();
  const s = require("../services/submission");
  const d = await s.prepare(body(), "设备");
  const p = page("confirm");
  p.setData({ draft: d });
  let finish;
  queue.push((o) => {
    finish = o;
  });
  const call = p.submit();
  await new Promise((resolve) => setImmediate(resolve));
  p.onHide();
  finish.success({ statusCode: 201, data: { code: "OK", data: { id: "55" } } });
  await call;
  assert.equal(navigation.length, 0);
});

test("在途旧内容不能伪装成新内容的成功结果", async () => {
  fresh();
  student();
  const s = require("../services/submission");
  await s.prepare(body(), "设备");
  let finish;
  queue.push((o) => {
    finish = o;
  });
  const old = s.submit();
  await s.prepare({ ...body(), purpose: "新用途" }, "设备");
  const newKey = s.read().key;
  await assert.rejects(s.submit(), /上一笔提交/);
  finish.success({
    statusCode: 201,
    data: { code: "OK", data: { id: "old-result" } },
  });
  await old;
  assert.equal(s.read().key, newKey);
  assert.equal(calls.length, 1);
});
