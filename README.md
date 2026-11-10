# Frostor's Carpet Addition

[English](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/README_en.md) | 简体中文

Frostor's Carpet Addition（FCA）是面向 Minecraft Java 版的
[Carpet](https://github.com/gnembon/fabric-carpet) 附属模组，提供大末影箱、
Easy Place V2-Extra、Player Ladder 和私人服务器在线资料回退等功能。

> **AI 辅助开发声明：** 部分代码、测试和文档由生成式 AI 辅助编写，并由项目
> 维护者审阅。功能设计、发布与维护责任由维护者承担。

模组 ID 为 `frostors-carpet-addition`，Java 包名为 `top.frostor.carpet`。
主要逻辑运行在服务端；除客户端规则外，不需要客户端安装 FCA。

## 支持版本

| Minecraft | Fabric Loader | Fabric API | Carpet |
| --- | --- | --- | --- |
| 26.2 | >= 0.19.3 | >= 0.152.1 | >= 26.2 < 26.3 |
| 26.3 | >= 0.19.5 | >= 0.160.5 | >= 26.3 < 26.4 |

项目要求 Java 25。必须选择与 Minecraft 版本对应的 FCA 构建。

## 安装与依赖

必需依赖：

- Java 25
- Fabric Loader
- Fabric API
- 对应 Minecraft 版本的 Carpet

可选依赖：

- Quick Shulker：允许通过其快捷键、背包操作或手持右键打开大末影箱；需要同时安装在
  客户端和服务端。

将 FCA 和上述依赖放入服务端的 `mods` 目录。客户端只有需要使用客户端规则或
可选兼容功能时才安装 FCA。

FCA 的规则由 Carpet 管理，并随世界配置保存：

```text
/carpet list fca
/playerladder
```

`/carpet list fca` 显示主规则；先启用 `/carpet playerLadder true`，再使用
`/playerladder` 查看和修改 Player Ladder 子设置。

## 功能概览

### 大末影箱

`bigEnderChest` 开启后，末影箱使用原版 `GENERIC_9x6` 菜单展示全部存储页的聚合
视图。原版客户端即可查看、取放、排序、翻页和丢弃物品。

- 页 0 直接使用玩家自己的原版末影箱。
- 额外页使用标准 27 格假人 `EnderItems` 容器，家具档案保存在 `playerdata`。
- 聚合槽是服务端管理的虚拟槽，最终状态以服务端事务结果为准。
- `bigEnderChestLiveFakePlayers` 可选地让实际发生存取的页面假人真实上线；索引和
  只读浏览仍使用离线文件。
- Quick Shulker 的快捷按键、背包操作和手持右键路径通过其公开
  `QuickOpenableRegistry` 接口打开大末影箱菜单。
- 完成 protocol v3 握手且支持 generic carrier 的客户端由 FCA 打开同一个原版 `GENERIC_9x6` carrier：54 个只读占位槽加 36 个玩家背包槽；完整物品列表仍通过协议快照和 Delta 传输。原版聚合菜单和终端 carrier 都不注册自定义 `MenuType`。

自动页面假人按页独立计时，菜单关闭不会触发统一下线；空闲回收、规则关闭和
服务器停止时会先同步数据。真实假人首次批量上线可能产生明显的 server tick 尖峰，
该路径默认关闭。

### Easy Place V2-Extra

`easyPlaceProtocolExtra` 提供 Servux 兼容的服务端轻松放置语义，通过
`earlycompat:network` 接收兼容客户端的精确方块状态编码。服务端仍执行玩家触及、
方块放置和最终 `BlockItem.canPlace` 校验；FCA 不包含客户端投影读取或放置交互。
墙挂悬挂牌遵循原版仅在放置时要求侧向支撑、支撑移除后仍保留的特性：V2-Extra
允许无侧向支撑放置，并恢复投影中的朝向；天花板悬挂牌同时恢复 `rotation` 与
`attached`，但仍校验目标可替换、碰撞和通用放置约束。蜡烛同样只在放置阶段要求
下方支撑，V2-Extra 允许无支撑放置且不改变放置后的 `updateShape` 行为。数量类方块按原版每次增加一级并将投影数量作为上限，每次交互只消耗一个对应物品。
所有原版 `WallBlock` 变体恢复投影中的四侧 `NONE/LOW/TALL` 连接和 `UP` 柱子状态；
`WATERLOGGED` 不进入协议状态，仍以服务端当前放置状态为准。墙方块放置期间会独立抑制
结构性 `updateShape` 重算，同时保留水方块 tick 等原版副作用。
任意 V2-Extra 方块放置期间，实际放置位置、四个水平邻居和上方邻居内的铁轨结构重算
会被抑制；墙体始终保护本次放置位置，相邻墙只有实际状态与投影预期一致时才保护。
墙状态比较忽略 `WATERLOGGED`，但方块种类、四侧连接或 `UP` 不符会排除该邻居。斜角、
两格以外位置以及普通非 Easy Place 放置不受影响。
这两项保护分别由 TriWasa 的 `easyPlaceRailBlockNoShapeUpdate` 和
`easyPlaceWallBlockNoShapeUpdate` 子开关控制，默认开启。
observer 的投影只恢复 `FACING`，`POWERED` 始终按未激活状态放置。在已有 observer
的检测面轻松放置方块时，本次变化不会排程 observer 脉冲；其他方向的变化仍保持原版
检测行为。该保护由 TriWasa 的 `easyPlaceObserverProtection` 子开关控制，默认开启；
关闭后 observer 放置和检测面更新均回到原版语义。

服务端状态处理使用显式、初始化后冻结的行为族注册表。稳定的
`EasyPlaceStateSync.apply(...)` 入口负责查找 owner；具体状态恢复位于
`easyplace/adapter` 下的 directional、attachment、rail、wall、observer、multiface、quantity、
container、sign、redstone 和 special 等适配器，通用纯状态操作集中在
`EasyPlaceStateSupport`。旧的 `LegacyEasyPlaceStateSync` 单体映射和 fallback
适配器已经移除。V2-Extra Payload 标记和协议版本仍为 1；辅助 Z 通道低 9 位继续用于
crafter slot mask，bit9/bit10 分别表示 rail/wall 更新保护请求，bit11-16 标记匹配的
相邻墙方向，bit17 表示 observer 保护请求。
多面方块的面索引顺序固定为 `DOWN, UP, NORTH, SOUTH, WEST, EAST`，藤蔓从同一顺序
过滤 `DOWN` 得到 `UP, NORTH, SOUTH, WEST, EAST`。使用历史面顺序构建的客户端或
服务端必须升级到匹配版本，混用版本时多面方块和藤蔓可能按错误方向解码。旧 `main`
快照保存在双方的 `legacy` 标签中，仅用于历史兼容，不再维护。实现边界和迁移记录见
[`EASY_PLACE_REGISTRY_PLAN.md`](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/EASY_PLACE_REGISTRY_PLAN.md)。

### Player Ladder

先使用 `/carpet playerLadder true` 解锁，再用 `/playerladder` 查询或修改骑乘、举起、
玩家堆叠、实体过滤和深度限制等设置。玩家互动路径可选择直接右键的 `player_ladder`，
或主副手均空手并潜行时右键的 `carry_on`；动作由 `/playerladder clickMode` 决定。
`carry_on` 成功后，当前按住 Shift 的这段时间不会下骑；松开后再次按下 Shift 才执行下骑。
功能支持 Carpet 假人。

从旧版升级时，原 `carpet.conf` 中的 `playerLadderClickMode`、深度限制、实体过滤等
子设置不会自动迁移到新的 `playerladder.conf`。升级后需要使用 `/playerladder`
重新配置；未配置的子项使用默认值。

### 客户端兼容与公开协议

FCA 不包含自定义大末影箱终端 GUI，也不注册自定义终端 `MenuType`。TriWasa 等兼容客户端可以通过 protocol v3 的 `BigEnderChestProtocol.Payload` 和 `EasyPlaceExtraPayload` 使用 FCA 定义的公开 Payload、消息 DTO 与 Codec。大末影箱终端需要与 FCA `0.2.1-beta.1` 匹配的 TriWasa 版本；原版客户端无需安装 FCA 或 TriWasa。FCA 同进程加载时，客户端直接复用这些类型，避免在同一通道注册不同 Payload 类。

### 在线资料回退

`onlineProfileFallback` 仅适用于受控的私人服务器。它不验证账号所有权，不能替代
Mojang 会话验证，不应在公开或不可信服务器上启用。

## Carpet 规则

完整规则规范见 [`RULES.md`](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/RULES.md)，包括全部规则名、默认值、作用端、合法取值、
用途和设置命令。规则名区分大小写：

```text
/carpet <规则名>
/carpet <规则名> <值>
/carpet <规则名> reset
```

## 兼容性与数据

- FCA 的 Carpet 规则保存在世界的 `carpet.conf` 中；Player Ladder 子设置由独立的
  Carpet 设置管理器管理，并保存在当前世界的 `playerladder.conf`。
- 旧版 Player Ladder 子规则不会从 `carpet.conf` 自动导入；升级后需按 `RULES.md`
  通过 `/playerladder` 重新设置自定义值。
- 大末影箱页面元数据保存在世界目录，不写入客户端配置。
- Carpet 只作为编译期依赖引用，不会打包进 FCA。
- Quick Shulker 仅用于可选的公开接口适配，不会打包进 FCA，也不会成为硬依赖。
- 客户端工具协议不包含终端 GUI；兼容客户端由独立项目实现。
- 大末影箱和 Easy Place 的规则握手由 Carpet `RuleObserver` 事件驱动；规则热切换会向
  已完成握手的在线客户端推送新的能力状态。

## 构建

使用项目 Wrapper，并确保 Gradle 使用的 JVM 是 Java 25。`JAVA_HOME` 可使用任意
JDK 25 安装路径。

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew :26.2:build :26.3:build
```

Windows PowerShell：

```powershell
$env:JAVA_HOME = '<JDK 25 installation>'
.\gradlew.bat :26.2:build :26.3:build
```

构建前按 [`libs/README.md`](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/libs/README.md)
准备对应版本的 Carpet 和 Quick Shulker 编译依赖。
共享源码、资源或 Mixin 修改后必须同时构建 26.2 和 26.3。
构建会执行 `logicTest` 和 `easyPlaceProtocolTest`；后者以
`EasyPlaceExtraProtocolTest` 为稳定入口，聚合并执行按行为族拆分的协议与状态测试。

PR 始终运行变更分类和 `CI Gate`；只有源码、资源、版本属性、Gradle、Wrapper 或
workflow 变化时才执行双版本完整构建。

## 许可证与第三方来源

FCA 使用 [MIT License](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/LICENSE)。

Player Ladder 改编自 ForwarD-NerN 的 PlayerLadder（MIT）。Easy Place V2-Extra
服务端实现改编自 chenjunfu2 的 EarlyCompat（CC0-1.0）。其他来源见
[THIRD_PARTY_NOTICES.md](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/THIRD_PARTY_NOTICES.md)。
