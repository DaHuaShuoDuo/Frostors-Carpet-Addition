# Frostor's Carpet Addition - 代理规则

## 项目定位

- 这是一个面向 Java 版 Minecraft 的 Fabric/Carpet 附属模组，当前同时支持 Minecraft
  `26.2` 和 `26.3`，主功能为 `playerLadder`。
- `src/main` 是两个版本共用的源码与资源；`versions/<版本>/gradle.properties`
  只负责版本差异。不要在版本目录中复制或分叉 Java 源码。
- 保持 mod id `frostors-carpet-addition`、Java 包名 `top.frostor.carpet`
  和公开入口点稳定，除非任务明确要求迁移。
- 项目要求 Java 25。Gradle Wrapper 固定为项目中的版本，不要为单次构建升级 Gradle
  Wrapper、Fabric Loom 或 Minecraft 依赖。

开始修改前先阅读 `README.md` 和相关实现。若目录中存在 `.git`，先用
`git status --short` 识别已有修改；当前目录可能是未附带 Git 元数据的交付包，
不要假定提交历史一定可用，也不要改写或删除无关文件。

## 构建与验证

在 PowerShell 环境中使用项目 Wrapper 构建两个版本：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-25'
.\gradlew.bat :26.2:build :26.3:build
```

- JDK 路径应按本机实际 Java 25 安装位置调整，但不要用 Java 17/21 代替 Java 25。
- 若 Wrapper 尝试在不可写的位置创建 `C:\.gradle`，仅为当前命令把
  `GRADLE_USER_HOME` 指向用户 Gradle 缓存，例如
  `$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'`；不要修改项目的
  Wrapper 配置来规避环境问题。
- 只构建一个版本只能证明该版本可编译。共享源码、资源、Mixin 或依赖变更后，必须
  构建 `26.2` 和 `26.3` 两个版本。
- 构建产物位于 `versions/<版本>/build/libs/`。不要手工编辑 `.gradle/`、`build/` 或
  `versions/*/build/`；这些目录均为生成内容。
- 项目当前没有自动化测试（Gradle 会显示 `test NO-SOURCE`）。构建成功不等于运行期
  行为正确；涉及游戏逻辑、事件或 Mixin 时，需尽可能在带 Carpet 和 Fabric API 的
  客户端/服务器中验证，并明确说明哪些手测已做、哪些未做。
- 新增编译警告应视为回归；现有警告也不要复制到新代码。需要排查 Gradle 弃用时可使用
  `--warning-mode all`。

## Carpet 集成约束

- 保持 `FrostorsCarpetAddition.onInitialize()` 中的
  `CarpetServer.manageExtension(this)`。Carpet 26.x 不会通过扩展入口点自动发现本模组。
- 规则统一由 `FrostorSettings` 中的 `@Rule` 静态字段声明，并在
  `onGameStarted()` 中通过 `CarpetServer.settingsManager.parseSettingsClass(...)`
  注册。不要在项目中重新引入 `config/playerladder.json` 等独立配置。
- 规则字段名就是 `/carpet` 规则名，大小写敏感，并会被写入世界的 `carpet.conf`。
  不要无意重命名已有规则；确需重命名时，必须处理旧配置兼容性并同步文档。
- 每条规则都必须在 `en_us.json` 和 `zh_cn.json` 中同时提供
  `carpet.rule.<字段名>.name` 与 `carpet.rule.<字段名>.desc`。缺少 `.desc` 会导致
  Carpet 拒绝注册规则。
- 新增或修改用户可见规则后，同步更新 `README.md` 的规则表、默认值和说明。
- 仅客户端规则使用 `RuleCategory.CLIENT`；服务端行为不得依赖客户端配置。

新增规则的最小流程：

1. 在 `FrostorSettings` 中添加带 `@Rule` 的字段及合理默认值。
2. 在两种语言的资源文件中补齐名称与描述。
3. 在业务代码中读取该字段，并补充必要的值校验和运行期切换处理。
4. 更新 README，并重新构建两个版本。

## 功能实现边界

- `PlayerLadder` 负责 Fabric 事件注册；交互语义集中在 `SharedHandler`；Mixin 只做
  必要的 Minecraft 接入和状态桥接。避免把大段业务逻辑塞进 Mixin。
- `UseEntityCallback` 和 `SharedHandler` 服务端逻辑必须检查
  `level.isClientSide()`，不要从客户端伪造可改变世界状态的结果。
- 保持“主手、空手、服务端执行、目标实体可交互”等既有交互前提，除非需求明确改变。
- 排除实体列表是按逗号分隔的实体 id 或 `#` 标签。修改缓存逻辑时，应确保
  `/carpet` 修改规则后无需重启即可生效，并正确清理类型缓存与标签缓存。
- 骑乘链遍历、层数上限、第三人堆叠开关、假人开关、断线和游戏模式变化处理之间存在
  关联。修改其中一项时要回归相关分支，避免出现循环骑乘、残留乘客或客户端不同步。
- 客户端准星修正只应存在于客户端 Mixin 中；服务端和人梯状态同步逻辑不得依赖它。

## Mixin 规则

- 新增 Mixin 必须加入 `frostors-carpet-addition.mixins.json` 的 `mixins` 或 `client`
  列表；注意 JSON 中的类名相对于声明的 `package`。
- 注入方法描述符、目标方法和返回类型必须与 Minecraft 方法签名一致；两个支持版本都要
  构建验证。优先保持插入点最小化，并在必要时调用 `Operation` 保留原行为和兼容性。
- 保持 `defaultRequire: 1`。不要为了绕过注入失败而静默降低为可选注入；应修正目标或
  明确记录版本差异。
- Minecraft 26.x 为未混淆版本，Loom 已设置 `useLegacyMixinAp = false`。不要重新启用
  legacy remap/refmap 流程，除非整个目标版本体系已迁移。
- Mixin 方法统一使用 `playerladder$<用途>` 命名，避免和目标类成员冲突。

## 版本与依赖

- 当前版本矩阵：

  | Minecraft | Fabric Loader | Fabric API |
  | --- | --- | --- |
  | 26.2 | >= 0.19.3 | 0.152.1+26.2 |
  | 26.3 | >= 0.19.5 | 0.160.5+26.3 |

- 根 `gradle.properties` 管共享属性（模组版本、group、产物前缀、Loom 版本）；
  Minecraft、Loader、Fabric API 和兼容范围由各版本属性文件定义。
- `libs/fabric-carpet-<Minecraft 版本>.jar` 只作为 `compileOnly` 编译依赖。Carpet
  必须是运行期 `depends`，但绝不能被打进模组 JAR，也不要复制或反编译其源码到项目。
- 增加或移除支持版本时，要同时处理 `settings.gradle`、版本属性、对应 Carpet 编译依赖、
  README 版本表和双版本构建验证，不能只改一个文件。

## 代码与交付习惯

- 沿用现有 4 空格缩进、UTF-8、功能分包和紧凑的 Java 风格；避免无关格式化或大范围重排。
- 优先使用 Minecraft/Fabric/Carpet 已有 API，新增依赖前说明必要性并确认不会改变
  JAR 的运行时依赖边界。
- 保留 MIT 许可证、项目声明以及模组元数据中的作者和依赖信息。
- 任务完成前至少确认：两个版本构建通过；Carpet 规则翻译完整；README 与行为一致；
  Carpet 未被误打包；涉及运行期逻辑时记录手测结果。
