# 大末影箱实施计划

## 1. 文档目的

本文档是后续 agent 实施“大末影箱”功能的规格基线。范围只包括 FCA 的服务端存储、
原版客户端兼容界面、页面容量管理和客户端工具对接协议。客户端终端界面由独立项目实现，
FCA 不包含其 GUI、按键、搜索界面或客户端物品仓库。

实施前必须阅读根目录 `AGENTS.md`、`README.md`，并遵守现有的 Carpet 规则、双版本构建、
Mixin 和依赖边界。

## 2. 已确定的设计

- 功能默认关闭，通过 Carpet 规则开启。
- 原版客户端不需要安装任何客户端模组。
- 原版客户端使用 `6×9` 的 `GENERIC_9x6` 箱子界面。
- 顶部 `5×9 = 45` 格显示聚合后的物品条目，底部一行 `9` 格用于翻页和控制。
- 安装客户端工具的玩家使用独立的终端 GUI，但继续复用同一服务端会话、物品索引和操作 API。
- 假人末影箱仍是物品的真实存储载体，不把物品迁移到 FCA 自建的虚拟物品容器中。
- 每个底层假人存储页固定采用标准末影箱的 `27` 格。
- 原版玩家自己的末影箱作为第 0 个底层存储页，不复制其物品。
- 假人页是仅由标准 `playerdata/<uuid>.dat` 表示的“幽灵存储页”，不得进入
  `PlayerList`，不得构造持续存在的 `ServerPlayer`，不得进入世界或参与 tick。
- “查看页”和“存储页”必须分开命名，避免实现时混淆：
  - **存储页**：一个标准 27 格假人末影箱。
  - **查看页**：原版客户端当前显示的 45 个聚合物品条目。
  - **终端页**：客户端终端自行决定的滚动/分页单位，不属于 FCA 协议语义。

## 3. 非目标

- 不实现客户端终端 GUI。
- 不新增 AE2 运行时依赖，不复制 AE2 源码。只借鉴“唯一物品模板 + 会话序号 + 增量更新”的协议模式。
- 不提供不同玩家之间共享末影箱。
- 不默认提供管理员跨玩家访问。
- 不允许客户端提交任意 `ItemStack` 并直接写入存储。
- 不自动删除超出新页数上限的已有页面或物品。
- 不把 Carpet JAR、AE2 或其他模组打包进 FCA。

### 3.1 AE2 代码复用边界

AE2 可以参考，但 v1 不得直接依赖其二进制或复制其存储实现。原因如下：

- 作为参考的 AE2 源码构建目标是 NeoForge、Minecraft 26.1.2；FCA 当前目标是
  Fabric、Minecraft 26.2 和 26.3，不能共享同一运行时二进制。
- AE2 是 LGPL-3.0 项目。通过公开 API 运行时集成和把源码移植进 MIT 项目不是同一种
  合规问题；未经明确许可和新的第三方声明，不要复制其实现文件。
- `BasicCellInventory` 绑定 `IBasicCellItem`、AE 配置、升级、分区和
  `STORAGE_CELL_INV` 组件，不能用于假人末影箱。
- `NetworkStorage` 绑定 AE Grid 的挂载、优先级和 `MEStorage`。
- 最值得复用的只是 `物品/组件身份 + long 数量`、优先填补既有类型、会话序号和增量
  协议设计，这些都可小规模独立实现。

禁止新增 `appeng.*` import、Maven 依赖、可选模组依赖或在 `fabric.mod.json` 中增加
AE2 `depends`/`recommends`。若未来另立需求要集成 AE2，必须单独评估平台、版本、
许可证和运行时依赖，不得作为本计划的顺手改动。

## 4. Carpet 规则与翻译

在 `FrostorSettings` 中增加默认关闭的规则。建议字段名如下，实施时除非用户另行确认，不得
无意改名：

```java
@Rule(categories = RuleCategory.FEATURE)
public static boolean bigEnderChest = false;

@Rule(categories = RuleCategory.FEATURE)
public static int bigEnderChestPageLimit = 64;
```

语义：

- `bigEnderChest`：关闭时完全保留原版末影箱行为。
- `bigEnderChestPageLimit`：允许创建的额外假人存储页上限，不含第 0 个原版页。
- 运行期将页数限制约束在 `1..1024`。超出范围时拒绝创建新页，不删除既有页面。
- 页面上限降低后，已有页面仍应可访问；仅禁止继续扩容。
- 规则关闭后，原版末影箱照常使用，FCA 页面数据隐藏但不删除。

必须同步修改：

- `src/main/resources/assets/frostors-carpet-addition/lang/en_us.json`
- `src/main/resources/assets/frostors-carpet-addition/lang/zh_cn.json`
- `README.md` 的规则表和功能说明

