# SpiritWave TTS 项目服务器交接摘要

整理时间：2026-10-27。依据 Muse 本地记录及其 2026-10-27 只读现场核验整理；本次未对服务器进行配置变更，不含任何密码或密钥。历史资料来源为 2026-10-21 / 2026-10-23 当日记录、Muse 的 `MEMORY.md`、`AGENTS.md` 与目标台账。现场核验未读取服务配置、环境变量、密钥或用户数据。

## 0. 项目定性

记录中的项目是“逐条语音朗读器”：单文件纯前端网页，朗读使用浏览器端语音合成。已记录的功能包括发音人、语速、句间间隔、Wake Lock、快捷键、逐条进度，以及朗读前符号清洗。

旧记录中没有服务端 TTS 模型部署信息。2026-10-27 只读核验确认另有 `story-tts-mvp`：8765 是其前端，8766 运行 `qwen-tts.service`，模型为 `Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice`。模型目录、API 路由与鉴权已按需核实，详见第 5 节。

## 1. 主机与连接

- 主机：家中 Ubuntu Server 旧电脑；记录配置为 512 GB SSD、16 GB 内存、RTX 3050 Ti。
- 局域网地址：`192.168.10.10`；SSH 用户：`bonjour`。
- 公网 SSH：`ssh.spiritwave.dpdns.org`，Cloudflare Tunnel 映射目标端口 `2222`；记录称只允许密钥登录、已关闭密码登录。不要将密钥内容写入文档或聊天。
- 2026-10-27 只读监听检查发现 SSH 监听 `0.0.0.0:2222`，不是 22；局域网连接应尝试 `192.168.10.10:2222`，但本次没有从局域网实际连接验证。
- Muse 曾验证的连接链：Muse 所在本机 `cloudflared` 本地代理 `127.0.0.1:23333` → 家中服务器 → 目标服务。
- 局域网 SSH 本次未实际连接测试；服务器监听 `0.0.0.0:2222`，因此记录的候选地址为 `192.168.10.10:2222`。服务器主机名曾出现 `spiritewave` 与 `spiritwave` 两种写法，也未核实。
- 记录称服务器夜间约 22:00–08:00 断联多数是用户主动关机，不应直接视为服务故障。

## 2. 已记录的网页部署

- 网页文件：`~/home-server/tts-reader/html/index.html`（上线记录约 19,156 字节）。
- 服务：`python3 -m http.server` 监听 `127.0.0.1:8088`，已加入 `~/home-server/bin/server-up.sh` 开机启动。
- 公网地址：<https://tts.spiritwave.dpdns.org/>，隧道 ingress 指向 `http://127.0.0.1:8088`。
- 2026-10-21 当日记录显示本机 8088 和公网均返回 HTTP 200，公网文件 MD5 与源文件一致；v2 记录的 MD5 为 `74062fb7e1514023754eb95654bd43f3`。
- v2 改动记录：顶部句子进度、修复右侧气泡压扁（行高 1.7、自动高度）、朗读前清洗符号但保持展示原文。
- 当时未采用 nginx / Docker：`/opt` 需要 sudo；Docker nginx 镜像下载遇到 403 或超时，因此改用 Python HTTP 服务。
- 2026-10-27 只读核验：公网 `https://tts.spiritwave.dpdns.org/` 返回 HTTP 200，外部响应约 1.8 秒；本机 `127.0.0.1:8088` 由 Python 进程（PID 3807）监听并返回 200。页面标题仍为“逐条语音朗读器”，文件 21,373 字节，正文 MD5 与 2026-10-21 v2 一致；v1 备份 19,156 字节仍在。因此旧朗读器自 2026-10-21 以来未变。
- 服务器本机 curl 公网域名时曾等待 20 秒仍未收完 body；Muse 判断可能与服务器出站/回环路径慢有关。外部实测正常，本机该路径原因未确定。

## 3. 域名、隧道与证书

- Cloudflare 命名隧道：`homeserver`；记录称通过 cron `@reboot` 启动。
- 隧道配置：`~/.cloudflared/config.yml`；备份见下一节。
- `tts.spiritwave.dpdns.org` 的 CNAME 由 `cloudflared tunnel route dns` 建立；ingress 指向本机 8088。2026-10-21 21:21 CST 曾通过 `restart-tunnel-delayed.sh` 延时重启隧道。
- 同隧道其他已记录入口：`code`、`ssh`、`ai`、`docker`、`monitor`、`dl.spiritwave.dpdns.org`；当时记录 `code` 仍返回 302。
- 记录中没有本地 certbot / ACME 证书配置；TLS 由 Cloudflare 边缘提供，本机证书细节未核实。
- `spiritwave.dpdns.org` 与 `spiritwave1.dpdns.org` 记录为 DigitalPlat 免费子域名，每年 12 月 3 日到期；已有每月 1 日和 11 月 19 日、11 月 26 日、12 月 2 日提醒。续期是否已执行仍需确认。

