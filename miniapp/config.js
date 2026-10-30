// 仅公开地址配置；AppSecret 和 JWT 签名密钥绝不能进入小程序。
// 正式发布前填写已在微信平台配置的 HTTPS request 合法域名，包含 /api/v1。
const targets = {
  develop: "http://127.0.0.1:8080/api/v1",
  trial: "",
  release: "",
};
function baseUrl() {
  const version = wx.getAccountInfoSync().miniProgram.envVersion;
  const url = targets[version];
  if (!url || (version !== "develop" && !url.startsWith("https://"))) {
    throw new Error("尚未配置此版本的 HTTPS 接口域名，请联系管理员");
  }
  return url.replace(/\/$/, "");
}
module.exports = { baseUrl };