每个字段都要有 `name` 和 `desc`，否则 Carpet 会拒绝注册。

## 5. 建议包结构

建议在 `top.frostor.carpet` 下新增：

```text
bigenderchest/
├── BigEnderChest.java                 # 事件注册与功能入口
├── BigEnderChestSessions.java         # 服务端会话注册表
├── BigEnderChestService.java          # 统一存取、查询和事务入口
├── capacity/
│   ├── BigEnderChestCapacity.java     # 页面预留、扩容和缓冲
│   └── PageReservation.java           # 批量操作容量令牌
├── page/
│   ├── StoragePage.java               # 27 格页面模型
│   ├── StoragePageState.java          # LOADING/READY/DIRTY/SAVING 等
│   ├── PageRegistry.java              # 世界级页面元数据
│   └── FakePageDataStore.java         # 标准假人 playerdata 读写
├── index/
│   ├── ItemTemplateKey.java           # 物品及组件身份，忽略数量
│   ├── StorageEntry.java              # 聚合数量与槽位位置
│   └── BigEnderChestIndex.java        # 可重建的物品索引
├── menu/
│   ├── BigEnderChestMenu.java         # GENERIC_9x6 服务端菜单
│   ├── BigEnderChestView.java         # 45 条目 + 9 控制槽视图
│   └── ViewControl.java               # 翻页、排序等控制项
├── api/
│   └── ...                            # 客户端工具可编译的稳定协议 DTO
└── protocol/
    ├── BigEnderChestProtocol.java     # 版本、通道和限制
    ├── Clientbound...                  # S2C payload
    └── Serverbound...                  # C2S payload
```

服务端实现类保持内部可见；只有客户端工具需要的协议常量和 payload DTO 放入 `api`。
不要把菜单、假人页或存储实现暴露成外部 API。

## 6. 打开流程

在 `FrostorsCarpetAddition.onInitialize()` 中注册 `BigEnderChest.register()`。

服务端通过 Fabric `UseBlockCallback` 识别原版末影箱方块。只有在以下条件全部满足时才
拦截原版打开流程：

- `FrostorSettings.bigEnderChest` 已开启。
- 逻辑端为服务端。
- 玩家满足原版使用末影箱的基本条件。
- 方块是 `Blocks.ENDER_CHEST` 或原版语义等价的目标。

不符合条件时返回 `PASS`，不得自行模拟或复制原版打开逻辑。规则关闭时必须恢复完全原版
行为。

打开后创建 `BigEnderChestSession`，保存：

- 所有者 UUID。
- 服务端菜单 `containerId`。
- 会话随机 `sessionId`。
- 所有者存储版本 `revision`。
- 当前原版查看页。
- 客户端是否已完成终端协议握手。

同一玩家同一时间只允许一个有效会话。玩家断线、菜单关闭或切换菜单时关闭会话并从注册表
移除。

## 7. 原版 6×9 界面

### 7.1 菜单结构

使用带自定义 `Container` 的：

```java
new ChestMenu(
        MenuType.GENERIC_9x6,
        syncId,
        playerInventory,
        viewContainer,
        6
);
```

或者提供等价的 `BigEnderChestMenu` 子类。容器总大小为 `54`：

```text
槽位 0..44   ：当前查看页的 45 个聚合物品条目
槽位 45..53  ：9 个控制项
```

玩家背包槽位仍由 `ChestMenu` 按标准位置添加。不要手工更改玩家背包槽位索引。

### 7.2 显示物品

原版客户端无法在单个 `ItemStack` 中合法显示任意大的聚合库存量。显示栈必须是合法物品栈：

- 物品类型和组件用于图标及工具提示。
- 数量字段不承载真实库存量，默认归一化为 `1`。
- 真实总库存量放到自定义名称或 lore 中，例如 `库存：12,345`。
- 客户端工具不得依赖这个显示数量，必须从协议中的 `long amount` 读取真实数量。

如果实现时发现客户端对 lore/名称缓存或比较带来额外网络开销，可以退化为只显示图标，
将总量仅放在悬浮提示中。不得发送超过该物品最大堆叠数的 `ItemStack.count`。

### 7.3 控制行

建议保留以下 9 个位置：

```text
[首页] [上一页] [页码] [下一页] [末页] [排序字段] [排序方向] [刷新] [关闭]
```

控制项是菜单视图生成的临时显示物品，不属于任何存储页，也不得进入物品索引。

底部 9 个槽位必须始终有内容：

