const TOKEN = "lab-student-token";
const USER = "lab-student-user";
let bindTicket = "";
function token() {
  return wx.getStorageSync(TOKEN) || "";
}
function user() {
  return wx.getStorageSync(USER) || null;
}
function clear() {
  wx.removeStorageSync(TOKEN);
  wx.removeStorageSync(USER);
  bindTicket = "";
}
function save(result) {
  if (
    !result ||
    !result.accessToken ||
    !result.user ||
    result.user.role !== "STUDENT"
  ) {
    clear();
    throw new Error("仅学生账号可以使用小程序");
  }
  wx.setStorageSync(TOKEN, result.accessToken);
  wx.setStorageSync(USER, result.user);
  bindTicket = "";
}
module.exports = {
  token,
  user,
  clear,
  save,
  ticket: () => bindTicket,
  setTicket: (value) => {
    bindTicket = value;
  },
};
