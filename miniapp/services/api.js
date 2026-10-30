const { request } = require("./request");
function query(params) {
  const keys = Object.keys(params || {}).filter(
    (k) => params[k] !== "" && params[k] !== null && params[k] !== undefined,
  );
  return keys.length
    ? "?" +
        keys
          .map(
            (k) => encodeURIComponent(k) + "=" + encodeURIComponent(params[k]),
          )
          .join("&")
    : "";
}
function id(value) {
  if (typeof value !== "string" || !/^[1-9][0-9]{0,18}$/.test(value))
    throw new Error("资源标识不正确");
  return value;
}
module.exports = {
  login: (code) =>
    request("/auth/wechat/login", {
      method: "POST",
      data: { code },
      anonymous: true,
    }),
  bind: (data) =>
    request("/auth/wechat/bind", { method: "POST", data, anonymous: true }),
  me: () => request("/auth/me"),
  labs: (params) => request("/labs" + query(params)),
  lab: (value) => request("/labs/" + id(value)),
  equipmentList: (params) => request("/equipment" + query(params)),
  equipment: (value) => request("/equipment/" + id(value)),
  slots: (value, date) =>
    request("/equipment/" + id(value) + "/slots" + query({ date })),
  create: (data, key) =>
    request("/reservations", {
      method: "POST",
      data,
      header: { "Idempotency-Key": key },
    }),
  mine: (params) => request("/reservations/mine" + query(params)),
  reservation: (value) => request("/reservations/" + id(value)),
  cancel: (value, reason) =>
    request("/reservations/" + id(value) + "/cancel", {
      method: "POST",
      data: reason ? { reason } : {},
    }),
};