- 有功能的槽位显示对应按钮物品。
- 当前条件不可用的按钮显示禁用态物品和原因提示。
- 尚未分配功能的槽位显示不可交互的灰色占位物品。
- 不允许保留 `ItemStack.EMPTY` 作为控制槽，避免第三方界面把空槽当作可用容器槽。
- 占位物和控制按钮不能进入玩家背包、不能交换、不能丢弃、不能参与任何排序。

至少需要禁用：

- 控制槽的 `setItem` 和 `removeItem`。
- Shift 点击把控制项移入玩家背包。
- 数字键交换、拖拽、双击收集和丢弃控制项。
- 玩家背包物品被 Shift 点击放入控制槽。

在 `BigEnderChestMenu.clicked`、`quickMoveStack` 和相关拖拽路径中统一拦截。控制槽只接受
明确的白名单点击动作，且仅在玩家手中没有物品时触发。所有菜单操作必须在服务端验证。

### 7.4 查看页更新

原版查看页显示的是 `BigEnderChestIndex` 的过滤和排序结果，不是固定存储页：

- 默认按物品注册名排序。
- 空库存条目不能出现在视图中。
- 翻页和排序只改变 45 个展示槽内容。
- 不因查看页变化而创建假人存储页。
- 物品或容量变化后增加索引版本，重新计算当前视图并同步 45 个显示槽。
- 排序后必须保持同一条目在当前视图中的稳定标识，避免鼠标下物品跳动造成误点。

原版客户端不接受文本搜索，v1 不要求原版界面提供搜索。客户端工具可以本地搜索。

### 7.5 R 键整理与异常批量点击防护

R 键属于客户端按键，服务端无法区分“玩家按了 R 键”和正常鼠标点击，因为第三方整理模组
通常通过连续发送普通容器点击包实现排序。因此必须同时约束客户端工具和服务端菜单。

客户端工具要求：

- 大末影箱终端打开时，`R` 键必须由终端屏幕消费，不传递给其他界面或物品整理模组。
- 不提供本地重新排列服务端存储页的动作；视觉排序只能改变客户端视图。
- 任何真正的存储页整理必须调用服务端显式操作，并遵守 revision 和事务规则。

服务端菜单要求：

- 查看槽和控制槽不提供“整理”菜单按钮码。
- 只接受 `PICKUP`、受限的 `QUICK_CRAFT` 和白名单 Shift 移动路径。
- 拒绝控制槽上的 `SWAP`、`THROW`、`PICKUP_ALL`、`CLONE`、拖拽分发及未知动作。
- 拒绝任何试图直接改动查看槽背后不存在服务器的 `Slot` 物品的请求。
- 第三方排序模组发出的异常 `SWAP` 或批量点击不得改变控制行，也不得产生物品复制。
- 服务端无法保证阻止未安装客户端工具的第三方整理模组读取按下的 R 键；此类模组不属于
  支持范围。默认原版客户端没有该 R 键整理功能。

实现时不要尝试通过“短时间内点击很多槽”判定 R 键，这会误伤正常快速操作。防护应基于
槽位角色和动作白名单，而不是基于输入速度。

### 7.6 原版菜单的存入与取出语义

查看页的 45 个槽位只是索引条目，不是实际存储槽。因此不能把原版箱子“必须先找到空展示
槽才能放入”的规则套用到存入操作上。当前查看页即使 45 格全部有物品，也必须能够向大
末影箱存入新物品。

#### 存入

以下操作都必须调用 `BigEnderChestService.insert`，由服务端在全部存储页中寻找或预留
真实容量：

- 玩家对物品栏槽位 Shift + 左键：尝试存入该槽位的整组物品。
- 玩家鼠标持有物品并对查看页任意非控制槽左键：尝试存入当前携带的整组物品。
- 玩家鼠标持有物品并在查看页上左键拖拽：拖拽结束时尝试存入剩余的整组携带物品。

具体规则：

- 当前查看页是否还有空显示槽不影响存入。
- 目标查看条目即使属于不同物品模板，也允许作为“存入动作”的目标；不要用它对应
  存储页中的实际槽位作为唯一目的地。
- 存入前执行同模板非满堆合并、空槽分配和必要容量预留。
- 存入成功后更新索引及 revision。若新物品类型不在当前查看页，可以保持当前页位置，
  由用户翻页查看；不得为了显示新条目而静默替换当前条目。
- 如果页数达到 `bigEnderChestPageLimit` 或真实存储空间不足，不得部分删除源物品。
  源堆剩余数量保留在玩家原位置，并返回容量不足。
- 拖拽操作只能在结束阶段提交一次真实事务，不能按鼠标经过的槽位重复插入。
- 携带物品左键点击查看条目的优先级是“存入”，不是“取出”。只有鼠标为空时才允许取出。

