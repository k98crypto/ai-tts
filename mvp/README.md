# 剧情配音 MVP

这是一个剧情配音 MVP。用户继续在 DeepSeek 免费聊天中玩剧情，可在页面展开并复制一次输出格式要求；之后粘贴最新回复、解析和编辑片段、为角色绑定模型音色，再按顺序播放和高亮字幕。也支持导入 TXT/Markdown。

语音由服务器上的 Qwen3-TTS 0.6B CustomVoice 模型生成。页面通过局域网 API 发送当前片段，服务在内存中合成 WAV，不保存请求文本或音频。模型提供五种中文预设音色，情绪标签映射为模型指令。模型卡标注 Apache-2.0；部署前应分别核对代码、模型权重和预设音色的适用许可。

## 页面部署

```sh
docker compose up -d
```

页面监听 8765 端口，TTS API 监听 8766 端口。TTS 使用服务器本机 CUDA，不依赖 Docker NVIDIA runtime。

网页容器会把同源的 `/health` 和 `/synthesize` 转发到服务器 Docker 网桥地址 `172.17.0.1:8766`。`/synthesize` 必须使用 Supabase Auth 登录会话；服务端向 Supabase 校验会话、仅按用户 ID 累计每日字符数，并限制 GPU 队列为一个执行任务和一个等待任务。每日字符上限默认 3000，可通过 `DAILY_CHARACTER_LIMIT` 调整。SQLite 只保存用户 ID、UTC 日期与字符数，不保存剧本文本或音频。

部署前在服务器的 Compose 环境配置中设置 `SUPABASE_URL` 和 `SUPABASE_PUBLISHABLE_KEY`。这两个值不会写进前端源码；公开 publishable key 由同源 `/config` 下发，服务端仅用它调用 `/auth/v1/user` 校验访问令牌。未配置时注册、登录和合成保持关闭。Supabase Auth 邮箱确认链接的 Site URL / Redirect URLs 必须配置为实际产品域名；大量注册前还需要配置自有 SMTP 并核实投递限额。

管理员邮箱通过服务器私有 `.env` 中的 `ADMIN_EMAILS` 配置，多个邮箱用逗号分隔。管理员登录后可从页眉打开 `/admin`，查看 TTS 使用量、调整单账号每日字符上限、封禁或恢复账号的 TTS 合成权限。账号列表只包含有合成记录的 Supabase 用户 ID，不读取邮箱或剧本文本；账号注册、删除等 Auth 操作仍在 Supabase 控制台完成。

网页容器只在服务器 `127.0.0.1:8765` 监听，Qwen API 只在默认 Docker 网桥网关 `172.17.0.1:8766` 监听；发布前应确认 Docker 网桥仍使用该网关。公网 HTTPS 域名需要 Cloudflare Tunnel 的 Public Hostname 路由到 `http://127.0.0.1:8765`。Cloudflare Access 只保护私人管理 hostname，不覆盖消费者域名；消费者登录由 Supabase Auth 负责。DNS 记录存在本身不会建立 Tunnel 路由。

## TTS 服务

服务器需要 NVIDIA 驱动、Python 3.12 venv 支持和约 3 GB 模型空间。Qwen3-TTS 代码包与推理依赖安装到隔离环境，权重放在 `/home/bonjour/tts-model`：

```sh
python3 -m venv /home/bonjour/qwen-tts-venv
/home/bonjour/qwen-tts-venv/bin/pip install --no-deps qwen-tts==0.1.1
/home/bonjour/qwen-tts-venv/bin/pip install -r requirements-tts.txt
```

下载权重时可使用 `huggingface_hub.snapshot_download`，仓库为 `Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice`。API 由 `tts_server.py` 提供；服务启动后检查 `http://服务器地址:8766/health`。

前端和 API 同源运行，不启用跨域访问。合成 API 的身份校验位于网页代理，`8766` 不应直接暴露给公网或用户设备。

## 冒烟测试

```sh
node smoke-test.cjs
```
