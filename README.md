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

将 FCA 和上述依赖放入服务端的 `mods` 目录。客户端只有需要使用客户端规则或
可选兼容功能时才安装 FCA。

FCA 的规则由 Carpet 管理，并随世界配置保存：

```text
/carpet list fca
```

## 功能概览

### 大末影箱

`bigEnderChest` 开启后，末影箱使用原版 `GENERIC_9x6` 菜单展示全部存储页的聚合
视图。原版客户端即可查看、取放、排序、翻页和丢弃物品。

- 页 0 直接使用玩家自己的原版末影箱。
- 额外页使用标准 27 格假人 `EnderItems` 容器，家具档案保存在 `playerdata`。
- 聚合槽是服务端管理的虚拟槽，最终状态以服务端事务结果为准。
- `bigEnderChestLiveFakePlayers` 可选地让实际发生存取的页面假人真实上线；索引和
  只读浏览仍使用离线文件。
- Quick Shulker 打开三行末影箱时会由服务端转换为大末影箱菜单。

自动页面假人按页独立计时，菜单关闭不会触发统一下线；空闲回收、规则关闭和
服务器停止时会先同步数据。真实假人首次批量上线可能产生明显的 server tick 尖峰，
该路径默认关闭。

### Easy Place V2-Extra

`easyPlaceProtocolExtra` 提供 Servux 兼容的服务端轻松放置语义，通过
`earlycompat:network` 接收兼容客户端的精确方块状态编码。服务端仍执行玩家触及、
方块放置和最终 `BlockItem.canPlace` 校验；FCA 不包含客户端投影读取或放置交互。

### Player Ladder

`playerLadder` 提供骑乘、举起、玩家堆叠、实体过滤和深度限制。支持 Carpet 假人，
并可选提供客户端准星修正。

### 客户端兼容与公开协议

FCA 不包含自定义大末影箱终端 GUI。TriWasa 等兼容客户端可以通过
`BigEnderChestProtocol.Payload` 和 `EasyPlaceExtraPayload` 使用 FCA 定义的公开
Payload、消息 DTO 与 Codec。FCA 同进程加载时，客户端直接复用这些类型，避免在同一
通道注册不同 Payload 类。

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

- FCA 规则保存在世界的 `carpet.conf` 中，不使用独立配置文件。
- 大末影箱页面元数据保存在世界目录，不写入客户端配置。
- Carpet 只作为编译期依赖引用，不会打包进 FCA。
- 客户端工具协议不包含终端 GUI；兼容客户端由独立项目实现。

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

构建前按 [`libs/README.md`](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/libs/README.md) 准备对应版本的 Carpet 编译依赖。
共享源码、资源或 Mixin 修改后必须同时构建 26.2 和 26.3。

PR 始终运行变更分类和 `CI Gate`；只有源码、资源、版本属性、Gradle、Wrapper 或
workflow 变化时才执行双版本完整构建。

## 许可证与第三方来源

FCA 使用 [MIT License](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/LICENSE)。

Player Ladder 改编自 ForwarD-NerN 的 PlayerLadder（MIT）。Easy Place V2-Extra
服务端实现改编自 chenjunfu2 的 EarlyCompat（CC0-1.0）。其他来源见
[THIRD_PARTY_NOTICES.md](https://github.com/DaHuaShuoDuo/Frostors-Carpet-Addition/blob/main/THIRD_PARTY_NOTICES.md)。
