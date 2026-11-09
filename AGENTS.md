# Frostor's Carpet Addition - 代理规则

## 项目定位

- 这是一个面向 Java 版 Minecraft 的 Fabric/Carpet 附属模组，当前同时支持 Minecraft
  `26.2` 和 `26.3`。当前已实现大末影箱、Easy Place V2-Extra、Player Ladder 和
  在线资料回退等功能；这份列表只记录现状，不是未来功能路线图、固定产品目标或新增
  功能的拒绝依据。
- 新功能可以继续加入，但必须遵守 Fabric/Carpet 生命周期、服务端权威、版本兼容和
  可测试性原则。不要为了维持早期“主功能”定位而强行把新功能塞进既有包、规则命名轴
  或协议中。
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

使用项目自带的 Gradle Wrapper，并确保实际执行 Gradle 的 JVM 是 Java 25。POSIX
shell 示例：

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew :26.2:build :26.3:build
```

Windows PowerShell 示例：

```powershell
$env:JAVA_HOME = '<JDK 25 installation>'
.\gradlew.bat :26.2:build :26.3:build
```

- 不要求固定 JDK 厂商或安装路径；`JAVA_HOME` 指向本机可用的 JDK 25 即可。不要用
  Java 17/21 代替 Java 25。
- 若默认 Gradle 缓存位置不可写，仅为当前环境把 `GRADLE_USER_HOME` 指向可写缓存目录；
  不要修改项目的 Wrapper 配置来规避环境问题。
- 只构建一个版本只能证明该版本可编译。共享源码、资源、Mixin 或依赖变更后，必须
  构建 `26.2` 和 `26.3` 两个版本。
- 构建产物位于 `versions/<版本>/build/libs/`。不要手工编辑 `.gradle/`、`build/` 或
  `versions/*/build/`；这些目录均为生成内容。
- `:26.2:build` / `:26.3:build` 会运行 `logicTest` 和 `easyPlaceProtocolTest` 中的
  无外部依赖测试。`src/gametest` 属于本机开发验证，不纳入版本控制，也不作为构建或
  发布产物的一部分。
- 涉及运行期逻辑时，必须在开发客户端或测试服务器中完成必要手测，并在交付说明中
  明确区分自动逻辑测试与手动运行期验证。不得假定未提交的本机 GameTest 在其他环境
  存在；只有当前工作区实际包含测试类并能运行时，才可声明 GameTest 通过，否则必须
  将相应运行期验证标记为未执行。
- 新增编译警告应视为回归；现有警告也不要复制到新代码。需要排查 Gradle 弃用时可使用
  `--warning-mode all`。

## Carpet 集成约束

- 保持 `FrostorsCarpetAddition.onInitialize()` 中的
  `CarpetServer.manageExtension(this)`。Carpet 26.x 不会通过扩展入口点自动发现本模组。
- 通用功能与总开关规则由 `FrostorSettings` 中的 `@Rule` 静态字段声明，并在
  `onGameStarted()` 中通过 `CarpetServer.settingsManager.parseSettingsClass(...)`
  注册。这些规则使用 `/carpet`，并写入世界的 `carpet.conf`。
- Player Ladder 子设置是有意的独立例外：由 `PlayerLadderSettings` 中的 `@Rule`
  字段声明，使用独立的 `SettingsManager` 注册为 `/playerladder`，并写入当前世界的
  `playerladder.conf`。`playerLadder` 总开关仍属于 `FrostorSettings` 和 `/carpet`。
  不要把总开关迁入子设置管理器，也不要为其他功能随意增加第三套配置存储。
- 独立管理器必须在执行命令前完成 `parseSettingsClass(...)`，并在
  `SERVER_STARTED` / `SERVER_STOPPING` 生命周期中成对调用 `attachServer(...)` /
  `detachServer()`。规则观察器、命令可用性和热切换状态必须与该生命周期一致。
- 规则字段名就是对应命令中的规则名，大小写敏感。`FrostorSettings` 规则使用
  `/carpet` 和 `carpet.conf`；`PlayerLadderSettings` 规则使用 `/playerladder` 和
  `playerladder.conf`。不要无意重命名已有规则；确需迁移时，必须处理或明确说明旧值
  兼容策略并同步文档。
- 旧版本的 `playerLadderClickMode` 等子规则不会自动从 `carpet.conf` 导入
  `playerladder.conf`。若版本没有实现迁移，升级文档必须明确要求管理员通过
  `/playerladder` 重新配置，不能静默暗示旧值仍会生效。
- 每条规则都必须在 `en_us.json` 和 `zh_cn.json` 中同时提供名称与描述。主规则使用
  `carpet.rule.<字段名>.name` 与 `carpet.rule.<字段名>.desc`。缺少 `.desc` 会导致
  Carpet 拒绝注册规则。Player Ladder 子设置使用
  `playerladder.rule.<字段名>.name` 与 `playerladder.rule.<字段名>.desc`，分类使用
  `playerladder.category.playerladder`。
- `RULES.md` 是 FCA 的规范规则文本，必须同时覆盖 `FrostorSettings` 与
  `PlayerLadderSettings` 的全部 `@Rule` 字段、默认值、作用端、合法取值和用途。
  新增、删除、重命名规则或修改默认值、取值、作用端时，必须同步更新 `RULES.md`、
  README 和对应中英文翻译。
- 仅客户端规则使用 `RuleCategory.CLIENT`；服务端行为不得依赖客户端配置。

新增规则的最小流程：

1. 确定归属：通用功能或功能总开关放入 `FrostorSettings`；仅属于 Player Ladder
   命令空间且由 `/carpet playerLadder` 总开关保护的子设置放入
   `PlayerLadderSettings`。
2. 在对应设置类中添加带 `@Rule` 的字段及合理默认值。
3. 在两种语言的资源文件中使用对应前缀补齐名称与描述。
4. 在业务代码中读取该字段，并补充必要的值校验和运行期切换处理。
5. 同步更新 `RULES.md`、README、迁移说明和中英文翻译，并重新构建两个版本。

`FrostorSettings` 规则字段使用 lowerCamelCase，并以所属功能（如 `playerLadder`、
`easyPlace`）作为前缀，避免全局 `/carpet` 命名冲突。`PlayerLadderSettings` 的点
命令已经提供独立命名空间，因此字段可使用简短的 lowerCamelCase 名称；总开关仍保持
`FrostorSettings.playerLadder`。布尔开关优先使用 `Allow...` 或明确的语义名称，数量
上限使用 `...Limit` / `...DepthLimit`，枚举模式使用 `...Mode`，忽略列表使用
`...IgnoredEntities`。避免同时使用 `Extension`、`InteractWith...`、
`RideFakePlayer` 等含义不统一的命名轴。

Carpet 会把枚举候选显示为小写，并在解析输入时先转为大写再调用 `Enum.valueOf`。因此 Java
枚举常量应保持 `UPPER_SNAKE_CASE`；README、翻译和实际 `/carpet` 或
`/playerladder` 取值使用全小写，不要把 Java 枚举常量改成小写，否则 Carpet 无法解析。

## 功能实现边界

- 以下规则约束当前高风险功能的实现，不构成未来功能范围。新增独立功能应优先建立独立
  包、规则前缀和协议边界，不要在既有功能类中堆叠无关逻辑。
- Fabric 事件注册、交互语义和 Mixin 接入应分离；Mixin 只做必要的 Minecraft 接入和
  状态桥接，避免把大段业务逻辑塞进 Mixin。
- 服务端世界修改必须由服务器线程和权威服务端状态执行。客户端包或客户端 Mixin 不得
  伪造可改变世界、库存或玩家档案的结果。
- 新增跨组件状态必须定义明确的清理、断线、规则热切换和服务器停止行为，并补充对应
  自动测试或说明手动验证缺口。

### Player Ladder

- `PlayerLadderSettings` 管理 `/playerladder` 子设置和 `playerladder.conf`；
  `PlayerLadder` 负责 Fabric 事件、命令注册、设置管理器生命周期和规则观察器；
  交互语义集中在 `SharedHandler`。
- `UseEntityCallback` 和 `SharedHandler` 服务端逻辑必须检查
  `level.isClientSide()`，不要从客户端伪造可改变世界状态的结果。
- 保持“主手、空手、服务端执行、目标实体可交互”等既有交互前提，除非需求明确改变。
- 排除实体列表是按逗号分隔的实体 id 或 `#` 标签。修改缓存逻辑时，应确保
  `/playerladder ignoredEntities` 修改后无需重启即可生效，并正确清理类型缓存与标签
  缓存。
- 骑乘链遍历、层数上限、第三人堆叠开关、假人开关、断线和游戏模式变化处理之间存在
  关联。修改其中一项时要回归相关分支，避免出现循环骑乘、残留乘客或客户端不同步。
- 独立的 `/playerladder` 子设置只在 `FrostorSettings.playerLadder=true` 时可用；
  关闭总开关后，交互、命令子树和 carry-on 临时状态必须停止生效或得到清理。
- 旧客户端准星修正已经移除。除非任务明确恢复该功能，不要把服务端状态同步重新依赖
  到客户端准星 Mixin。

### Big Ender Chest

- `BigEnderChestSessions` 管理玩家菜单会话，`BigEnderChestService` 是主线程存储门面；
  后台 I/O 只能负责 playerdata 或元数据读写，不得从异步线程直接修改世界、菜单或索引。
- 页 0 必须直接映射玩家原版末影箱；额外页使用页面元数据和标准 playerdata。页面元数据、
  异步保存、原子替换和服务器停止时的 flush 边界不得绕过。
- 索引、搜索、排序、翻页、扩容和只读浏览不得触发真实假人上线。`bigEnderChestLiveFakePlayers`
  只允许在实际存入或取出时请求对应页面上线。
- 自动页面假人必须使用 FCA 保存的本地 UUID/名称，不查询 Mojang profile；上线后强制
  旁观和飞行，并避免生成在会导致坠出世界的位置。
- 一个页面假人的租约必须按页记录使用时间。关闭菜单不能统一下线；仅在空闲超时、规则
  关闭或服务器停止时回收 FCA 管理的假人。手动 Carpet 假人不得被接管或误杀。
- 在线容器切换必须等待明确绑定，避免登录中间态覆盖离线副本。在线写入、空闲下线、
  重新打开、规则关闭和服务器停止路径必须保持物品数量与组件一致，不得丢失、增加或
  重复。修改生命周期时必须覆盖 26.2/26.3 的大末影箱 GameTest 和必要的快速存取测试。
- `BigEnderChestProtocol.Payload` 是该网络通道的唯一公开 Payload 定义；不要在服务端
  同时注册其他回退同类 Payload。

## Easy Place V2-Extra 边界

- 该功能只由一个总开关 `easyPlaceProtocolExtra` 控制，不要添加客户端编码、配置界面或
  子规则。
- 服务端方块状态语义以 Servux 的精准放置行为为兼容基线，并在 FCA 内直接实现；不要
  根据服务端是否安装 Servux 动态切换实现。这样同一规则不会因模组组合、加载顺序或
  Servux 版本产生不同结果。合成器锁槽以及现有红石点状状态和拉杆通电映射是 FCA 的
  既定例外。
- 服务端握手使用与 EarlyCompat 兼容的 `earlycompat:network` 通道，并在玩家断线时清理
  状态；如果 EarlyCompat 自身已加载，不要重复注册该通道。
- FCA 是共享协议 API 的提供方。`EasyPlaceExtraPayload` 和
  `BigEnderChestProtocol.Payload` 由 FCA 定义频道、消息 DTO 和 Codec；TriWasa
  通过 `compileOnly` 依赖这些公开类型，并在 FCA 同进程加载时直接复用。
- TriWasa 的独立 Payload 只用于客户端未安装 FCA、连接专用服务器时的线兼容回退。
  FCA 不应注册 TriWasa 的回退 Payload，也不得通过反射在两种 Payload 类之间复制字段；
  任何新增能力都先扩展 FCA 的公开协议 API，再更新 TriWasa 适配。
- 协议值只从服务端方块放置上下文中的命中位置解码，最终状态仍须通过 `BlockItem.canPlace`
  校验，不能信任客户端直接写入任意方块状态。
- 方块状态映射集中在 `easyplace` 包。`EasyPlaceStateSync` 是稳定 facade，实际 owner
  由初始化后冻结的 `EasyPlaceStateRestorerRegistry` 决定；按 behavior family 注册
  `easyplace/adapter` 下的 restorer，纯通用状态操作放入 `EasyPlaceStateSupport`。
  不要把已移除的 `LegacyEasyPlaceStateSync` 单体分支重新引回，也不要为每种原版方块
  复制独立 Mixin。新增适配时应扩展对应 family suite，同时保留
  `EasyPlaceExtraProtocolTest` 作为构建入口和集成矩阵。
- 多面方块线值顺序必须保持显式 `DOWN, UP, NORTH, SOUTH, WEST, EAST`；藤蔓顺序从
  同一数组过滤 `DOWN` 派生，不得另手写一份。该顺序属于当前 V2-Extra v1 构建契约：
  混用使用历史面顺序的 FCA、TriWasa 或 EarlyCompat 构建会错误解码多面/藤蔓方向；
  调整顺序或增加兼容协商时必须同步两仓库、模板测试、README 和兼容性说明。
- `MultifaceBlock` 的面状态必须从目标位置当前世界状态合并，不能继续使用原始放置状态；
  否则目标状态已含同一面时原版会返回 `null`，在附着方块周围有密集完整方块时表现为
  挂墙/多面方块漏放。必须保留密集图案 GameTest。
- 箱子必须按原版配对关系恢复：孤立半箱保持 `SINGLE`，放置另一侧后立即归一化为互配的
  `LEFT/RIGHT`。不要照搬 Servux 将 `CHEST_TYPE` 当普通终态属性强写；必须保留非连续
  放置后 `ChestBlock.getContainer()` 为 54 格的测试。
- 箱子协议使用 `bit6` 表示显式类型存在、`bit4-5` 表示 `SINGLE/LEFT/RIGHT`。旧客户端
  没有显式位时只能走保守的可用邻居推断。箱子同步使用
  `Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE` 做受控状态写入，不要直接复用铁轨
  的全局或定时位置抑制标签。
- `WallBlock` 的四个 `NONE/LOW/TALL` 横侧面按
  `NORTH + EAST*3 + SOUTH*9 + WEST*27` 编码进 7 位 raw，合法范围为 `0..80`；
  辅助 Z 通道的 bit0 表示 `UP`。`WATERLOGGED` 必须保持服务端放置状态，不能编码或强写。
  墙结构重算由独立 `WALL` family 在 `WallBlock.updateShape` 的 `RETURN` 阶段抑制，
  保留水 tick 等原版副作用；不得在 `HEAD` 直接取消。
- 放置期更新抑制 guard 按 `RAIL` 和 `WALL` family 分别记录并独立清理。嵌套放置和
  上一层结束后不得让一个方块族隐式压制另一个方块族。
- 命中位置中的 V2-Extra 编码会超出原版对方块中心的距离校验。仅对规则已开启且完成
  V2-Extra 握手的玩家放宽该校验，同时保留玩家触及范围和最终 `canPlace` 校验。该
  放宽必须独立生效，不能依赖 `antiCheatDisabled`。
- HELLO 应答和规则开启时的能力广播必须携带 `protocol_version`、显式能力布尔值以及
  `capabilities` 列表。客户端可把缺少新字段的 ACK 视为旧版兼容，但服务端不得退化为
  只发送 `mod_version`。
- 该服务端实现改编自 EarlyCompat，来源为 CC0-1.0；修改协议或状态表时同步维护
  `THIRD_PARTY_NOTICES.md`。不要移植 Litematica、MaLiLib、Fanetlib 或客户端服务。

## Mixin 规则

- 新增 Mixin 必须加入对应的 `frostors-carpet-addition*.mixins.json` 的 `mixins`、
  `client` 或 `server` 列表；注意 JSON 中的类名相对于声明的 `package`。
- 注入方法描述符、目标方法和返回类型必须与 Minecraft 方法签名一致；两个支持版本都要
  构建验证。优先保持插入点最小化，并在必要时调用 `Operation` 保留原行为和兼容性。
- 保持 `defaultRequire: 1`。不要为了绕过注入失败而静默降低为可选注入；应修正目标或
  明确记录版本差异。
- Minecraft 26.x 为未混淆版本，Loom 已设置 `useLegacyMixinAp = false`。不要重新启用
  legacy remap/refmap 流程，除非整个目标版本体系已迁移。
- 新增 Mixin 方法使用所属功能前缀，例如 `playerladder$<用途>`、
  `bigEnderChest$<用途>`、`easyPlace$<用途>` 或 `onlineProfile$<用途>`，避免和目标
  类成员冲突。不要把新的无关功能继续命名为 `playerladder$`；碰巧修改旧 Mixin 时再
  按所属功能修正命名，不做无关的大规模重命名。

## 版本与依赖

- 当前版本矩阵：

  | Minecraft | Fabric Loader | Fabric API | Carpet |
  | --- | --- | --- | --- |
  | 26.2 | >= 0.19.3 | >= 0.152.1 | >= 26.2 < 26.3 |
  | 26.3 | >= 0.19.5 | >= 0.160.5 | >= 26.3 < 26.4 |

- 根 `gradle.properties` 管共享属性（模组版本、group、产物前缀、Loom 版本）；
  Minecraft、Loader、Fabric API 和兼容范围由各版本属性文件定义。
- `libs/fabric-carpet-<Minecraft 版本>.jar` 只作为 `compileOnly` 编译依赖。Carpet
  必须是运行期 `depends`，但绝不能被打进模组 JAR，也不要复制或反编译其源码到项目。
- 增加或移除支持版本时，要同时处理 `settings.gradle`、版本属性、对应 Carpet 编译依赖、
  README 版本表和双版本构建验证，不能只改一个文件。
- 运行时依赖版本范围由各版本的 `fabric_api_dependency` 和 `carpet_dependency` 管理，
  并会展开进 `fabric.mod.json`。修改范围时必须双版本构建并检查产物元数据。

## Git、提交与 GitHub

- 开始任务时先运行 `git status --short`，区分并保留用户已有修改；不得覆盖、清理或
  格式化与当前任务无关的文件。
- 功能改动默认从最新 `main` 创建 `codex/<主题>` 本地分支。不要在功能分支混入无关
  提交，也不要直接修改或强推 `main`；确需改写已发布历史时必须取得用户明确同意。
- 除非用户明确要求，否则不要 push、创建 PR、启用 Auto-merge 或合并 PR。提交前确认
  暂存区只包含当前任务文件。
- 提交前按变更类型验证：
  - 源码、资源、Mixin、版本属性、Gradle、Wrapper 或 workflow：构建 26.2 和 26.3，
    并运行 `logicTest`、`easyPlaceProtocolTest`。
  - README、docs、许可证等纯文档改动：确认所有变更路径都不属于 CI 的构建必需路径，
    不要求执行完整 Minecraft 构建；PR 已创建时仍须让 `CI Gate` 通过。
  - 涉及运行期、Carpet 规则、假人或大末影箱数据路径：补充对应 GameTest 或明确记录
    未执行的手测项。`src/gametest` 保持本机开发验证，不提交、不进入 CI。
- 提交信息使用清晰的 `type: summary` 前缀，例如 `fix:`、`feat:`、`refactor:`、
  `ci:`、`docs:`。不得提交 `.gradle/`、`build/`、`versions/*/build/`、运行世界、
  日志、本地依赖 JAR 或其他生成内容。
- `README.md` 与 `README_en.md` 是互为镜像的中英文用户文档。修改功能、安装、
  依赖、规则、协议或兼容性说明时同步更新两种语言，并保留文件顶部互相跳转的绝对
  GitHub 链接；两份 README 对 `RULES.md` 等技术细节的指向必须一致。
- 用户要求创建 PR 时，PR 必须说明改动范围、自动测试、GameTest/手测结果、未验证风险
  和性能影响；涉及 TriWasa 协议兼容时注明使用的 `fca_ref` 或 FCA 提交。
- `main` 只能通过 PR 合并，必需检查为固定的 `CI Gate`，分支必须与最新 `main`
  同步。不要绕过分支保护、直接推送或临时移除必需检查。
- 只有仓库所有者提交的同仓库、非草稿 PR 才可使用 `auto-merge` 标签；Fork PR、
  未添加标签的 PR 以及修改 workflow、Gradle、Wrapper 或依赖配置的 PR 必须人工
  合并。用户未明确授权合并时，不得添加该标签。
- Auto-merge 使用 Squash。移除 `auto-merge` 标签后应确认 Auto-merge 已关闭。
- 合并 PR 后同步本地 `main`、清理已合并分支，并确认工作区干净。不得要求用户在聊天
  中发送 GitHub Token、密码或其他凭据。
- FCA 与 TriWasa 的协议改动必须按依赖顺序处理。先确认 FCA 的公开 Payload/API
  状态，再推送或更新 TriWasa 的适配；跨仓库验证失败时不得用旧 JAR 或反射桥接掩盖
  不兼容。

## 代码与交付习惯

- 沿用现有 4 空格缩进、UTF-8、功能分包和紧凑的 Java 风格；避免无关格式化或大范围重排。
- 优先使用 Minecraft/Fabric/Carpet 已有 API，新增依赖前说明必要性并确认不会改变
  JAR 的运行时依赖边界。
- 保留 MIT 许可证、项目声明以及模组元数据中的作者和依赖信息。
- 任务完成前至少确认：两个版本的 `logicTest` 与 `easyPlaceProtocolTest` 通过；
  Carpet 规则翻译完整；`RULES.md` 与 `FrostorSettings`、`PlayerLadderSettings`
  的全部 `@Rule` 字段一致；README 与行为及配置存储说明一致；Carpet 未被误打包；
  涉及运行期逻辑时记录手动验证结果。
