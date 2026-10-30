// 由 WechatAuthIT 启动：真实 HTTP/MySQL，只有微信平台换码和 wx 容器 API 使用测试适配。
// 不属于小程序发布代码，不冒充微信开发者工具或真机验证。
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const { URL } = require("node:url");
const base = process.env.MINIAPP_TEST_API_URL;
const password = process.env.MINIAPP_TEST_PASSWORD;
const equipmentId = process.env.MINIAPP_TEST_EQUIPMENT_ID;
if (
  !base ||
  !password ||
  !equipmentId ||
  !["127.0.0.1", "localhost"].includes(new URL(base).hostname)
)
  throw new Error("必须由隔离测试注入本地接口与凭据");
const storage = new Map();
global.wx = {
  getStorageSync: (k) => storage.get(k),
  setStorageSync: (k, v) => storage.set(k, v),
  removeStorageSync: (k) => storage.delete(k),
  getAccountInfoSync: () => ({ miniProgram: { envVersion: "develop" } }),
  login: (o) => o.success({ code: "isolated-mock-platform-code" }),
  getRandomValues: (o) => {
    const b = crypto.randomBytes(16);
    o.success({
      randomValues: b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength),
    });
  },
  reLaunch() {},
  request(o) {
    fetch(o.url, {
      method: o.method,
      headers: o.header,
      body: o.method === "GET" ? undefined : JSON.stringify(o.data),
    })
      .then(async (r) =>
        o.success({
          statusCode: r.status,
          data: await r.json(),
          header: Object.fromEntries(r.headers),
        }),
      )
      .catch(() => o.fail({ errMsg: "network failure" }));
  },
};
require("../config").baseUrl = () => base;
const auth = require("../services/auth"),
  api = require("../services/api"),
  submission = require("../services/submission"),
  time = require("../utils/time");
async function admin(path, body, token) {
  const r = await fetch(base + path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: "Bearer " + token } : {}),
    },
    body: JSON.stringify(body),
  });
  assert.equal(r.status, 200);
  return (await r.json()).data;
}
async function run() {
  assert.equal(await auth.login(), "bind");
  await auth.bind("student", password);
  assert.equal((await api.me()).role, "STUDENT");
  const labs = await api.labs({ page: 1, size: 20 });
  assert.ok(labs.total > 0);
  const equipment = await api.equipment(equipmentId);
  const list = await api.equipmentList({
    labId: equipment.labId,
    page: 1,
    size: 20,
  });
  assert.ok(list.records.some((r) => r.id === equipmentId));
  const day = time.addDays(time.date(), 1);
  assert.equal((await api.slots(equipmentId, day)).slots.length, 0);
  const body = {
    equipmentId,
    startTime: time.iso(day, 540),
    endTime: time.iso(day, 600),
    purpose: "真实HTTP跨端联调",
  };
  const draft = await submission.prepare(body, equipment.name);
  const result = await submission.submit();
  assert.equal(result.status, "PENDING");
  assert.equal((await api.create(body, draft.key)).id, result.id);
  await assert.rejects(
    api.create({ ...body, purpose: "不同内容" }, draft.key),
    (e) => e.code === "IDEMPOTENCY_CONFLICT",
  );
  await assert.rejects(
    api.create(body, await submission.uuid()),
    (e) => e.code === "TIME_CONFLICT",
  );
  assert.equal(
    (await api.mine({ page: 1, size: 20 })).records[0].id,
    result.id,
  );
  const lab = await admin("/auth/login", { username: "lab", password });
  await admin(
    "/reservations/" + result.id + "/review",
    { decision: "APPROVE" },
    lab.accessToken,
  );
  const detail = await api.reservation(result.id);
  assert.equal(detail.status, "APPROVED");
  assert.equal(detail.logs.length, 2);
  await api.cancel(result.id, "学生取消HTTP验证");
  const cancelled = await api.reservation(result.id);
  assert.equal(cancelled.status, "CANCELLED");
  assert.equal(cancelled.logs.length, 3);
  const sys = await admin("/auth/login", { username: "admin", password });
  assert.ok(sys.accessToken);
  console.log(
    "小程序正式服务层真实HTTP流程通过：绑定、JWT、资源、时段、创建、幂等、冲突、管理员审批、本人查询和取消。微信平台为测试替换，非真机。",
  );
}
run().catch(() => {
  console.error("小程序隔离HTTP联调失败（原始异常已隐藏）");
  process.exitCode = 1;
});
