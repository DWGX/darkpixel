# DarkPixel - Minecraft Server Plugin

![GitHub release](https://img.shields.io/github/v/release/DWGX/darkpixel?color=blue)
![GitHub license](https://img.shields.io/github/license/DWGX/darkpixel)

---

## 项目简介 (Project Overview)

**DarkPixel** 是一个专为 **Minecraft 1.21.4** 设计的服务器插件，集成了多种实用功能，旨在提升服务器的互动性、管理效率和游戏乐趣。无论你是想打造一个智能化的社区服务器，还是需要强大的反作弊保护，DarkPixel 都能满足需求。

**DarkPixel** is a powerful server plugin designed for **Minecraft 1.21.4**, integrating a variety of practical features to enhance server interactivity, management efficiency, and gameplay enjoyment. Whether you aim to create an intelligent community server or need robust anti-cheat protection, DarkPixel has you covered.

---

## 核心功能 (Key Features)

### 中文
- **AI 聊天**：与智能助手对话，支持公开/私聊、模型切换（如 DeepSeek），并提供消息限制和历史记录。
- **NPC 系统**：生成大厅助手（僵尸形态），引导玩家进入仪表盘或小游戏。
- **反作弊保护**：检测多种作弊行为，包括快速移动、飞行外挂、击杀光环等。
- **仪表盘 GUI**：直观的界面，支持小游戏导航、玩家观战和聊天次数领取。
- **玩家交互**：实现格挡机制（带剑）、冻结玩家、坐下功能，增强游戏沉浸感。
- **配置灵活**：通过多个配置文件（如 `config.yml`、`minigame.yml`）自定义功能。

### English
- **AI Chat**: Interact with an intelligent assistant, supporting public/private chat, model switching (e.g., DeepSeek), with message limits and history tracking.
- **NPC System**: Spawn lobby assistants (zombie entities) to guide players to the dashboard or minigames.
- **Anti-Cheat Protection**: Detects various cheats, including fast movement, fly hacks, and kill aura.
- **Dashboard GUI**: An intuitive interface for minigame navigation, player spectating, and chat limit collection.
- **Player Interactions**: Implements blocking mechanics (with swords), player freezing, and sitting features for immersive gameplay.
- **Flexible Configuration**: Customize features via multiple config files (e.g., `config.yml`, `minigame.yml`).

---

## 依赖 (Dependencies)
- **ProtocolLib**: 用于数据包处理。
- **NBTAPI**: 用于操作物品 NBT 数据。

---

## 安装步骤 (Installation)

### 中文
1. 下载 `DarkPixel.jar` 并放入服务器的 `plugins` 文件夹。
2. 确保安装了依赖插件（ProtocolLib 和 NBTAPI）。
3. 启动服务器，自动生成配置文件。
4. 编辑 `config.yml`、`minigame.yml` 等文件，根据需要调整参数。

### English
1. Download `DarkPixel.jar` and place it in the `plugins` folder of your server.
2. Ensure the dependency plugins (ProtocolLib and NBTAPI) are installed.
3. Start the server to auto-generate configuration files.
4. Edit `config.yml`, `minigame.yml`, etc., to tweak settings as needed.

---

## 使用示例 (Usage Examples)

### 中文
- `/aichat public 你好` - 公开向 AI 发送消息。
- `/dashboard` - 打开服务器大厅仪表盘。
- `/freeze Steve on` - 冻结玩家 Steve。
- `/npc spawn helper1` - 在当前位置生成一个 ID 为 "helper1" 的大厅 NPC。
- `/sit` - 在方块上坐下。

### English
- `/aichat public Hello` - Send a public message to the AI.
- `/dashboard` - Open the server lobby dashboard.
- `/freeze Steve on` - Freeze the player Steve.
- `/npc spawn helper1` - Spawn a lobby NPC with ID "helper1" at your location.
- `/sit` - Sit on a block.

---

## 配置说明 (Configuration)

- **`config.yml`**: 主配置文件，包含 AI 设置、NPC 位置、功能开关等。
- **`minigame.yml`**: 定义小游戏传送点和显示信息。
- **`commands.yml`**: 存储 AI 可识别的 Minecraft 命令模板。
- **其他文件**: 如 `chat_history.yml`、`darkac.yml` 等，用于保存聊天历史和反作弊数据。

---

## 贡献 (Contributing)

欢迎提交 **Issues** 或 **Pull Requests**！我们期待你的建议和代码贡献：
1. Fork 本仓库。
2. 创建你的功能分支（`git checkout -b feature/new-feature`）。
3. 提交更改（`git commit -m "Add new feature"`）。
4. 推送到分支（`git push origin feature/new-feature`）。
5. 创建 Pull Request。

---

## 社区支持 (Community)

- **Discord**: 加入我们的社区 [https://discord.gg/sMx9HTHr](https://discord.gg/sMx9HTHr)，获取帮助或交流想法。
- **Issues**: 在 GitHub 上提交问题或建议。

---

## 许可证 (License)

本项目采用 [MIT License](LICENSE) - 你可以自由使用、修改和分发。

---

## 致谢 (Acknowledgements)

感谢所有支持者和贡献者！DarkPixel 将持续优化，为 Minecraft 玩家带来更好的体验。

**让你的服务器更智能、更安全、更有趣！立即尝试 DarkPixel！**  
**Make your server smarter, safer, and more fun! Try DarkPixel now!**