`quickMoveStack` 对玩家背包来源槽必须调用上述存入路径，不能直接寻找 `0..44` 中的空槽。
不能在拖拽的模拟/分发阶段修改任何真实容器。

#### 取出

当玩家鼠标为空并左键点击一个聚合查看条目时：

- 取出的数量上限是该物品的 `maxStackSize`。
- 普通可堆叠物品最多取出一组，而不是该条目的全部总库存。
- 不可堆叠物品最多取出一个。
- 先从已有非满堆提取以帮助减少碎片，再按确定顺序从其他槽位补足，但总数不得超过
  一个最大堆叠。
- 如果鼠标已有物品，不允许直接取出并合并到鼠标；必须先处理携带物品的存入语义。
- Shift + 左键点击查看条目进入玩家背包时同样最多移动一个最大堆叠。

同一聚合条目可能有数百组存量，用户需要重复点击才能逐组取出。v1 不提供“一次取出全部”
的原版菜单动作。

## 8. 聚合索引

### 8.1 物品身份

`ItemTemplateKey` 必须包含：

- 物品类型。
- 全部数据组件。

不包含：

- `count`。
- 当前所在存储页和槽位。

使用 Minecraft 26.x 的组件比较 API，不能只比较 `Item` 或旧式 NBT。不同潜影盒内容、
不同自定义名称或不同染色数据必须视为不同模板。

### 8.2 聚合条目

`StorageEntry` 至少包含：

```text
ItemTemplateKey template;
long totalAmount;
List<StorageLocation> locations;
```

`StorageLocation` 至少包含：

```text
int storagePage;  // 0 = 原版末影箱，1..N = 假人页
int slot;         // 0..26
```

索引是派生缓存，不是物品真相来源。必须保证可以从各存储页重新构建。

索引可以持久化以减少首次扫描，但必须带校验版本；发现页面、工具版本或数据结构不匹配时
丢弃并重建。页面物品仍只能存在于原版末影箱或假人标准 `EnderItems` 中。

### 8.3 更新来源

索引变化只能由以下路径触发：

- FCA 菜单成功完成物品移动。
- 玩家原版末影箱自身内容发生变化。
- 页面首次加载。
- 页面容量增加。
- 终端动作成功提交。

不要每 tick 扫描全部假人页面。菜单打开时加载基础索引；页面修改通过已知
`storagePage + slot` 精确更新索引。

### 8.4 空间碎片与整理

AE2 的基本存储单元不是按 Minecraft 物品槽保存，而是按唯一 `AEKey` 保存
`key -> long amount`。因此同一个存储单元内不存在“同一种物品占据多个不满堆”的问题。
但同一种物品仍可能因优先级、过滤或插入顺序分布在多个存储单元中，每个单元都要额外承担
一份类型开销。AE2 不会在每次插入后主动跨单元搬迁既有物品；网络插入会优先选择已经含有
该物品的存储单元，而完整整理通常需要借助 I/O Port 等外部手段重新搬运。

假人大末影箱不能直接照搬这一点，因为每个页面仍是真实的 27 个 `ItemStack` 槽位，
非整组物品会实际占槽。必须同时实现插入优化和可选整理。

#### 插入时优化

所有存入路由统一经过 `BigEnderChestService`，不得直接寻找“第一个空槽”。

对每个待存物品模板：

1. 在所有已加载存储页中查找同模板且未达到最大堆叠数的槽位。
2. 优先填满这些已有非满堆，避免插入顺序额外制造碎片。
3. 剩余数量再依次写入空槽。
4. 同一事务内的新物品也参与后续合并，不能逐组独立寻找空槽。
5. 页面选择顺序必须稳定，例如按 `storagePage`、`slot` 升序，避免结果随加载顺序变化。
6. 容量计算必须扣除已有非满堆可继续容纳的数量，而不是简单用
   `ceil(totalAmount / maxStackSize)` 从零计算。

建议使用 best-fit 分配：优先选择可容纳量足够、剩余空间更小的同模板槽位，再使用更大余量
或空槽。算法必须保持确定性，并在大量槽位时使用模板索引，而不是每次扫描全部 27N 个槽。

#### 既有碎片整理

若在扩容前仍存在不足一页的容量浪费，可以尝试整理已有页面：

- 将同模板的非满堆合并成尽可能少的满堆。
- 合并后把完整堆集中到较早的存储页和槽位。
- 将整理后空出的页面保留为 `READY` 缓冲页，不立即删除档案。
- 默认不为了整理而移动页面 0 中的原版末影箱物品，避免玩家原版末影箱的排列被静默改变。
- 页面 1..N 可以被整理，但必须通过统一事务执行并发送索引 Delta。

