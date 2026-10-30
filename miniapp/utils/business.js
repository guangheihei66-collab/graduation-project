const statuses = {
  PENDING: "待审批",
  APPROVED: "已通过",
  REJECTED: "已驳回",
  CANCELLED: "已取消",
  EXPIRED: "已过期",
  COMPLETED: "已完成",
};
const categories = {
  INSTRUMENT: "仪器",
  COMPUTER: "计算机",
  TOOL: "实验工具",
  OTHER: "其他",
};
const equipmentStatus = {
  AVAILABLE: "可用",
  MAINTENANCE: "维护中",
  DISABLED: "已停用",
};
const actions = {
  CREATE: "提交预约",
  APPROVE: "审批通过",
  REJECT: "驳回",
  CANCEL: "本人取消",
  REVOKE: "管理员撤销",
  EXPIRE: "系统过期",
  COMPLETE: "归还完成",
};
function bindError(username, password) {
  if (!username || username !== username.trim() || username.length > 64)
    return "请输入1—64字符的学号或用户名，不含首尾空白";
  if (
    !password ||
    password.length < 8 ||
    password.length > 64 ||
    unescape(encodeURIComponent(password)).length > 72
  )
    return "密码须为8—64字符，UTF-8不超过72字节";
  return "";
}
function canCancel(row, user, now) {
  return (
    !!row &&
    !!user &&
    row.studentId === user.id &&
    ["PENDING", "APPROVED"].includes(row.status) &&
    Date.parse(row.startTime) > now
  );
}
module.exports = {
  statuses,
  categories,
  equipmentStatus,
  actions,
  bindError,
  canCancel,
};
