# Frostor's Carpet Addition

一个 [Carpet](https://github.com/gnembon/fabric-carpet) 附属模组，Java 包名与 Maven
group 为 `top.frostor`。

> **声明：本项目的代码由 AI 辅助完成。** 需求、功能设计与发布由 Frostor 负责，实现代码在 AI 辅助下生成并经人工审阅。

## 支持的版本

| Minecraft | Fabric Loader | Carpet | Fabric API |
| --- | --- | --- | --- |
| 26.2 | >= 0.19.3 | >= 26.2 < 26.3 | >= 0.152.1 |
| 26.3 | >= 0.19.5 | >= 26.3 < 26.4 | >= 0.160.5 |

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
FCA 的全部规则同时归入 `fca` 分类，可使用 `/carpet list fca` 集中查看。

| 规则 | 默认值 | 说明 |
| --- | --- | --- |
| `bigEnderChest` | `false` | 服务端大末影箱总开关 |
| `bigEnderChestPageLimit` | `64` | 额外假人存储页上限（不含原版页 0），有效值 `1..1024`；`-1` 表示无限 |
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

### 大末影箱

`bigEnderChest` 开启后，空手使用原版末影箱会打开一个 `GENERIC_9x6` 菜单：顶部
`5×9` 格显示全部存储页聚合后的物品模板，底部 9 格固定为首页、上一页、页码、下一页、
末页、排序字段、排序方向和刷新，最后一个为不可交互的预留占位。控制项不会进入玩家背包，
也不能被交换、丢弃、双击收集或通过第三方批量点击修改。聚合条目保持原版名称和组件，物品
图标右下角按原版规则显示至多一组，完整库存量追加在物品 Lore 中，不再改写物品名称。查看
页的翻页和排序不会创建存储页。

页 0 始终直接映射玩家自己的原版 27 格末影箱，不复制其内容。额外页由不会进入
`PlayerList`、不会生成 `ServerPlayer`、不会参与 tick 的假人档案承载，每页仍是一个标准
27 格 `EnderItems` 容器。页面身份、名称和 UUID 保存在世界 `data` 目录的元数据文件中；
物品本身只写入对应 `playerdata/<uuid>.dat`，并以临时文件、原子替换和 `.dat_old` 备份保存。

页面元数据中的 owner 只属于创建该页的玩家。首次打开会准备两页缓冲；查看页滚动、排序或
搜索不会触发扩容。实际需要写入更多槽位时才按整页预留，并受
`bigEnderChestPageLimit` 限制。有限模式有效值为 `1..1024`；设为 `-1` 时不再应用配置
页数上限，但实际仍受磁盘、内存、整数页号和服务器性能限制。降低上限不会删除已有页面，
已有页面仍可读取和操作，但不会继续扩容；关闭规则会隐藏 FCA 菜单并保留全部数据。

每位玩家最后使用的排序字段和排序方向按 UUID 保存在同一世界级元数据文件中，重新打开
菜单或重启服务器后自动恢复。

页面空档案、元数据和脏页快照写入由有界 I/O 执行器串行完成，服务端主线程只负责生成内存
页面和当前内容的 NBT 快照。`SAVING` 状态仍允许存取；保存期间继续发生的修改会把页面重新
标为 `DIRTY`，由后续保存写入最新版本。服务器停止时会等待待处理写入完成。

原版菜单的存入和取出语义：

- 玩家背包槽 Shift + 左键存入 `BigEnderChestService`。即使当前 45 个查看槽已满也可以
  存入新模板。
- 光标为空时左键查看条目，取一组到光标；光标持有物品时左键查看条目则存入。查看条目
  Shift + 左键直接移动一组到玩家背包。
- 光标为空时右键查看条目，取该条目一组容量的一半（向上取整）到光标。
- 鼠标悬浮查看条目时按 Q 丢弃一个；按住 Q 产生的重复丢弃请求每次 tick 最多提交一个。
  Ctrl + Q 丢弃一组。
- 顶部 45 个聚合查看槽是虚拟槽。每个服务端 tick 最多提交一个查看/控制操作；同一 tick
  出现多个虚拟槽点击时全部丢弃并回推完整状态，避免整理模组或重复点击造成批量取物。
  右击、拖拽、交换、丢弃、双击收集和其它非白名单动作仍会被拒绝。
- 该守卫在共同端菜单点击入口执行，客户端不会先本地整理显示物品后再等待服务端纠正。
- Shift 左键、右键半组和丢弃均从原版容器点击包在服务端解析，不要求服务端另装客户端桥接。
  纯服务端部署时客户端可能短暂显示本地虚拟槽变化，服务端事务完成后会广播真实状态。
- 同模板优先补齐已有非满堆，再按稳定的页面/槽位顺序使用空槽；容量不足时不移动源物品。

从原版末影箱方块打开时，FCA 会把 6×9 会话绑定到该方块实体的原版开启计数，并在关闭菜单、
切换规则或断线时释放；箱盖动画、开合音效和原版容器开启事件会在会话结束时正常收尾。

安装客户端工具的玩家可在同一服务端会话上使用独立终端 GUI。FCA 协议通道为
`frostors-carpet-addition:big_ender_chest`，协议版本为 `2`。未握手客户端继续使用原版
6×9 聚合菜单；声明终端菜单能力的客户端改用
`frostors-carpet-addition:big_ender_chest_terminal` 专用菜单，该菜单只同步玩家背包和
carried item。Hello/Ack 返回双方能力交集，不支持 Delta 的客户端只接收快照。

协议会发送页面目录，以及从完整聚合索引生成的 `EntryRecord` 快照；快照分块带 CRC32，
并以 `SnapshotEnd`、`SnapshotAck`、缺块重传和 `RequestResync` 收尾。Delta 发送模板新增、
删除、总量、页面状态和容量变化。动作使用 `templateSerial + amount`，由服务端选择物理
槽位，并支持批量存入、批量移动到玩家背包、丢弃、动作序号、请求幂等以及每 tick 限流。
菜单关闭、断线、规则关闭或会话失效时发送 `SessionClose`。

要求 ACK 的快照若 5 秒内未确认会重新生成，客户端主动重同步最多每秒接受一次，避免异常
客户端堆积后台快照任务。`OpenTerminalRequest` 只接受当前玩家的既有会话、当前 `containerId`
和原箱子 8 格范围内的请求；QuickShulker 等无方块来源的外部会话沿用其自身打开校验。

页面列表加载期间会话标记为只读；页面加载完成后重建索引并结束只读。快照在后台生成，
分块按 tick 发送，不依赖所有页面在菜单打开时同步阻塞加载。终端 GUI、按键和本地搜索
仍由独立客户端项目实现。

QuickShulker 的快捷末影箱也走同一功能入口。FCA 会识别 QuickShulker 为
`PlayerEnderChestContainer` 创建原版三行菜单的服务端路径，并在规则开启时替换为
`GENERIC_9x6` 大末影箱；规则关闭时仍保留 QuickShulker 原本的 27 格行为。

额外页假人仍可通过 Carpet `/player <名称> spawn` 上线并访问同一个标准末影箱；页面 uuid
和名称会写入服务器用户名缓存，并在后续启动时复用，不会随机改名。

新页面的假人名称采用固定 16 字符的可读格式：

```text
fec_p<36进制页号>_<玩家名前4字符><4位所有者摘要>
```

例如 `fec_p01_Frosa1b2` 表示 Frostor 的第 1 个额外存储页。页号使用 36 进制，以便在
Minecraft 16 字符名称限制内保留完整所有者隔离；末尾摘要防止不同玩家或页面冲突。
已有页面保持原名，不会因格式升级而迁移或破坏既有 `playerdata`。

### 轻松放置协议 V2-Extra

`easyPlaceProtocolExtra` 仅实现服务端部分：兼容使用 EarlyCompat 通道的客户端握手、解析命中位置中的 V2-Extra 状态值，并在方块放置后恢复方向、朝向、开关、堆叠数量等原版方块状态。只要规则开启且客户端完成 V2-Extra 握手，服务端就会保持普通轨、充能轨和探测轨的投影形状，不依赖客户端额外的“铁轨不更新”子配置；客户端编码、Litematica 集成和三项“移植”配置由 TriWasa 提供。

服务端状态语义直接对齐 Servux 的精准放置行为，不根据服务端是否安装 Servux 切换实现。
因此同一套规则不会因 Servux 的安装状态、加载顺序或版本产生不同结果。合成器锁槽、
现有的红石点状状态和拉杆通电映射保持 FCA 的既定行为。新增的兼容状态包括床首位占用
校验、蛋糕咬数、铜傀儡姿态、铜灯开关和通电状态，以及门上下半状态。双箱子使用原版
配对语义并在每次放置后归一化，连续或间隔放置都会形成 54 格组合容器。
多面方块会基于目标位置的世界状态合并附着面，避免密集方块图案中因重复面而漏放。
命中点放宽由 FCA 自身完成，不要求开启 Carpet 的 `antiCheatDisabled`。

服务端 HELLO 应答和能力广播会携带 V2-Extra 协议版本、显式能力布尔值以及能力列表；
TriWasa 会校验该能力。旧版仅含 `mod_version` 的应答仍按兼容模式接受。

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
│   ├── bigenderchest                # 大末影箱存储、菜单、页面与客户端工具协议
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