整理不能由每个物品插入同步触发。建议仅在以下时机执行：

- 即将分配新页面之前，先尝试整理并重新计算是否仍需要扩容。
- 管理员或玩家通过显式“整理”控制触发。
- 后续增加空闲维护任务时，可在碎片超过配置阈值后异步整理。

整理事务要求：

- 在服务器主线程规划和执行所有容器修改。
- 先模拟完整移动集合，验证不复制、不丢失，并计算整理后空页数。
- 对跨页面移动写事务日志，至少记录页面、槽位、原物品和最终物品。
- 保存失败或服务器中断后应能重新加载并完成或回滚，不能只写一半。
- 整理完成后一次性递增服务端 revision，再通知原版菜单和终端客户端重算视图。
- 终端持有旧 revision 的操作必须拒绝并请求重新同步。

允许自动“扩容前整理”，但不建议默认启用持续的后台整理。自动整理会持续移动玩家物品，
增加磁盘写入和崩溃恢复复杂度，收益通常低于插入时合并。

## 9. 页面和容量管理

### 9.1 存储页

- 存储页 0 直接映射发起玩家的 `getEnderChestInventory()`。
- 存储页 1..N 各自映射一个假人的 27 格 `EnderItems`。
- 一个假人存储页只能属于一个玩家所有者。
- 页面注册表记录所有者、逻辑页号、固定 profile UUID、生成名称和当前状态。
- 页面状态至少包括 `UNLOADED`、`LOADING`、`READY`、`DIRTY`、`SAVING`、`ERROR`。
- 只有 `READY` 页面可以被物品事务使用。

### 9.2 初始容量与预扩容

建议内部常量：

```text
storageSlotsPerPage = 27
initialWarmPages = 2
bufferWarmPages = 2
growthChunkPages = 2
prewarmThreshold = 75%
```

策略：

- 玩家首次使用大末影箱时，在后台准备初始额外页面。
- 当可用的 `READY` 空闲页数低于 2，或已用槽位超过 75% 时，后台按 2 页一组扩容。
- 查看页滚动、终端搜索或单纯打开界面不得触发扩容。
- 达到 `bigEnderChestPageLimit` 后停止扩容。
- 页面分配必须通过同一容量管理器完成，原版菜单和终端不能各自创建页面。

### 9.3 预留事务

服务端提供：

```text
reserveSlots(owner, requestedSlots) -> PageReservation
commit(PageReservation, itemMovement)
rollback(PageReservation)
```

要求：

- 首先计算已有同类栈可合并的数量，再计算所需新槽位。
- 预留需要的新页面，并确保它们在主线程可立即作为内存容器使用。
- 批量存入执行前完成预留。
- 操作失败时源物品保持不变，未使用的预留页面可转为缓冲页。
- 不允许在逐组移动过程中临时等待页面 I/O。
- 单次批量请求必须有服务端上限，避免一次请求预留无限页面。

由于物品真相仍在假人 `playerdata` 中，新页面应先持久化空档案，再进入 `READY`。
页面内容可以随后按正常容器保存策略合并写回。

## 10. 假人页档案

### 10.1 身份生成

不得使用 `EntityPlayerMPFake.createFake()`，因为它会注册玩家、进入世界并广播数据。

页面身份要求：

- 不进行 Mojang 名称或皮肤查询。
- 生成 3–16 字符、符合原版用户名规则的稳定名称。
- UUID 使用相同名称可重新推导的离线 UUID，或持久化明确映射并保证可通过 Carpet
  重新创建同一 UUID。
- 名称在全服唯一，不能与在线玩家、已有假人页或 usercache 冲突。
- 将 `NameAndId` 写入 `server.services().nameToIdCache()` 并保存。
- 名称和 UUID 映射必须持久化，后续不得重新随机生成。

建议名称格式为 `fec` 加最多 13 位小写十六进制摘要。生成源包含服务器存档标识、
所有者 UUID、逻辑页号和冲突盐。发生冲突时增加盐并重新生成，完整记录最终结果。

### 10.2 标准数据

每个页面必须有标准 `playerdata/<uuid>.dat`：

- 物品只写标准 `EnderItems`。
- 保留文件中的其他标准玩家字段。
- 读取时使用玩家数据 DataFixer。
- 写入时使用当前 DataVersion 和标准 `ItemStackWithSlot` codec。
- 使用临时文件、原子替换和 `.dat_old` 备份。
- 未识别字段不得因一次末影箱操作而丢失，除非它们已被 DataFixer 明确迁移。

禁止把页面物品另存到 FCA 的 `SavedData`、JSON 或自定义容器文件。

