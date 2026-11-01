# 供应链加固待办

以下事项已确认存在，但按当前决定暂缓处理。完成前不应把构建产物视为完全可复现或
已完成第三方来源审计。

## 1. 固定构建工具与依赖校验

- [ ] 在 `gradle/wrapper/gradle-wrapper.properties` 中为 Gradle 9.7.1 分发包添加
      `distributionSha256Sum`，并从 Gradle 官方校验和页面获取该值。
- [ ] 将 `gradle.properties` 中的 `loom_version=1.17-SNAPSHOT` 固定到已验证的具体版本。
      当前构建实际解析为 Fabric Loom `1.17.21`。
- [ ] 评估启用 Gradle dependency locking 或 dependency verification；启用后提交生成的锁文件
      或校验元数据，并在 `26.2`、`26.3` 上重新构建。

验收条件：清空 Gradle 缓存后仍能从固定版本按记录的校验值完成双版本构建。

## 2. 记录 Carpet 二进制来源与许可

- [ ] 在 `libs/` 旁增加来源清单，记录官方发布地址、版本、发布日期、预期 SHA-256 和获取方式。
- [ ] 确认 `fabric-carpet-26.2.jar` 与 `fabric-carpet-26.3.jar` 的来源可验证，并决定继续
      跟踪二进制文件还是改由受控仓库获取。
- [ ] 核验再分发这些二进制文件时对 Carpet MIT 许可及版权声明的附带要求；必要时补充
      third-party notices。

当前核验值：

| 文件 | Carpet 版本 | SHA-256 |
| --- | --- | --- |
| `libs/fabric-carpet-26.2.jar` | `26.2+v260616` | `F6ADA912AF65C91536D4B0D80ADF26CC438253252DDAF259F2C6617AE471311C` |
| `libs/fabric-carpet-26.3.jar` | `26.3+v260915` | `7C40B93E807ED914064176898037117097446C6CC76E6A80A856C8BF7F7659F9` |

验收条件：仓库维护者能根据清单验证两个 JAR 的官方来源、版本、校验值及许可义务。
