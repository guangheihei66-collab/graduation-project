# 微信原生学生端（阶段 5）

使用 WXML、WXSS、JavaScript 和微信原生 API，无运行时 npm 依赖。已实现正式业务接口对接与自动化逻辑/HTTP 验证；未验证微信官方编译、真机及真实微信平台认证，不能视为已可正式发布。

## 工程与页面

```text
app.js / app.json / app.wxss   启动身份校验、页面、底部Tab和全局样式
project.config.json           安全工程配置，touristappid不具备真实认证资格
config.js                     开发/体验/正式API地址（公开配置）
pages/login、bind             wx.login与学生账号密码绑定
pages/home、lab、equipment    实验室分页、详情与设备分类/搜索/详情
pages/slots、confirm          半小时时段、用途、确认与幂等提交
pages/reservations            我的预约，状态/开始日期/资源ID筛选
pages/reservation、profile    本人详情/审计/取消、当前账号/退出
components/load-state        加载、错误重试与空数据
services/                    wx.request、正式学生API、认证与提交
utils/                       会话、北京时间、分页与页面请求代次
scripts/、tests/              结构检查、Node逻辑与隔离HTTP测试，不发布
```

共10个页面。蓝白配色、底部Tab、卡片、状态标签、日期选择、时段网格、加载更多、下拉刷新、二次确认和网络反馈。设备没有图片字段，不新增图片数据库或上传服务。用户输入通过WXML文本绑定展示。

## 导入与配置

1. 从微信官方渠道安装微信开发者工具（云端没有该工具，Windows上需自行验证）。导入本仓库的 miniapp 目录，不导入仓库根目录。
2. 在工具中选择自己的真实小程序 AppID。提交的 touristappid 只保留可安全导入的配置，不代表真实微信登录可用；AppID是公开标识，AppSecret只能配置在后端。
3. 工程配置选择基础库3.7.12，属于配置目标，并非本环境已安装/通过的版本。开发者工具需下载可用兼容基础库；本项目使用安全随机数 wx.getRandomValues，不使用 Math.random 代替幂等UUID。
4. 编辑 config.js 的 targets。develop 默认 http://127.0.0.1:8080/api/v1，仅供电脑开发者工具连接本机。体验/正式地址默认空白，未配置会明确拒绝请求，不会自动回退测试HTTP地址。
5. 启动真实MySQL及后端，设置微信环境变量，使用预先创建的STUDENT账号绑定。无需执行“构建npm”，没有 npm 运行依赖。
6. project.private.config.json 已忽略，工具本地设置不要提交。密码、微信code、JWT和绑定票据不能写进工程配置或共享日志。

Windows可在PowerShell进入 miniapp，使用 Node.js 24.19.0 / npm 11.9.0（本次Linux实际版本；工程最低Node22.22.2）运行下列检查。后端按 backend/README.md 设置环境变量，PowerShell用 `$env:变量名='本地值'`，不要提交实际值。Windows工具及真机没有在本次云端验证。

## HTTPS与微信凭据

后端设置 WECHAT_APP_ID、WECHAT_APP_SECRET、既有数据库及JWT变量。微信客户端真实请求固定为 https://api.weixin.qq.com/sns/jscode2session，连接3秒、整个HTTP请求5秒超时，不跟随重定向。云环境没有真实微信凭据；访问官方文档 https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html 经代理返回403，在线现行正文未能核对，正式联调前需要在可访问环境复核。

正式小程序须在微信平台配置 HTTPS request 合法域名、有效证书以及实际后端地址。开发者工具本机调试临时关闭域名校验仅是本地选项，不是正式发布能力；本项目提交配置保持 urlCheck=true。127.0.0.1在真机指手机自身，不是开发电脑。生产域名、备案及平台账号要求需按当前微信平台规则实际确认；未做线上部署。

所有前端配置都会进入小程序，禁止放入AppSecret、JWT签名密钥、数据库密码。后端未配置微信凭据时微信登录返回503，管理员原密码登录仍可用，没有固定openid或免密入口。