### 10.3 加载和保持 Carpet 可恢复性

页面打开不应构造 `ServerPlayer`，因为完整 `ServerPlayer` 初始化会加载统计、成就等与
末影箱无关的玩家子系统。

建议流程：

1. 在 I/O 执行器上读取并解压页面 `playerdata`。
2. 在主线程执行必要的 Minecraft 解码，生成 `PlayerEnderChestContainer` 等价的 27 格容器。
3. 页面转 `READY` 后允许菜单访问。
4. 关闭或变更后主线程生成 NBT 快照。
5. I/O 执行器原子写回。

如果对应 UUID 已经真实在线，禁止同时读取磁盘副本。可以拒绝该页面访问，或直接使用在线
玩家的实际末影箱，但不得出现双快照。

审计要求：页面假人必须仍可通过 Carpet `/player <名称> spawn` 上线；上线后其普通末影箱
应能看到大末影箱中对应页面的物品。实现必须提供 GameTest 或明确手测记录验证一次。

## 11. 客户端工具对接

FCA 只实现服务端协议、DTO 和操作处理，不实现终端屏幕。

### 11.1 握手

使用 Fabric API 自定义 payload。建议通道命名空间：

```text
frostors-carpet-addition:big_ender_chest
```

客户端工具在进入服务器后发送：

```text
Hello {
    protocolVersion: VarInt,
    capabilities: VarInt,
    maxChunkBytes: VarInt
}
```

服务端回复：

```text
HelloAck {
    protocolVersion: VarInt,
    capabilities: VarInt,
    chunkByteLimit: VarInt
}
```

只有在握手成功且客户端发送过支持能力后才发送 FCA 自定义 payload。原版客户端不会发送
Hello，因此不得收到任何相关自定义包。

### 11.2 会话

原版大末影箱菜单打开后，如果客户端已握手，发送：

```text
SessionOpen {
    sessionId: long,
    containerId: VarInt,
    revision: VarLong,
    pageLimit: VarInt,
    flags: VarInt
}
```

客户端工具仍依赖服务端打开的箱子菜单提供玩家背包和 carried item 语义，但可以不渲染
原版 45 个展示槽。终端不能只凭客户端自己的 `containerId` 访问，必须由服务端会话表验证。

### 11.3 快照

初始快照使用分块传输，建议单块目标不超过 `256 KiB` 未压缩序列化数据，且每 tick 限制发送
预算，避免终端一次打开造成网络突发。

快照模型：

```text
SnapshotBegin {
    sessionId,
    snapshotId,
    revision,
    templateCount,
    slotCount,
    chunkCount
}

TemplateRecord {
    templateSerial: VarLong,
    item: ItemStack.OPTIONAL_STREAM_CODEC // 数量归一化为 1
}

SlotRecord {
    storagePage: VarInt,
    slot: unsigned byte,
    templateSerial: VarLong,
    amount: VarInt
}
```

`templateSerial` 是本次会话内的唯一物品模板编号。同一物品类型和组件组合只发送一次，
后续槽位和终端条目引用该编号。空槽不发送。

每个快照块带 `snapshotId`、块序号和校验值。客户端发现版本过期、校验失败或块缺失时，
丢弃该快照并请求重同步。

### 11.4 增量

任何成功事务都增加全局 `revision`，服务端发送：

```text
Delta {
    sessionId,
    baseRevision: VarLong,
    revision: VarLong,
    templateAdds: List<TemplateRecord>,
    slotChanges: List<SlotRecord>,
    pageAdded: List<VarInt>,
    capacityChanged: boolean
}
```

规则：

- 同一页面的连续变化可以在一个 tick 内合并。
- 删除或数量归零使用 `amount = 0` 的 `SlotRecord`。
- 新增模板在同一 Delta 的 `templateAdds` 中先发送。
- Delta 过大、基准版本不匹配或序列化失败时，改为发送新快照。
- 客户端工具必须支持未知字段跳过或明确拒绝不兼容的新主协议版本。

### 11.5 终端操作

客户端发送：

```text
ActionRequest {
    sessionId: long,
    containerId: VarInt,
    knownRevision: VarLong,
    storagePage: VarInt,
    slot: unsigned byte,
    expectedTemplateSerial: VarLong,
    action: VarInt,
    button: byte
}
```

建议 v1 动作：

- `TAKE_ONE`
- `TAKE_STACK`：最多取一个 `maxStackSize`；不可堆叠物品为 1
- `PUT_ONE`
- `PUT_STACK`：存入当前 carried item 的整组，不要求目标条目原本为空
- `QUICK_MOVE_TO_PLAYER`：最多移动一个 `maxStackSize` 到玩家背包
- `QUICK_MOVE_FROM_PLAYER`：存入指定玩家背包槽位的整组