## 4. 备份与变更记录

- 网页 v1 备份：`~/home-server/tts-reader/html/index.html.bak-20261021-v1`。
- 隧道配置备份：`~/.cloudflared/config.yml.bak-tts-20261021`。
- 服务器 CHANGELOG 是部署细节的权威记录；精确路径未记录，推测在 `~/home-server/` 下，需现场确认。
- Muse 的目标台账目录 `workspace/goals/sentence-by-sentence-voice-reader/files/` 当时为空；记录称网页没有 Muse 本机副本，备份在服务器侧。
- 除上述备份外，是否有更新的网页备份未核实。

## 5. 同机其他服务（均非 TTS）

- code-server：端口 8080。
- Open WebUI：Docker 端口 3000；`WHISPER_MODEL=small` 是语音识别配置，不是 TTS。
- Portainer、Netdata。
- Flask 多用户 NetDisk：端口 8899。
- Ollama `qwen3:4b`：端口 11434，经 `olfwd` 转发 11435；公网 qwen API 为 11440 → 11434。
- `story-tts-mvp`：2026-10-27 只读核验确认 8765 是 Docker 容器（`python:3.10-slim`，容器内 8000 映射到 8765）的《声场 | 剧情配音》前端；8766 是 `qwen-tts.service`（active/running，uvicorn），工作目录 `/home/bonjour/story-tts-mvp`，服务描述为 “Local Qwen3-TTS API”。两者属于同一项目目录。
- Qwen 模型：`Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice`，模型目录 `/home/bonjour/tts-model`；只使用 CUDA，运行设备 `cuda:0`，精度 `bfloat16`。服务配置为单 worker，失败自动重启。
- API：`GET /health` 返回 `ready`、`model`、`device`，现场返回 200 且 `ready=true`；`/healthz` 返回 404；`/docs` 返回 200。
- API：`POST /synthesize` 接收 JSON：`text`（必填，1–1200 字）、`speaker`（必填：`Vivian` / `Serena` / `Uncle_Fu` / `Dylan` / `Eric`）、`emotion`（可选，默认 `neutral`；支持 `neutral` / `sad` / `angry` / `hesitant` / `whisper` / `joy`）、`direction`（可选，最长 120 字）。语言固定中文，每次一个请求，成功返回 `audio/wav`（PCM 16-bit）。校验失败 422，模型加载中 503，代理上游失败 502。
- 8765 前端使用同源 `/health` 和 `/synthesize`；`web_proxy.py` 将请求转发到宿主机 8766，上游超时 300 秒。前端按片段发送 `text` / `speaker` / `emotion` / `direction`。
- API 无鉴权；CORS 仅允许 `192.168.10.10:8765`、`tts.spiritwave.dpdns.org`、`127.0.0.1:8765`、`localhost:8765`，但 CORS 不是访问控制。API 监听 `0.0.0.0:8766`，因此局域网可直连；该监听本身不等于公网可达。
- 2026-10-27 本机合成测试：文本“今天天气不错，我们一起去公园散步吧。”、speaker `Vivian`、默认情绪；HTTP 200，耗时 3.00 秒，WAV PCM 16-bit 单声道 24 kHz，184,364 字节、3.84 秒。临时文件 `/tmp/tts-test.wav` 已删除。
- 当日公网 `tts.spiritwave.dpdns.org` 仍指向旧 8088 朗读器，而不是 story 前端。只读探测未观察到 Cloudflare Access 登录墙、常规 WAF 质询或限流响应；单次正常请求不能排除特定规则。域名没有可见保护时，不应直接把无鉴权合成接口切到公网。
- 其他运行中的 systemd 服务包括 `docker.service`、`ollama.service`；Docker 容器还包括 `open-webui`、`netdata`、`portainer`、`my-hermes-agent`、`ddns-go`。后两者作用未核实，未暴露端口。
- 已知但未确认关联：一个监听 `*:9876` 的进程归属不明；`127.0.0.1:20241` 已确认属于 cloudflared。

