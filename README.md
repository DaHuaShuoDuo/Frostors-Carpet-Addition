# Frostor's Carpet Addition

Frostor's Carpet Addition（FCA）是一个面向 Minecraft Java 版的
[Carpet](https://github.com/gnembon/fabric-carpet) 附属模组，提供服务端大末影箱、
Player Ladder、轻松放置协议 V2-Extra 和正版资料回退等功能。

> **AI 辅助开发声明：** 本项目的部分代码、测试和文档由生成式 AI 辅助编写，并由
> 项目维护者审阅。功能设计、发布决策、最终审查和维护责任由项目维护者承担。

模组 ID 为 `frostors-carpet-addition`，Java 包名为 `top.frostor.carpet`。
FCA 的主要逻辑运行在服务端。除明确标记为客户端规则的选项外，功能不依赖客户端安装
FCA 或其他特定客户端模组。

## 支持版本

| Minecraft | Fabric Loader | Fabric API | Carpet |
| --- | --- | --- | --- |
| 26.2 | >= 0.19.3 | >= 0.152.1 | >= 26.2 < 26.3 |
| 26.3 | >= 0.19.5 | >= 0.160.5 | >= 26.3 < 26.4 |

项目要求 Java 25。安装时必须选择与 Minecraft 版本对应的 FCA 构建。

## 安装

将 FCA 放入服务端的 `mods` 目录，同时安装对应版本的 Fabric Loader、Fabric API 和
Carpet。客户端只有需要使用客户端规则或兼容功能时才需要安装 FCA。

FCA 的规则由 Carpet 管理，并随世界配置保存。使用以下命令查看全部规则：

```text
/carpet list fca
```

规则名区分大小写，可用 `/carpet <规则名>` 查看，或用
`/carpet <规则名> <值>` 修改。

## 规则

| 规则 | 默认值 | 说明 |
| --- | --- | --- |
| `bigEnderChest` | `false` | 启用服务端大末影箱 |
| `bigEnderChestPageLimit` | `64` | 每名玩家的额外存储页上限；`1..1024`，`-1` 表示不设配置上限 |
| `bigEnderChestLiveFakePlayers` | `false` | 是否让实际发生存取的页面假人真实上线 |
| `easyPlaceProtocolExtra` | `false` | 启用服务端轻松放置协议 V2-Extra |
| `onlineProfileFallback` | `false` | 在正版模式下提供用户名解析与资料回退 |
| `playerLadder` | `false` | 启用 Player Ladder |
| `playerLadderClickMode` | `ride` | 右键行为：`ride`、`pick_up` 或 `none` |
| `playerLadderPickupDepthLimit` | `16` | 举起实体时向上查找的最大层数 |
| `playerLadderRideDepthLimit` | `16` | 骑乘实体时向上查找的最大层数 |
| `playerLadderAllowNonPlayers` | `false` | 是否允许非玩家实体进行骑乘或举起 |
| `playerLadderIgnoredEntities` | 见下文 | 逗号分隔的忽略实体 ID 或 `#` 标签 |
| `playerLadderDismountOnGameModeChange` | `false` | 载体切换游戏模式时是否让乘客下马 |
| `playerLadderAllowRiderStacking` | `true` | 是否允许两名以上玩家继续堆叠 |
| `playerLadderAllowFakePlayers` | `false` | 是否允许与 Carpet 假人进行骑乘或举起 |
| `playerLadderClientAimingFix` | `true` | 客户端准星修正，仅客户端生效 |

`playerLadderIgnoredEntities` 默认值：

```text
minecraft:wither,minecraft:ender_dragon,minecraft:warden,minecraft:minecart,#minecraft:boat,#minecraft:dismounts_underwater
```

## 大末影箱

启用 `bigEnderChest` 后，玩家使用末影箱会打开一个原版 `GENERIC_9x6` 菜单，无论手中
是否持有物品。手持物品不会阻止大末影箱打开，也不会在该交互中被消耗或写入存储。
未安装客户端工具的玩家仍可使用完整的聚合查看、取放、排序、翻页和丢弃功能。

### 原版 6×9 菜单

- 顶部 `5×9` 个槽位显示所有存储页聚合后的物品模板。
- 底部 9 个槽位提供首页、上一页、页码、下一页、末页、排序字段、排序方向和刷新。
- 物品保持原版名称、组件和图标；完整库存量显示在 Lore 中。
- 左键取一组；光标持有物品时左键存入。
- Shift + 左键将一组物品移到玩家背包。
- 右键取半组，数量按可见组容量向上取整。
- Q 丢弃一个；Ctrl + Q 丢弃一组。
- 玩家背包装满时，Shift + 左键仍可存入聚合存储。
- 查看页只影响聚合列表的翻页和排序，不会创建新的物理存储页。

聚合槽是服务端管理的虚拟槽。控制项不能被交换、丢弃、复制或作为普通物品取出。
服务端限制连续点击和整理模组提交的批量虚拟操作，并以服务端事务结果纠正客户端状态。

### 存储页

页 0 直接映射玩家自己的原版 27 格末影箱，不复制其内容。额外存储页由不参与游戏 tick 的
假人档案承载，每页仍为标准的 27 格 `EnderItems` 容器。

页面身份、假人 UUID、名称和排序偏好保存在世界 `data` 目录的 FCA 元数据文件中；
页面物品保存在服务器原版 `playerdata` 中。元数据和页面档案使用后台 I/O、临时文件与
原子替换保存，服务器停止时会等待待处理写入完成。

页面按实际容量需求生成。首次打开会准备少量缓冲页；查看、翻页和排序不会触发扩容。
降低 `bigEnderChestPageLimit` 不会删除已有页面，已有页面仍可访问，但不会继续扩容。

如果额外页对应的假人已经通过 Carpet `/player <名称> spawn` 真实上线，FCA 会直接使用
该在线假人的末影箱容器，并把外部槽位变化同步进聚合索引。在线期间不会同时读写该 UUID
的磁盘档案；假人下线后页面自动切回 `playerdata` 路径。

`bigEnderChestLiveFakePlayers` 默认关闭。开启后，建立索引、搜索、排序和只读浏览仍不会
上线假人；只有页面实际发生存入或取出时，FCA 才会请求该页面对应的假人上线，并保持在线
至页面连续 200 tick 未被使用。关闭末影箱菜单只结束当前会话，不会立即下线页面假人；
页面假人仅在空闲超时、功能规则关闭或服务器停止时回收，手动上线的假人不受影响。自动
页面假人直接使用 FCA 保存的本地 UUID 与名称，不查询 Mojang 档案；上线时强制使用旁观
模式和飞行状态，避免等待网络或受重力坠落。

关闭 `bigEnderChest` 只会隐藏功能，不删除页面或物品。再次开启后可继续读取。

额外页名称采用固定 16 字符的可读格式：

```text
fec_p<36 进制页号>_<玩家名前缀><所有者摘要>
```

名称在创建时写入服务器用户名缓存，后续启动保持稳定。

### 会话与来源校验

从末影箱方块打开时，FCA 会接管原版箱盖动画、开启计数和音效。关闭菜单、关闭规则、
断线或会话失效时，箱盖动画会正常结束。

通过原版末影箱方块打开的大末影箱会话会检查来源方块：

- 玩家离开来源末影箱 8 格以上或切换维度后，终端会话失效。
- 协议终端动作要求玩家仍打开当前会话菜单。
- Quick Shulker 等没有方块来源的外部会话沿用其自身的打开条件。

### Quick Shulker

Quick Shulker 使用原版三行末影箱菜单打开玩家末影箱时，FCA 会在服务端将它替换为
`GENERIC_9x6` 大末影箱。规则关闭时保留 Quick Shulker 原本的 27 格行为。

该兼容逻辑只注册在服务端，不修改 Quick Shulker 的客户端界面，也不要求 FCA 客户端。

### 客户端工具协议

FCA 不包含自定义终端 GUI。兼容的客户端工具可以在原版 6×9 菜单之外实现独立终端。

协议通道：

```text
frostors-carpet-addition:big_ender_chest
```

FCA 通过
`top.frostor.carpet.bigenderchest.api.BigEnderChestProtocol.Payload` 公开该通道的
Payload、消息 DTO 和 Codec。TriWasa 在编译期引用该 API，并在 FCA 同进程加载时直接复用
同一 Payload 类；客户端未安装 FCA 时才使用自身的线兼容实现。

当前协议版本为 `2`。未完成握手的客户端继续使用原版 6×9 菜单；完成握手并声明终端能力
的客户端使用：

```text
frostors-carpet-addition:big_ender_chest_terminal
```

客户端可以在会话中重新发送 `HELLO` 增删终端能力。服务端会关闭当前会话并按新能力重开
同一位置的菜单，因此关闭终端能力时回退到原版 6×9 菜单，重新声明时恢复终端。

终端菜单只同步玩家背包和 carried item，不同步 45 个原版展示槽。协议包含：

- 双方能力交集和服务端会话信息。
- 页面目录、页面状态、容量和只读状态。
- 从完整聚合索引生成的物品模板快照。
- CRC32 分块、`SnapshotEnd`、ACK、缺块重传和主动重同步。
- 模板新增、删除、总量变化和页面 revision Delta。
- 按 `templateSerial` 和数量执行的服务端权威取放。
- 批量存入、批量移回玩家背包、丢弃、动作序号、请求幂等和每 tick 限流。
- 菜单关闭、断线、规则关闭和会话失效通知。

`ActionRequest.playerSlots` 和 `QUICK_MOVE_FROM_PLAYER.button` 使用玩家
`Inventory` 索引 `0..35`，不是菜单槽位索引。协议动作不会直接提交受信任的物品栈，
服务端会重新校验菜单、会话、revision、页面状态、模板和玩家背包。

## 轻松放置协议 V2-Extra

`easyPlaceProtocolExtra` 提供 Servux 兼容的服务端轻松放置语义，通过 EarlyCompat
网络通道接收客户端精确状态。客户端编码、Litematica 投影读取和按键交互不包含在本模组中。

FCA 公开 `top.frostor.carpet.easyplace.EasyPlaceExtraPayload`，由它定义
`earlycompat:network` 通道、消息 DTO 与 Codec。TriWasa 在编译期引用该公开 API，并在
FCA 同进程加载时直接复用同一 Payload 类；未安装 FCA 的客户端连接专用服务器时才使用
TriWasa 自身的线兼容 Payload。

服务端会恢复或同步包括方向、朝向、开关、堆叠数量、床首位、蛋糕咬数、铜傀儡姿态、
铜灯通电、门上下半、双箱子配对和多面方块附着面等原版状态。

规则开启且客户端完成握手后，FCA 会放宽 V2-Extra 编码命中位置的原版中心距离限制，
但仍执行玩家触及范围和最终 `BlockItem.canPlace` 校验。该放宽不依赖 Carpet 的
`antiCheatDisabled`。

依赖行为以 FCA 自身实现为准，不根据服务端是否安装 Servux 切换状态语义。

## Player Ladder

`playerLadder` 开启后，玩家可以按照规则设置骑乘或举起实体。功能支持 Carpet 假人、
玩家堆叠、忽略实体列表、深度限制和游戏模式变化处理。

- `ride`：右键让目标成为载具。
- `pick_up`：右键将目标举到玩家上方。
- `none`：关闭右键交互，但保留相关状态清理逻辑。

`playerLadderAllowNonPlayers`、`playerLadderAllowFakePlayers`、
`playerLadderAllowRiderStacking` 和两个深度限制可分别控制允许对象与遍历范围。

`playerLadderClientAimingFix` 是客户端规则，只影响堆叠玩家的准星选择；服务端逻辑和
骑乘结构不依赖该规则。

## 在线资料回退

`onlineProfileFallback` 仅用于特定的私人服务器场景：当玩家用户名对应的正版 UUID
已存在于服务器玩家档案中时，允许正版模式登录继续解析该用户名和历史皮肤/披风资料。

该功能不验证账号所有权，不能替代 Mojang 会话验证，不应在公开或不可信服务器上开启。

## 构建

使用项目自带的 Gradle Wrapper，并将 `JAVA_HOME` 指向 Java 25。不要使用 Java 17
或 Java 21 代替。

PowerShell 示例：

```powershell
$env:JAVA_HOME = '<Java 25 installation>'
.\gradlew.bat :26.2:build :26.3:build
```

如果 Gradle 无法在默认位置创建缓存，可以仅为当前命令指定用户缓存：

```powershell
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
```

FCA 不会把开发依赖 JAR 提交到版本库。构建前请按
[`libs/README.md`](libs/README.md) 中的文件名，将对应版本的 Carpet 编译依赖放入
`libs/`。依赖文件和构建产物都不会被打包或发布。

共享源码、资源或 Mixin 修改后必须同时构建 26.2 和 26.3。构建会运行不启动游戏的逻辑和
协议测试，并验证两个版本均可打包。

## 数据与兼容性

- 规则保存在世界的 `carpet.conf` 中，不使用独立的 `config/playerladder.json`。
- 大末影箱页面元数据保存在世界目录内，不写入客户端配置。
- Carpet 仅作为编译期依赖引用，不会被打进 FCA 产物。
- Quick Shulker 兼容路径不要求将其编译依赖或二进制打包进 FCA。
- 客户端工具协议不包含终端 GUI；兼容客户端由独立项目实现。

## 项目结构

```text
src/main/java/top/frostor/carpet
├── bigenderchest/      大末影箱存储、页面、菜单和工具协议
├── easyplace/          轻松放置 V2-Extra 服务端实现
├── onlineprofile/      在线资料回退
├── playerladder/       Player Ladder
├── FrostorSettings.java
└── FrostorsCarpetAddition.java

src/main/resources      元数据、Mixin 配置和翻译
src/test/java           无游戏运行期的逻辑与协议测试
versions/26.2           26.2 版本属性
versions/26.3           26.3 版本属性
```

FCA 在 `FrostorsCarpetAddition.onInitialize()` 中通过
`CarpetServer.manageExtension(this)` 注册 Carpet 扩展，并在游戏启动后注册
`FrostorSettings` 中的规则。

## 许可证与第三方来源

FCA 本身使用 [MIT License](LICENSE)。

Player Ladder 改编自 ForwarD-NerN 的 PlayerLadder（MIT）。轻松放置协议 V2-Extra
的服务端实现改编自 chenjunfu2 的 EarlyCompat（CC0-1.0）。其他行为参考和来源说明见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
