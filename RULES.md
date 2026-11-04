# Frostor's Carpet Addition - Carpet Rules

FCA 把全部规则注册到 Carpet 的 `fca` 分类。规则名区分大小写，并随世界的
`carpet.conf` 保存。

```text
/carpet list fca
/carpet <规则名>
/carpet <规则名> <值>
/carpet <规则名> reset
```

## 规则总表

| 规则 | 默认值 | 作用端 | 主要取值 | 说明 |
| --- | --- | --- | --- | --- |
| `bigEnderChest` | `false` | 服务端（菜单可由客户端使用） | `true` / `false` | 启用服务端大末影箱 |
| `bigEnderChestPageLimit` | `64` | 服务端 | `1..1024` 或 `-1` | 每名玩家的额外存储页上限，不含原版页 0 |
| `bigEnderChestLiveFakePlayers` | `false` | 服务端 | `true` / `false` | 实际存取页面时是否允许自动页面假人上线 |
| `easyPlaceProtocolExtra` | `false` | 服务端 | `true` / `false` | 启用 Easy Place V2-Extra 握手和方块状态处理 |
| `onlineProfileFallback` | `false` | 服务端 | `true` / `false` | 私人服务器在线资料回退 |
| `playerLadder` | `false` | 服务端 | `true` / `false` | Player Ladder 总开关 |
| `playerLadderClickMode` | `ride` | 服务端 | `ride` / `pick_up` / `none` | 空手右键实体时的行为 |
| `playerLadderPickupDepthLimit` | `16` | 服务端 | 整数，建议 `>= 0` | 举起实体时的最大层数 |
| `playerLadderRideDepthLimit` | `16` | 服务端 | 整数，建议 `>= 0` | 骑乘实体时的最大层数 |
| `playerLadderAllowNonPlayers` | `false` | 服务端 | `true` / `false` | 是否允许非玩家实体参与交互 |
| `playerLadderIgnoredEntities` | 见下方默认值 | 服务端 | 逗号分隔的实体 ID 或 `#` 标签 | 永远不允许骑乘或举起的实体 |
| `playerLadderDismountOnGameModeChange` | `false` | 服务端 | `true` / `false` | 载体切换游戏模式时是否让乘客下马 |
| `playerLadderAllowRiderStacking` | `true` | 服务端 | `true` / `false` | 是否允许三名及以上玩家继续堆叠 |
| `playerLadderAllowFakePlayers` | `false` | 服务端 | `true` / `false` | 是否允许与 Carpet 假人交互 |
| `playerLadderClientAimingFix` | `true` | 客户端 | `true` / `false` | 堆叠玩家的客户端准星修正 |

## 大末影箱

### `bigEnderChest`

功能总开关。关闭后，右键末影箱使用原版行为，同时暂停 FCA 的大末影箱菜单、
终端会话和页面存储服务。原版客户端获得 6×9 聚合菜单；扩展终端由安装了 TriWasa
的客户端使用。

### `bigEnderChestPageLimit`

设置每名玩家的额外假人存储页上限，不包含玩家自己的原版页 0。默认 `64`，配置范围
为 `1..1024`；`-1` 表示不设配置上限。降低该值不会删除已有页面。

```text
/carpet bigEnderChestPageLimit 64
/carpet bigEnderChestPageLimit -1
```

### `bigEnderChestLiveFakePlayers`

默认关闭。开启后，只有页面实际发生存入或取出时才请求对应页面假人上线；索引、
搜索、排序、翻页、扩容和只读浏览仍使用离线 `playerdata` 路径。

自动页面假人使用 FCA 保存的本地 UUID 和名称，不查询 Mojang profile。租约按页面
独立计时，页面连续 200 tick 未使用后回收。关闭菜单不会触发统一下线；规则关闭或
服务器停止时会先同步数据再回收。手动通过 Carpet 上线的假人不受 FCA 自动租约管理。

该路径会执行完整的玩家登录流程，首次批量上线可能产生明显的 server tick 尖峰。

## Easy Place V2-Extra

### `easyPlaceProtocolExtra`

启用与兼容客户端之间的 V2-Extra 握手，并在服务端恢复精确方块状态。缺少能力、
握手失败或规则关闭时，客户端应回退到普通轻松放置行为。FCA 本身不包含对应的
客户端放置编码。

## 在线资料回退

### `onlineProfileFallback`

在正版模式下解析玩家请求的用户名及皮肤/披风属性，但仅当对应 UUID 已在本服务器
存在玩家档案时跳过 Mojang 会话验证。

该规则不验证账号所有权，只适用于受控的私人服务器；不应在公开或不可信服务器上
启用。

## Player Ladder

以下规则仅在 `playerLadder=true` 时产生交互效果。

### `playerLadderClickMode`

- `ride`：右键后骑乘目标实体。
- `pick_up`：右键后举起目标实体。
- `none`：不执行 Player Ladder 操作。

### `playerLadderPickupDepthLimit`

举起实体时向上查找的最大叠罗汉层数，默认 `16`。

### `playerLadderRideDepthLimit`

骑乘实体时向上查找的最大叠罗汉层数，默认 `16`。

### `playerLadderAllowNonPlayers`

是否允许非玩家实体作为 Player Ladder 目标，默认关闭。

### `playerLadderIgnoredEntities`

按逗号分隔的实体 ID 或 `#` 标签。默认值：

```text
minecraft:wither,minecraft:ender_dragon,minecraft:warden,minecraft:minecart,#minecraft:boat,#minecraft:dismounts_underwater
```

修改规则后立即清理并重建实体类型与标签缓存，不需要重启服务器。

### `playerLadderDismountOnGameModeChange`

载体切换游戏模式时是否让乘客下马，默认关闭。

### `playerLadderAllowRiderStacking`

是否允许三名及以上玩家继续堆叠，默认开启。关闭后仍允许两个玩家互相骑乘。

### `playerLadderAllowFakePlayers`

是否允许骑乘或举起 Carpet 假人，默认关闭。

### `playerLadderClientAimingFix`

仅客户端规则。开启时，堆叠玩家仍能被准星选中，默认开启；服务端骑乘结构不依赖
该规则。

## 变更维护

每条规则必须在 `FrostorSettings`、`en_us.json`、`zh_cn.json`、本文件、README
规则表和运行时处理代码之间保持一致。新增、删除、重命名、修改默认值或修改合法取值
时，必须同步更新以上位置并完成双版本构建。