## 6. Clash、代理与网络记录

- Clash 虚拟网段：记录将 `198.18.0.0/15` 标注为 Clash fake-IP / TUN 网段；`198.18.0.1` 是小新本机虚拟地址，不是家中服务器可访问的小新局域网地址。
- 2026-10-25：fake-IP、系统代理关闭、TUN 关闭时，应用直连 fake-IP 会失败；记录称当时将注册表 `ProxyEnable` 从 0 改为 1，`ProxyServer` 已是 `127.0.0.1:7897`，并回读确认。
- 2026-10-24：Clash Verge Merge 顶层 `rules:` 覆盖了 867 条订阅规则，造成 Google / OpenAI 直连并因 DNS 污染超时；记录称已改为 `prepend-rules:`，规则数为 868。后续修改需重新生成订阅并核对规则数。
- 2026-10-27：Muse 本机的 `cloudflared` 代理 `127.0.0.1:23333` 曾掉线，随后后台重启恢复。记录提醒 `pkill -f` 可能匹配并杀掉自身。
- 手机使用中国移动流量时，记录称 `*.spiritwave.dpdns.org` 可能因移动 DNS 到 DigitalPlat 解析链路而无法打开；阿里 DoH 描述文件已提供但尚未收到安装测试反馈。
- 服务器使用 Wi-Fi，有线网口为 DOWN；记录称公网下载很慢，大文件操作优先使用局域网 `192.168.10.10`。
- SakuraCat 是用户侧过渡 VPN，记录明确其与本项目服务器无关。

## 7. 待核实事项

1. 在家庭局域网使用 `http://192.168.10.10:8765/`；该入口在 Muse 核验时作为同源前端和 API 代理可用，浏览器端实际访问仍待用户设备验证。
2. 若将 `tts.spiritwave.dpdns.org` 切至 story 前端，先配置 Cloudflare Access 或等效鉴权与限流，并确认用户访问流程；目前该域名无可见访问保护，API 没有鉴权。
3. 确认是否要把旧 `tts-reader` 留作服务器备份或迁到其他路径；原网页文件与 v1 备份目前均在服务器侧。
4. 补齐服务器 CHANGELOG 的实际路径，并与本文件核对。
5. 完成 Windows 与华为平板浏览器上的前端、生成音频播放、Wake Lock、倒计时真机验证。
6. 确认 12 月 3 日域名续期是否已处理。
7. 2026-10-27 安全核验续查：本机通过既有 SSH 密钥以 `bonjour` 成功连接；`ufw.service` 为 active/enabled，但 `sudo -n` 需要密码，故 UFW 实际规则、nftables、IPv6 入站可达性及 root 视角的 sshd/进程归属尚未核实。Muse 的只读检查也因没有安全 sudo 提示通道而停止；未传入口令、未做变更。

## 8. Access 与新站点部署规则

- 以后新增的公网网址，发布前必须登记到 Cloudflare Access 的保护清单，并验证未登录请求确实无法直达受保护入口。不要遗漏 Tunnel 新增 hostname。
- 管理面板使用仅允许站点所有者的 Access 策略；普通用户产品入口必须使用独立的 Access 应用/策略，不能继承所有者邮箱白名单。
- Cloudflare Access 负责边缘身份验证，不提供 SpiritWave 产品内的用户注册、账号生命周期或业务权限。产品注册/登录应由应用自身实现；Access OTP 可作为早期受限用户的邮箱登录方案，不视为通用消费者账号系统。
- 若评估让所有有效邮箱用户通过 Access OTP，先核对当前 Zero Trust 计划、用户席位和成本。Cloudflare 官方资料将免费方案描述为最多 50 个用户；大量消费者登录前需选定可持续的应用级身份方案，避免把 Access 席位成本转嫁成意外账单。参考：[Access seat management](https://developers.cloudflare.com/cloudflare-one/team-and-resources/users/seat-management/)、[One-time PIN login](https://developers.cloudflare.com/cloudflare-one/integrations/identity-providers/one-time-pin/)、[Access policies](https://developers.cloudflare.com/cloudflare-one/access-controls/policies/)。
- 公开用户入口保持可注册/登录的产品体验；管理入口、内部 API 与运维入口继续使用更严格的策略。任何新增 hostname 在 Access 身份策略、应用自身认证、公开分享需求和移动端登录体验都确认前，不得直接套用管理员策略或暴露无鉴权服务。