`PUT_ONE` 与 `PUT_STACK` 的目标槽位仅用于选择物品模板和提供点击上下文，不得把该槽位
当作唯一可写入位置。服务端必须在全部存储页中寻找可合并的非满堆、空槽或预留容量。

客户端的左拖存入可以在视觉上跟随原版拖拽，但释放时必须发送一次 `PUT_STACK` 或专用的
批量存入请求。不得为拖拽经过的每个条目发送独立 `PUT_ONE`，否则会造成重复提交和语义
不一致。

服务端必须验证：

- 会话仍存在且属于当前玩家。
- `containerId` 与当前菜单一致。
- 当前规则仍开启。
- 页面 `READY`。
- 槽位范围合法。
- 槽位物品模板与 `expectedTemplateSerial` 一致。
- 玩家与菜单的 carried item 和背包满足动作前提。

客户端不得提交物品栈内容。操作结果通过：

```text
ActionResult {
    sessionId,
    requestId,
    status: VarInt,
    revision: VarLong
}
```

返回。失败状态至少区分：会话过期、版本过期、页面未就绪、槽位不匹配、容量不足、
背包已满、权限不足和内部错误。

### 11.6 批量存入

客户端工具可以提供“存入背包全部”的扩展动作，但必须走容量预留：

1. 服务端根据当前菜单中的玩家背包统计可存入物品。
2. 计算合并及空槽需求。
3. 调用 `reserveSlots`。
4. 预留成功后在一个服务端事务中移动物品。
5. 失败时保留源物品，并返回明确状态。
6. 成功后发送合并后的索引 Delta。

不要在客户端逐组发送无限动作来模拟批量存入。

## 12. 事务和并发规则

- 所有 Minecraft 容器和菜单状态只能在服务器主线程修改。
- 磁盘读取、压缩和写入可在有界 I/O 执行器执行。
- 每个页面同一时间最多有一个加载任务和一个保存任务。
- 使用页面版本号防止旧保存覆盖新内容。
- 保存任务按页面串行化，可合并中间版本，但最终必须保存最新 revision。
- 同一玩家的批量扩容和物品事务按顺序执行。
- 页面假人在线时禁止磁盘快照路径。
- 服务端停止前必须停止接收新事务并等待/同步刷完脏页面。
- 页面加载失败时进入 `ERROR`，显示不可操作状态并记录日志，不得用空容器覆盖原文件。
- 页面第一次保存失败时不得从可用容量中扣除；重试策略必须有上限。

## 13. 协议和网络性能

- 连接层已有 Minecraft 压缩。默认不做二次 gzip。
- 快照和 Delta 都按字节预算分块，不能依赖“单包通常不大”。
- 初始快照只向完成握手的客户端发送。
- 只允许一个正在发送的快照；新快照会取消旧任务。
- 页面增量按 tick 合并，避免每次点击单独发包。
- 终端搜索结果由客户端基于已有索引完成。若后续增加服务端搜索，必须使用独立请求/响应
  版本，不要扩大基础快照。
- 原版菜单和终端协议共享存储版本，但原版菜单只显示 45 个当前条目。

## 14. 测试计划

### 14.1 无外部依赖测试

至少覆盖：

- `ItemTemplateKey` 对 count 不敏感，对任意数据组件差异敏感。
- 聚合索引增加、减少、移动和重建。
- 插入时优先补齐同模板非满堆，不因分组存入顺序增加槽位占用。
- 多槽位散乱的同模板物品可以整理成理论最少的堆数。
- 整理模拟不会复制、丢失或改变物品组件，失败时状态保持不变。
- 容量预留按 27 格页面计算，不产生部分预留。
- 页面上限降低时不删除原有页面。
- 页面排序和 45 格查看页分页。
- 协议 VarInt/VarLong、模板序号、Delta 版本和编解码往返。
- 页面名称生成长度合法、稳定且冲突时可重新生成。

### 14.2 Fabric GameTest

至少覆盖：

