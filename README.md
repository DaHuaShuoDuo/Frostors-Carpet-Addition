# Frostor's Carpet Addition

一个 [Carpet](https://github.com/gnembon/fabric-carpet) 附属模组，Java 包名与 Maven
group 为 `top.frostor`。

> **声明：本项目的代码由 AI 辅助完成。** 需求、功能设计与发布由 Frostor 负责，实现代码在 AI 辅助下生成并经人工审阅。

## 支持的版本

| Minecraft | Fabric Loader | Carpet | Fabric API |
| --- | --- | --- | --- |
| 26.2 | >= 0.19.3 | >= 26.2 < 26.3 | >= 0.152.1 < 0.153.0 |
| 26.3 | >= 0.19.5 | >= 26.3 < 26.4 | >= 0.160.5 < 0.161.0 |

对应产物名为 `frostors-carpet-addition-fabric-<Minecraft 版本>`。

## 依赖

- [Carpet](https://github.com/gnembon/fabric-carpet)（必需，`fabric.mod.json` 中声明为 `depends`）
- [Fabric API](https://modrinth.com/mod/fabric-api)（必需）
- Java 25

## 构建

```powershell
$env:JAVA_HOME="C:\Program Files\Zulu\zulu-25"
.\gradlew.bat :26.2:build :26.3:build
```

产物位于 `versions/<版本>/build/libs/`。

Carpet 仅以 `compileOnly` 方式引用（`libs/fabric-carpet-<版本>.jar`），不会被打进产物。

两个子项目的 `build` 都会运行无需启动游戏的骑乘链规则测试，并编译 Fabric GameTest。
若已明确同意 Minecraft EULA，可额外执行服务端多人运行期测试：

```powershell
.\gradlew.bat :26.2:runGameTest :26.3:runGameTest -PacceptMinecraftEula=true
```

客户端 Mixin 加载测试：

```powershell
.\gradlew.bat :26.2:runClientGameTest :26.3:runClientGameTest -PacceptMinecraftEula=true
```

`acceptMinecraftEula=true` 会让测试运行目录自动写入 EULA 接受标记。不传该参数时不会
启动游戏测试，也不会自动接受 EULA。客户端测试首次运行可能需要下载 Minecraft
资源；它用于确认客户端 Mixin 能正常应用，真实准星命中体验仍应进行一次手动验证。

## 规则

安装后可用 `/carpet <规则名>` 查看或修改，规则随世界保存在 `carpet.conf`。

| 规则 | 默认值 | 说明 |
| --- | --- | --- |
| `easyPlaceProtocolExtra` | `false` | 启用服务端轻松放置协议 V2-Extra 握手与方块状态同步 |
| `playerLadder` | `false` | 人梯功能总开关 |
| `playerLadderClickMode` | `ride` | 右键行为：`ride` / `pick_up` / `none` |
| `playerLadderPickupDepthLimit` | `16` | 举起时向上寻找的最大层数 |
| `playerLadderRideDepthLimit` | `16` | 骑乘时向上寻找的最大层数 |
| `playerLadderAllowNonPlayers` | `false` | 是否允许非玩家实体作为骑乘或举起对象 |
| `playerLadderIgnoredEntities` | 见下 | 逗号分隔的忽略实体 id / 标签 |
| `playerLadderDismountOnGameModeChange` | `false` | 切换游戏模式时是否让乘客下马 |
| `playerLadderAllowRiderStacking` | `true` | 是否允许 3 人及以上的玩家堆叠（关闭时仅允许两位玩家骑乘） |
| `playerLadderAllowFakePlayers` | `false` | 是否允许骑乘/举起 Carpet 假人 |
| `playerLadderClientAimingFix` | `true` | 客户端准星修正（仅客户端生效） |

`playerLadderIgnoredEntities` 默认值：

```
minecraft:wither,minecraft:ender_dragon,minecraft:warden,minecraft:minecart,#minecraft:boat,#minecraft:dismounts_underwater
```

> 该模组原先的 `config/playerladder.json` 已完全移除，所有设置改由 Carpet 规则承载，
> 随世界保存在 `carpet.conf` 中，可用 `/carpet <规则名> <值>` 修改。

### 轻松放置协议 V2-Extra

`easyPlaceProtocolExtra` 仅实现服务端部分：兼容使用 EarlyCompat 通道的客户端握手、解析命中位置中的 V2-Extra 状态值，并在方块放置后恢复方向、朝向、开关、堆叠数量等原版方块状态。只要规则开启且客户端完成 V2-Extra 握手，服务端就会保持普通轨、充能轨和探测轨的投影形状，不依赖客户端额外的“铁轨不更新”子配置；客户端编码、Litematica 集成和三项“移植”配置由 TriWasa 提供。

在单人游戏或集成服务器中，FCA 检测到 TriWasa 时会复用 TriWasa 的协议
Payload/Codec；TriWasa 负责客户端编码，FCA 负责同一进程内的服务端处理。TriWasa
会在 Litematica 与 `litematica-printer` 的真实调用点执行编码，因此可与 MasaGadget
开启的 `fixAccurateProtocol` 共存。


新增规则的方式：

1. 在 `top.frostor.carpet.FrostorSettings` 中添加 `@Rule(categories = RuleCategory.FEATURE) public static ...` 字段，字段名即规则名（大小写敏感）。
2. 在 `src/main/resources/assets/frostors-carpet-addition/lang/en_us.json`（以及 `zh_cn.json`）中补充 `carpet.rule.<字段名>.name` 与 `carpet.rule.<字段名>.desc`。缺少 `.desc` 时 Carpet 会拒绝注册该规则。
3. 在代码中直接读取该静态字段即可。

## 项目结构

```
.
├── common.gradle                 # 各版本共享的构建脚本
├── gradle.properties             # 全局属性（mod 版本、loom、loader）
├── libs/                         # Carpet 编译期依赖（不打包）
├── settings.gradle               # 26.2 / 26.3 两个子项目
├── src/main/java/top/frostor/carpet
│   ├── FrostorsCarpetAddition.java   # 入口：注册 Carpet 扩展、加载翻译
│   ├── FrostorSettings.java          # Carpet 规则
│   └── playerladder                  # 由 PlayerLadder 移植的人梯功能
│       ├── PlayerLadder.java         # 事件注册（UseEntity / 断线）
│       ├── SharedHandler.java        # 骑乘 / 举起 / 下马逻辑
│       ├── ClickMode.java
│       └── mixin/                    # 玩家 tick、游戏模式、载具序列化、准星修正
├── src/main/resources
│   ├── fabric.mod.json
│   ├── frostors-carpet-addition.mixins.json
│   └── assets/frostors-carpet-addition/lang/
├── src/test/java                    # 无外部依赖的骑乘链规则测试
├── src/gametest                     # Fabric GameTest 运行期测试
└── versions/
    ├── 26.2/gradle.properties
    └── 26.3/gradle.properties
```

## 工作方式

Carpet 26.x 不再通过入口点自动发现扩展，因此本模组在自己的 `main` 入口点里调用
`CarpetServer.manageExtension(this)`。Carpet 的 `server` / `client` 入口点会在所有 `main`
入口点之后执行，其 `onGameStarted()` 会依次回调扩展，注册规则与翻译。

## 许可证

[MIT](LICENSE)。基于 Carpet 生态开发，未复制 Carpet 源码。

人梯功能改编自 [PlayerLadder](https://github.com/ForwarD-NerN/PlayerLadder)，
原作者为 ForwarD-NerN，原项目使用 MIT 许可证。

轻松放置协议 V2-Extra 与方块状态适配逻辑改编自
[EarlyCompat](https://github.com/chenjunfu2/EarlyCompat)，原作者为 chenjunfu2，原项目使用
[CC0-1.0](https://github.com/chenjunfu2/EarlyCompat/blob/master/LICENSE)。详情见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

依赖校验和与来源追踪事项见 [TODO.md](TODO.md)。
