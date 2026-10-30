const api = require("./api");
const session = require("../utils/session");
const STORAGE = "lab-reservation-draft";
let pending = null;
let pendingKey = null;
function uuid() {
  return new Promise((resolve, reject) =>
    wx.getRandomValues({
      length: 16,
      success(result) {
        const bytes = new Uint8Array(result.randomValues);
        if (bytes.length !== 16) {
          reject(new Error("无法生成安全提交标识"));
          return;
        }
        bytes[6] = (bytes[6] & 15) | 64;
        bytes[8] = (bytes[8] & 63) | 128;
        const h = Array.from(bytes, (b) =>
          b.toString(16).padStart(2, "0"),
        ).join("");
        resolve(
          `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`,
        );
      },
      fail: () => reject(new Error("安全随机数不可用，请升级微信客户端")),
    }),
  );
}
function read() {
  const d = wx.getStorageSync(STORAGE);
  return d && session.user() && d.userId === session.user().id ? d : null;
}
async function prepare(body, name) {
  const user = session.user();
  if (!user) throw new Error("请先登录");
  const normalized = {
    equipmentId: body.equipmentId,
    startTime: body.startTime,
    endTime: body.endTime,
    purpose: body.purpose.trim(),
  };
  const fingerprint = JSON.stringify(normalized);
  const old = read();
  if (old && old.fingerprint === fingerprint) return old;
  const draft = {
    userId: user.id,
    body: normalized,
    equipmentName: name,
    fingerprint,
    key: await uuid(),
  };
  wx.setStorageSync(STORAGE, draft);
  return draft;
}
function submit() {
  const draft = read();
  if (!draft)
    return Promise.reject(new Error("提交内容已失效，请重新选择时段"));
  if (pending)
    return pendingKey === draft.userId + ":" + draft.key
      ? pending
      : Promise.reject(new Error("上一笔提交仍在处理中，请先查询结果后重试"));
  pendingKey = draft.userId + ":" + draft.key;
  pending = api
    .create(draft.body, draft.key)
    .then((result) => {
      const current = read();
      if (
        current &&
        current.key === draft.key &&
        current.userId === draft.userId
      )
        wx.removeStorageSync(STORAGE);
      return result;
    })
    .finally(() => {
      pending = null;
      pendingKey = null;
    });
  return pending;
}
module.exports = { uuid, read, prepare, submit };
