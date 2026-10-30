// 可移植的基础检查，不等同于微信官方 WXML/WXSS 编译。
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const root = path.resolve(__dirname, "..");
function walk(dir) {
  return fs
    .readdirSync(dir, { withFileTypes: true })
    .flatMap((e) =>
      e.isDirectory() && !["node_modules"].includes(e.name)
        ? walk(path.join(dir, e.name))
        : e.isFile()
          ? [path.join(dir, e.name)]
          : [],
    );
}
let count = 0;
for (const file of walk(root)) {
  if (file.endsWith(".js")) {
    new vm.Script(fs.readFileSync(file, "utf8"), { filename: file });
    count++;
  }
  if (file.endsWith(".json")) {
    JSON.parse(fs.readFileSync(file, "utf8"));
    count++;
  }
}
const app = require("../app.json");
const native = new Set([
  "view",
  "text",
  "input",
  "textarea",
  "button",
  "picker",
  "block",
  "scroll-view",
  "image",
  "navigator",
  "switch",
  "label",
]);
for (const page of app.pages) {
  for (const ext of [".js", ".json", ".wxml"])
    if (!fs.existsSync(path.join(root, page + ext)))
      throw new Error("页面文件缺失：" + page + ext);
  const config = JSON.parse(
    fs.readFileSync(path.join(root, page + ".json"), "utf8"),
  );
  let definition;
  global.Page = (p) => {
    definition = p;
  };
  require(path.join(root, page + ".js"));
  const wxml = fs.readFileSync(path.join(root, page + ".wxml"), "utf8");
  const stack = [];
  const tags = wxml.match(/<(?:[^>"']|"[^"]*"|'[^']*')*>/g) || [];
  for (const tag of tags) {
    const match = tag.match(/^<(\/?)([\w-]+)/);
    if (!match) continue;
    const [, closing, name] = match;
    if (!native.has(name) && !(config.usingComponents || {})[name])
      throw new Error("未声明标签：" + name);
    if (closing) {
      if (stack.pop() !== name) throw new Error("标签未配对：" + page);
    } else if (!tag.endsWith("/>")) stack.push(name);
    for (const h of tag.matchAll(
      /(?:bind|catch)(?::?[\w]+)="([a-zA-Z][\w]*)"/g,
    ))
      if (typeof definition[h[1]] !== "function")
        throw new Error("事件处理缺失：" + page + " " + h[1]);
  }
  if (stack.length) throw new Error("标签未闭合：" + page);
}
const sources = walk(path.join(root, "services"))
  .map((f) => fs.readFileSync(f, "utf8"))
  .join("\n");
if (/\/(review|revoke|complete)['"`]/.test(sources))
  throw new Error("学生端不能包含管理员动作");
console.log(
  `JavaScript/JSON ${count} 文件检查通过，${app.pages.length} 个页面结构、路由和事件检查通过（非官方编译）`,
);