## 登录与绑定

启动及受保护页面通过 /auth/me 校验已有JWT；401清理会话并返回登录，403保留会话，网络失败显示重试。点击微信登录后 wx.login 获取一次性code，仅通过POST请求体发送服务端。

已绑定、启用的STUDENT返回现有JWT。未绑定返回5分钟bindTicket，票据只在运行内存中保存，不写URL或本地存储；输入预创建学生账号及密码完成BCrypt验证。绑定成功保存JWT与脱敏UserView，销毁票据。密码不持久化，绑定失败或离开页面会清空输入。进程退出导致票据丢失时重新微信登录，不猜测身份。

JWT与当前用户使用wx本地存储。退出清除令牌、用户、票据及预约草稿；本地存储不等于加密保险箱，应保护设备。退出不保证服务端JWT即时撤销；每次请求仍由后端检查启用及角色。正式登录不返回openid、session_key或密码哈希。

## 预约与幂等

- 只调用既有学生API，不重写后端冲突、配额或状态机。全部ID保持字符串，包括筛选与导航参数。
- 北京时间、08:00—22:00、半小时粒度、30分钟至4小时、同日、开始晚于当前且至多未来7天；使用HTTP Date响应头辅助校准展示时间，缺失时用设备时钟，最终服务端判定。
- 时段网格：先点开始格，再点最后包含的半小时格；同一格点两次为30分钟。占用/过去/超出7天不可选择，连续区间包含占用则提示。刷新会清空选择，提交仍可能409。
- 用途去首尾空白后1—500字符。确认前生成安全随机UUID，按当前学生和规范化设备、时间、用途保存提交草稿及键；只修改名称展示不会换键。
- 一次在途提交共用Promise；网络超时、503和进程重启后的同内容重试沿用原键，不自动无限重试。改设备/时间/用途才建立新键；201创建与200重放都视为服务端成功，然后查询预约详情。
- TIME_CONFLICT提示重新选择；IDEMPOTENCY_CONFLICT提示检查内容；QUOTA_EXCEEDED、RESOURCE_UNAVAILABLE有明确提示。失败不会生成假的预约结果。
- 仅显示本人开始前PENDING/APPROVED的取消按钮，原因可选最多500字符。确认取消后重新查服务端；INVALID_STATE提示变化并刷新。
- 预约状态显示：待审批、已通过、已驳回、已取消、已过期、已完成。详情显示真实审批意见与审计，不包含管理员审批/撤销/完成操作入口。

草稿保存用途以支持安全重试，只按对应学生读取，退出主动清除；不要共用无人监管的设备。授权和归属由服务端最终执行，页面ID验证不是授权替代。

## 检查和测试

```bash
cd miniapp
npm run check
npm test
```

基础检查解析全部JS/JSON、10个页面路由/文件、WXML标签配对和事件处理器；不是微信官方WXML/WXSS编译器。Node内置测试直接执行 student.test.js，避免某些运行器仅汇报文件数量；以实际测试明细计数。

隔离HTTP测试 tests/real-backend.js 由后端 WechatAuthIT 自动启动，执行小程序正式services代码和真实Spring Boot端口/MySQL；wx容器API及微信换码为测试替换，不是微信真机。测试脚本不属于发布代码，project.config.json已排除 tests/scripts。实际基础检查39个JS/JSON文件、10个页面通过；41项Node逻辑测试通过。后端30单元+113 MySQL集成共143项通过；原后台36项Vitest及4项真实浏览器回归通过。完整命令及结果见 docs/PROGRESS.md。

## 必须补验的项目

- 微信开发者工具实际导入、WXML/WXSS官方编译、模拟器跳转/表单/时段及网络交互。
- 使用真实AppID/AppSecret与wx.login code验证官方换码、首次绑定和再次登录。
- 合规HTTPS域名、真机网络、常见屏幕及安全区域、Windows工具。
- 正式发布、安全测试和性能基准均未完成，不提前进入阶段6。