- 规则关闭时原版末影箱行为不改变。
- 规则开启时原版客户端获得 `GENERIC_9x6` 菜单。
- 顶部 45 格显示聚合条目，底部 9 格不能被取出或放入。
- 底部 9 格即使没有对应功能也始终显示不可交互占位，不出现空控制槽。
- 控制按钮不能把其显示物品复制到玩家背包。
- 拒绝控制槽上的 SWAP、THROW、PICKUP_ALL、CLONE、拖拽和未知动作。
- 页面 0 与玩家原版末影箱实时共享内容。
- 额外页面加入后原版视图和索引可见内容。
- 当前查看页 45 格全满时，玩家背包物品仍可通过 Shift + 左键存入。
- 当前查看页 45 格全满时，鼠标携带物品左键任意查看条目仍可存入整组。
- 携带物品左拖查看页最终只提交一次存入，不复制物品。
- 从聚合条目取出普通物品时最多得到一个 `maxStackSize` 的物品堆。
- 从聚合条目取出不可堆叠物品时只得一个。
- 聚合条目总库存大于最大堆叠数时，一次取出和 Shift 取出都不会超过一个最大堆叠。
- 存入、取出、Shift 移动、拖拽和快速连续点击没有复制或丢失物品。
- 页面关闭、重新打开和服务器重载后物品保持一致。
- 假人页未构造 `ServerPlayer`、未进入 PlayerList。
- 页面假人通过 Carpet 上线后可看到对应标准末影箱内容。

涉及运行期 GameTest 时必须获得用户明确同意并使用：

```powershell
.\gradlew.bat :26.2:runGameTest :26.3:runGameTest -PacceptMinecraftEula=true
```

不得默认接受 EULA。

### 14.3 客户端工具协议测试

FCA 不测试终端 GUI，但应提供：

- 独立协议 codec 单元测试。
- 未握手客户端不收到 S2C 自定义包。
- Hello/Ack 版本不兼容时安全拒绝。
- 快照分块、校验、过期快照丢弃。
- 原版客户端仍能打开并使用 6×9 菜单。
- 终端客户端在界面打开时消费 R 键，不触发本地或第三方整理。

## 15. 实施顺序

1. 增加规则、翻译、README 占位和功能入口。
2. 实现 `ItemTemplateKey`、聚合索引和容量管理纯逻辑测试。
3. 实现 `page 0` 原版末影箱映射及 6×9 菜单，不创建假人页。
4. 实现假人 profile、页面注册表和标准 `playerdata` 读写。
5. 接入异步页面加载、保存、预扩容和容量预留。
6. 实现 45 条目视图、排序、翻页和控制行。
7. 实现批量存取事务及所有并发、断线、关闭处理。
8. 实现客户端协议 DTO、握手、快照、Delta 和动作处理。
9. 补齐单元测试、GameTest、README 和协议说明。
10. 运行 26.2/26.3 双版本构建并检查产物。

## 16. 完成标准

实施完成后至少满足：

- `bigEnderChest` 默认关闭，开启后原版客户端无模组可用。
- 原版界面是合法 `GENERIC_9x6`，45 个条目槽加 9 个控制槽。
- 底部 9 格均有按钮、禁用按钮或不可交互占位，不存在空控制槽。
- 未完成握手的客户端不会收到终端协议数据。
- 终端界面消费 R 键，服务端拒绝非白名单批量槽位操作。
- 客户端工具无需编译 FCA 的菜单或存储内部类，只需协议 API。
- 所有额外物品仍存放在假人标准 `EnderItems` 中。
- 不创建持续在线或参与 tick 的假人。
- 快速连续操作不会因异步页面创建而部分失败；容量不足时源物品完整保留。
- 查看页全满不阻止 Shift 左击或携带物品左击/左拖存入。
- 任何直接取出操作最多产生一个最大堆叠，不可堆叠物品最多一个。
- 页面扩容不会使已打开菜单出现服务端/客户端槽位错配。
- 关闭规则不删除数据，移除模组后原版世界仍可加载。
- 26.2 和 26.3 的 `build`、`logicTest` 和 GameTest 编译通过。
- 新增编译警告视为回归。
- README 与实际规则、默认值、页面容量和控制方式一致。

## 17. 实施时必须再次核对的 API

26.2 和 26.3 的以下 API 必须在编码前逐一确认：

- `MenuType.GENERIC_9x6`、`ChestMenu` 自定义容器构造器及菜单打开流程。
- `AbstractContainerMenu.clicked`、拖拽和 `quickMoveStack` 的 26.x 签名。
- `PlayerEnderChestContainer.fromSlots/storeAsSlots` 与 `ItemStackWithSlot.CODEC`。
- `PlayerDataStorage` / `PlayerList.loadPlayerData` / `ValueInput` / `ValueOutput`。
- `NbtIo`、DataFixer、`DataVersion` 和原子替换工具。
- Fabric API 26.2/26.3 的 S2C/C2S custom payload 注册与发送 API。
- `ItemStack.OPTIONAL_STREAM_CODEC` 或两个版本中可用的等价 codec。

不要假设 26.2 与 26.3 的这些内部签名完全一致。共享源码中的任何差异应通过现有版本属性
或最小兼容层处理，不要把 Java 源码复制到 `versions/<版本>`。

