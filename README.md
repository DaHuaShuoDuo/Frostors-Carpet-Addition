# Frostor's Carpet Addition

一个 [Carpet](https://github.com/gnembon/fabric-carpet) 附属模组，命名空间为 `frostor.top`。

> **声明：本项目的代码由 AI 辅助完成。** 需求、功能设计与发布由 Frostor 负责，实现代码在 AI 辅助下生成并经人工审阅。

## 支持的版本

| Minecraft | Fabric Loader | 产物名 |
| --- | --- | --- |
| 26.2 | >= 0.19.3 | `frostors-carpet-addition-fabric-26.2` |
| 26.3 | >= 0.19.5 | `frostors-carpet-addition-fabric-26.3` |

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

## 规则

安装后可用 `/carpet <规则名>` 查看或修改，规则随世界保存在 `carpet.conf`。

| 规则 | 默认值 | 说明 |
| --- | --- | --- |
| `playerLadder` | `false` | 人梯功能总开关 |
| `playerLadderClickMode` | `RIDE` | 右键行为：`ride` / `pick_up` / `do_nothing` |
| `playerLadderPickUpLimit` | `16` | 举起时向上寻找的最大层数 |
| `playerLadderStepUpLimit` | `16` | 骑乘时向上寻找的最大层数 |
| `playerLadderInteractWithAnyLiving` | `false` | 是否允许非玩家生物作为对象 |
| `playerLadderExcludedEntities` | 见下 | 逗号分隔的排除实体 id / 标签 |
| `playerLadderDismountOnGameModeChange` | `false` | 切换游戏模式时是否让乘客下马 |
| `playerLadderRideExtension` | `true` | 是否允许 3 人及以上的玩家堆叠（关闭时仅允许两位玩家骑乘） |
| `playerLadderRideFakePlayer` | `false` | 是否允许骑乘/举起 Carpet 假人 |
| `playerLadderClientInteractions` | `true` | 客户端准星修正（仅客户端生效） |

`playerLadderExcludedEntities` 默认值：

```
minecraft:wither,minecraft:ender_dragon,minecraft:warden,minecraft:minecart,#minecraft:boat,#minecraft:dismounts_underwater
```

> 该模组原先的 `config/playerladder.json` 已完全移除，所有设置改由 Carpet 规则承载，
> 随世界保存在 `carpet.conf` 中，可用 `/carpet <规则名> <值>` 修改。


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
