const api = require("./api");
const session = require("../utils/session");
let restoring;
let restoringToken;
function restore() {
  if (!session.token()) return Promise.resolve(null);
  const token = session.token();
  if (restoring && restoringToken === token) return restoring;
  restoringToken = token;
  restoring = api
    .me()
    .then((user) => {
      if (session.token() !== token)
        throw new Error("会话已变化，请重新进入页面");
      session.save({ accessToken: token, user });
      return user;
    })
    .finally(() => {
      if (restoringToken === token) restoring = null;
    });
  return restoring;
}
function login() {
  return new Promise((resolve, reject) =>
    wx.login({
      timeout: 10000,
      success: (result) =>
        result.code
          ? resolve(result.code)
          : reject(new Error("微信未返回登录凭据，请重试")),
      fail: () => reject(new Error("微信登录失败，请检查网络后重试")),
    }),
  )
    .then((code) => api.login(code))
    .then((result) => {
      if (result.needBind) {
        if (!result.bindTicket) throw new Error("服务返回的绑定凭证缺失");
        session.setTicket(result.bindTicket);
        return "bind";
      }
      session.save(result);
      return "home";
    });
}
async function bind(username, password) {
  const ticket = session.ticket();
  if (!ticket) throw new Error("绑定凭证不存在，请重新微信登录");
  const result = await api.bind({ bindTicket: ticket, username, password });
  session.save(result);
}
function logout() {
  session.clear();
  wx.removeStorageSync("lab-reservation-draft");
  wx.reLaunch({ url: "/pages/login/index" });
}
module.exports = { restore, login, bind, logout };
