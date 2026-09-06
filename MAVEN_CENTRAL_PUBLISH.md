# MAVEN_CENTRAL_PUBLISH —— Sonatype 中央仓库发布指南

> 本文档基于 2026-09-06 本仓库（`io.github.zusrsoft` 命名空间）的真实发布实战整理，
> 包含完整发布流程、依赖链发布顺序、踩坑实录与注意事项。
> 发布目标：Sonatype Central Portal —— https://central.sonatype.com
> 命名空间：**io.github.zusrsoft**（已在 Portal 验证）

---

## 目录

1. [发布概览](#1-发布概览)
2. [前置条件（一次性准备）](#2-前置条件一次性准备)
3. [凭证配置（踩坑重灾区）](#3-凭证配置踩坑重灾区)
4. [项目发布配置说明](#4-项目发布配置说明)
5. [发布操作流程（详细步骤）](#5-发布操作流程详细步骤)
6. [常见校验错误对照表（踩坑实录）](#6-常见校验错误对照表踩坑实录)
7. [发布结果确认](#7-发布结果确认)
8. [安全注意事项](#8-安全注意事项)
9. [附录](#9-附录)

---

## 1. 发布概览

### 1.1 制品矩阵

本仓库一次完整发布会向 Central 上传 3 个模块的全平台构件：

| 模块             | 坐标                                       |
| ---------------- | ------------------------------------------ |
| markdown-parser  | `io.github.zusrsoft:markdown-parser:1.5.2` |
| markdown-runtime | `io.github.zusrsoft:markdown-runtime:1.5.2`|
| markdown-renderer| `io.github.zusrsoft:markdown-renderer:1.5.2`|

每个模块包含以下 publication（vanniktech 插件自动生成）：

- `jvm`(jar) / `android`(aar) / `js`(klib) / `wasmJs`(klib) / `iosArm64`(klib) / `iosSimulatorArm64`(klib)
- `kotlinMultiplatform`（.module 元数据 + allMetadata jar）

每个 publication 自带：主构件、sources.jar、javadoc.jar（KMP 为空壳）、.pom、.module、`.asc` 签名。

> `markdown-preview` / `composeApp` / `androidapp` / benchmark 模块**不发布**（无 `mavenPublishing` 配置）。

### 1.2 上游依赖链

`markdown-renderer` 编译期依赖同命名空间的三件套（均为 `implementation`，**不会进入发布 POM**，但构建时必须可解析）：

| 上游依赖                | 坐标（io.github.zusrsoft）                    | 已发布版本 |
| ----------------------- | --------------------------------------------- | ---------- |
| latex 三件套            | `latex-base` / `latex-parser` / `latex-renderer` | 1.5.4      |
| codehighlight 两件套    | `codehighlight-parser` / `codehighlight-render`  | 1.1.2      |
| diagram                 | `diagram-render`                                  | 1.0.4      |

**依赖链发布顺序**：latex → codehighlight / diagram → Markdown。
上游发新版本后，必须等其同步到 repo1.maven.org，本仓库才能构建（见 5.2 场景 B）。

### 1.3 发布链路

```
RELEASE_TO_CENTRAL=1 gradlew publishAllPublicationsToMavenCentralRepository
   → 编译全平台（Android/iOS/JVM/JS/WasmJs）
   → GPG 签名（每个构件生成 .asc）
   → 上传（Central Portal API，认证 = User Token）
   → Sonatype 校验：签名可验证 / 命名空间匹配 / POM 规范 / javadoc & sources 齐全
   → publishToMavenCentral(true) 自动发布 → PUBLISHED
   → 几分钟~30 分钟内同步到 repo1.maven.org
```

### 1.4 发布方式

| 方式           | 适用场景                             | 本仓库现状 |
| -------------- | ------------------------------------ | ---------- |
| 本地命令行发布 | 首次发布、调试签名问题、上游依赖联动 | ✅ 使用中  |
| GitHub Actions | 例行发版（Release 触发）             | 暂未配置（latex 仓库有 publish.yml 可参考） |

---

## 2. 前置条件（一次性准备）

### 2.1 注册 Central Portal 账号并验证命名空间

1. 打开 https://central.sonatype.com → 用 GitHub 账号（**zusrsoft**）登录
2. 进入 **Namespaces** 页面，确认命名空间 `io.github.zusrsoft` 状态为 **Verified**
   - GitHub 命名空间验证方式：Portal 要求在 `github.com/zusrsoft` 账号下创建一个指定名称的临时空仓库，按页面提示操作后自动通过
3. ⚠️ 命名空间必须与发布坐标 groupId 完全一致，否则上传直接被拒（见 6.5）

### 2.2 生成访问令牌（User Token）

1. Portal 右上角头像 → **Account** → **Generate User Token**
2. 得到一对"用户名 + 密码"（均为随机字符串）
3. 这是发布 API 的认证凭证，**等同于账号密码，切勿提交到仓库**

### 2.3 准备 GPG 密钥（本机已就绪）

```powershell
# 生成密钥（记住 passphrase）
gpg --gen-key

# 查看密钥列表
gpg --list-secret-keys --keyid-format short

# 上传公钥到 keyserver（Sonatype 支持的服务器，两处都传）
gpg --keyserver keyserver.ubuntu.com --send-keys 88DE87D5
gpg --keyserver keys.openpgp.org --send-keys 88DE87D5

# 验证公钥已可查询
gpg --keyserver keyserver.ubuntu.com --recv-keys 88DE87D5
```

注意事项：

- ⚠️ **keys.openpgp.org 需要邮箱验证**：上传后向密钥 uid 邮箱发送验证链接，点击确认后公钥才对外提供
- ⚠️ 公钥上传后 Sonatype 有**查询缓存（约 10~60 分钟）**，刚上传就发布会报"找不到公钥"（见 6.4）
- 忘记 GPG 密码无法找回，只能重新生成密钥并重新上传公钥；旧密钥已发布的版本不受影响
- 本机密钥信息见 [9.2 附录：本机密钥信息](#92-本机密钥信息公开部分)

---

## 3. 凭证配置（踩坑重灾区）

### 3.1 ⚠️ 坑一：确认 GRADLE_USER_HOME 的真实位置

**Gradle 只读 `GRADLE_USER_HOME` 指向目录下的 `gradle.properties`，不一定是 `~/.gradle`！**

```powershell
# 先查环境变量
echo $env:GRADLE_USER_HOME
# 本机实测：D:\Dev-tools\gradle-repository
# → 凭证必须写入 D:\Dev-tools\gradle-repository\gradle.properties
#   写到 C:\Users\<user>\.gradle\gradle.properties 是无效的！
```

**症状**：凭证明明配了，发布却报 `Cannot perform signing task ... because it has no configured signatory`。

### 3.2 五项凭证属性

写入 `GRADLE_USER_HOME\gradle.properties`（本机已配置就绪）：

```properties
mavenCentralUsername=令牌用户名
mavenCentralPassword=令牌密码
signingInMemoryKeyId=88DE87D5
signingInMemoryKeyPassword=GPG密钥密码
signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n\<换行内容见 3.3>
```

### 3.3 ⚠️ 坑二：私钥必须是"单行 \n 转义"格式

properties 文件**不支持多行值**。把 `gpg --armor --export-secret-keys` 的输出原样粘贴进去是无效的——Gradle 只会读到第一行。

正确格式（一行写完，换行用字面 `\n` 表示，armor 格式头部空行如有必须保留）：

```properties
signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n\nlQVGBZ+XXXXX...\n...\n=XXXXX\n-----END PGP PRIVATE KEY BLOCK-----
```

**PowerShell 一键转换**（从 gpg 直接生成合法单行值）：

```powershell
$single = (gpg --armor --export-secret-keys 88DE87D5 | Where-Object { $_ -ne $null }) -join '\n'
# $single 即为 signingInMemoryKey= 后面的完整值
```

### 3.4 ⚠️ 坑三：keyId 必须是 8 位短 ID

Gradle 的 `PgpKeyId` **只接受 8 位十六进制短 ID**（如 `88DE87D5`）：

| keyId 写法                         | 结果                                                 |
| ---------------------------------- | ---------------------------------------------------- |
| `88DE87D5`（8 位短 ID）            | ✅ 正常                                               |
| `36C9087F88DE87D5`（16 位长 ID）   | ❌ `The key ID must be in a valid form (eg 00B5050F)` |
| `9FD4A322...88DE87D5`（40 位指纹） | ❌ `Could not read PGP secret key`                    |

短 ID = 长密钥 ID 的**最后 8 位** = 指纹的**最后 8 位**。

### 3.5 验证凭证是否被 Gradle 读到

```powershell
.\gradlew.bat help --init-script checkprops.gradle
```

探针脚本（确认属性存在，不回显内容）：

```groovy
// checkprops.gradle
gradle.projectsLoaded {
    def root = gradle.rootProject
    ['mavenCentralUsername','mavenCentralPassword','signingInMemoryKeyId',
     'signingInMemoryKeyPassword','signingInMemoryKey'].each { n ->
        def v = root.findProperty(n)
        println "PROP ${n} -> " + (v == null ? 'NULL' : "OK length=${String.valueOf(v).length()}")
    }
}
```

### 3.6 环境变量方式（CI / 临时覆盖）

```properties
ORG_GRADLE_PROJECT_mavenCentralUsername=...
ORG_GRADLE_PROJECT_mavenCentralPassword=...
ORG_GRADLE_PROJECT_signingInMemoryKeyId=88DE87D5
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=...
ORG_GRADLE_PROJECT_signingInMemoryKey=多行私钥原文即可（env 不受 properties 单行限制）
```

---

## 4. 项目发布配置说明

以下配置已就绪，日常发版无需改动：

### 4.1 发布插件

- 根 `build.gradle.kts`：`alias(libs.plugins.mavenPublish) apply false`（vanniktech **0.36.0**）
- 三个发布模块各自 `alias(libs.plugins.mavenPublish)`

### 4.2 mavenPublishing 配置（三个模块相同模式）

```kotlin
mavenPublishing {
    // 仅在显式设置 RELEASE_TO_CENTRAL 环境变量时启用 Central 发布与签名
    // （签名密钥由 signingInMemory* 属性提供；本地 mavenLocal 发布不签名）
    if (providers.environmentVariable("RELEASE_TO_CENTRAL").isPresent) {
        publishToMavenCentral(true)   // true = 校验通过后自动发布，无需手动点 Release
        signAllPublications()         // 使用 signingInMemory* 属性签名
    }

    coordinates("io.github.zusrsoft", "markdown-parser", rootProject.property("VERSION").toString())
    pom { /* name/description/url/license/developer/scm 齐全，满足 Central 校验 */ }
}
```

**门控说明**：不带 `RELEASE_TO_CENTRAL` 时，`publishToMavenLocal` 走纯本地发布（不签名）；
带 `RELEASE_TO_CENTRAL=1` 时本地预演也会启用签名，可提前验证签名配置（见 5.1 第 2 步）。

### 4.3 版本号管理

- 版本唯一来源：根 `gradle.properties` 的 `VERSION`（当前 `1.5.2`）
- ⚠️ Central **不允许覆盖发布**：同命名空间下已发布的版本号永久占用（校验失败的部署不占用，可重试）
- 发布前用 https://central.sonatype.com/search?q=io.github.zusrsoft 检查版本占用
- 版本号**不带构建后缀**（如 `-sdk36`）：中央仓库版本保持干净的 `MAJOR.MINOR.PATCH` 口径

### 4.4 上游依赖坐标

`gradle/libs.versions.toml` 中 latex / codehighlight / diagram 依赖全部指向 `io.github.zusrsoft`。
上游升级流程：上游仓库发新版 → 等其同步到 repo1 → 更新本仓库 toml 版本引用 → 发布本仓库。

---

## 5. 发布操作流程（详细步骤）

### 5.1 场景 A：常规发版（上游依赖不变）

#### 第 1 步：确认版本号可用

到 https://central.sonatype.com/search?q=io.github.zusrsoft 检查目标版本号是否已发布过。

#### 第 2 步：跑全量测试（AGENTS.md 要求）

```powershell
.\gradlew.bat jvmTest --no-configuration-cache
```

约 4 分钟，必须 BUILD SUCCESSFUL。

#### 第 3 步（可选但推荐）：本地预演（含签名）

```powershell
$env:RELEASE_TO_CENTRAL = "1"
.\gradlew.bat publishToMavenLocal --no-configuration-cache
Remove-Item Env:RELEASE_TO_CENTRAL   # 用完清理
```

产物落到 `%GRADLE_USER_HOME%\m2`，可提前发现签名/POM 问题，不上传。约 10 分钟（全平台）。

#### 第 4 步：正式发布

```powershell
$env:RELEASE_TO_CENTRAL = "1"
.\gradlew.bat publishAllPublicationsToMavenCentralRepository --no-configuration-cache
Remove-Item Env:RELEASE_TO_CENTRAL   # 用完清理
```

- 全平台编译约 4~6 分钟（增量会更快）
- 日志出现 `Validating deployment <uuid>...` 表示上传完成、进入 Sonatype 校验
- 自动发布模式下：校验通过 → `Deployment is being published to Maven Central` → `BUILD SUCCESSFUL`；校验失败 → 构建失败并打印全部错误明细
- ⚠️ 必须带 `--no-configuration-cache`（门控逻辑在配置阶段读取环境变量）

#### 第 5 步：确认结果

见第 7 节。

### 5.2 场景 B：含上游依赖链的完整发布（实战流程）

当 latex / codehighlight / diagram 需要一起发新版时：

```
1. 发布上游仓库（各自目录独立执行）
   latex:      .\gradlew.bat publishAllPublicationsToMavenCentralRepository --no-configuration-cache
   codehigh:   同上（本地路径 ..\codehigh）
   diagram:    同上（本地路径 ..\diagram）

2. 等待上游同步到 repo1（关键！不同步完，下游构建必失败，见 6.8）
   轮询脚本见 7.3

3. 更新本仓库 gradle/libs.versions.toml 的版本引用

4. 按场景 A 流程发布 Markdown
```

> 实战时间参考（2026-09-06）：codehighlight 发布完成 → repo1 可解析约耗时 6 分钟。

### 5.3 上游仓库发布注意事项

- **latex / diagram**：本地仓库坐标已切 `io.github.zusrsoft`，无 `RELEASE_TO_CENTRAL` 门控，直接执行发布命令
- **codehigh**：本地仓库从 `huarangmeng/codehigh` clone 而来，切坐标方式与 Markdown 相同
  （`coordinates()` + POM url/scm/developer 三个 replace），GitHub 上需建 `zusrsoft/codehigh` 仓库
- 上游仓库发布前同样确认：`gradle.properties` 的 VERSION 未占用、凭证就绪（共用同一套）

---

## 6. 常见校验错误对照表（踩坑实录）

以下错误均在 2026-09 实战中真实出现，按排查顺序排列。

### 6.1 `Cannot perform signing task ... because it has no configured signatory`

**原因**：Gradle 根本没读到签名属性。

排查顺序：

1. 凭证写错文件位置（`~/.gradle` vs 真实 `GRADLE_USER_HOME`）——最常见，见 3.1
2. 属性名拼写错误（注意大小写敏感）
3. Gradle 守护进程缓存了旧的属性文件（`.\gradlew.bat --stop` 后重试）

### 6.2 `Could not read PGP secret key`

**原因**：私钥内容或 keyId 无法解析。

排查顺序：

1. keyId 是 40 位指纹或 16 位长 ID → 改为 8 位短 ID（见 3.4）
2. 私钥单行转义时丢了头部空行 → 补回（见 3.3）
3. 私钥内容有残缺（长度异常、头尾标记不完整）

### 6.3 `The key ID must be in a valid form (eg 00B5050F), given value: xxxxxxxx`

**原因**：keyId 给了 16 位长 ID。**必须 8 位短 ID**。加 `--stacktrace` 能在堆栈里看到这条真实原因，否则只显示笼统的 6.2 错误。

> 技巧：签名失败先跑 `.\gradlew.bat markdown-parser:signJvmPublication --stacktrace` 单独定位，比整包发布快得多。

### 6.4 `Could not find a public key by the key fingerprint`

**原因**：签名用的 GPG **公钥**没上传 keyserver，或 Sonatype 缓存未刷新。

处理：

1. 确认公钥已上传：`https://keyserver.ubuntu.com/pks/lookup?search=0x<指纹>&op=vindex&fingerprint=on`
2. keys.openpgp.org 的上传需完成**邮箱验证**才生效（见 2.3）
3. 以上都确认后，就是 Sonatype 缓存延迟——**等 10~60 分钟重试即可**，期间重试只会重复失败

### 6.5 命名空间被拒（namespace not verified / 403）

**原因**：groupId 与 Portal 已验证命名空间不一致。

- 本仓库 2026-09 实战：坐标 `io.github.huarangmeng`（原作者）vs Portal 命名空间 `io.github.zusrsoft`
- 解决：3 个 build.gradle.kts 的 `coordinates()` + POM url/scm/developer 切换为 zusrsoft；
  README badge、依赖示例、外部依赖表格与 `gradle/libs.versions.toml` **全部同步切换**（含上游依赖坐标）
- ⚠️ 切换命名空间对老用户是破坏性变更，需在 README 中提示迁移

### 6.6 版本已存在

同版本号发布第二次会被拒。校验失败的部署（FAILED 状态）不占用版本号，可直接重试。

### 6.7 `Component with coordinate '...' is currently being published in another deployment`

**原因**：目标坐标正被**另一个 deployment** 持有（通常是之前某次上传中断后，Portal 侧的部署仍处于 PENDING/VALIDATING/PUBLISHING 状态，形成坐标锁）。

实战案例：diagram 发布时报 `io.github.zusrsoft:diagram-core-android:1.0.4` 被 deployment `c616485e` 占用——而该部署其实已通过校验、正在自动发布中。

处理：

1. 先查部署列表（见 7.2）确认占用者的状态：
   - `PUBLISHING` → **无需任何操作**，它就是这次发布本身，等它变 PUBLISHED 即可
   - `PENDING`（卡死的僵尸上传）→ Portal 页面手动删除该部署，或等 Portal 自动过期后重试
   - `FAILED` → 不占坐标，直接重发
2. 自己这次报错的部署会标记 FAILED，不占版本号

### 6.8 `Could not resolve io.github.zusrsoft:xxx`（构建期依赖解析失败）

**原因**：上游依赖刚发布，repo1.maven.org 还没同步完（几分钟~30 分钟），Gradle 拉不到。

处理：用 7.3 的轮询脚本等 repo1 出现上游的 `maven-metadata.xml` 后再构建。
⚠️ 本地 `mavenLocal` 没有这些制品（直接发布到 Central 不经过 mavenLocal），不能靠缓存绕过。

### 6.9 签名/发布任务成功但 `Failed to stop service 'maven-central-build-service'`

发布校验失败的伴随症状（vanniktech 构建服务收尾时报错）。真正的失败原因在其上方的
`Deployment <uuid> failed validation: ...` 输出里，按对应小节处理。

---

## 7. 发布结果确认

### 7.1 Portal 页面

https://central.sonatype.com/publishing/deployments 查看部署列表：

| 状态                 | 含义                                             |
| -------------------- | ------------------------------------------------ |
| PENDING / VALIDATING | 上传中 / 校验中                                   |
| VALIDATED            | 通过（自动发布模式下瞬间转 PUBLISHING）           |
| PUBLISHING           | 正在发布到 repo1（校验已过，等同成功，勿重复发）  |
| PUBLISHED            | ✅ 发布成功，永久生效                             |
| FAILED               | 校验失败，点击查看明细；不占用版本号，修复后重试  |

### 7.2 API 查询（脚本化确认）

```powershell
# 读取凭证（不回显）并列出最近部署及状态
$props = @{}
Get-Content -LiteralPath "D:\Dev-tools\gradle-repository\gradle.properties" |
  ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { $props[$Matches[1]] = $Matches[2] } }
$pair = $props['mavenCentralUsername'] + [char]58 + $props['mavenCentralPassword']
$auth = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
$r = Invoke-RestMethod -Uri "https://central.sonatype.com/api/v1/publisher/deployments" -Headers @{Authorization=$auth}
$r.deployments | Select-Object -First 5 | ForEach-Object { "$($_.deploymentId)  $($_.deploymentState)" }
```

### 7.3 repo1 同步轮询（发布后 / 下游构建前）

```powershell
# 等待指定坐标在 repo1 可见（每 60s 查一次，最多 11 分钟）
$urls = @(
  'https://repo1.maven.org/maven2/io/github/zusrsoft/<artifact>/maven-metadata.xml'
  # 可列多个，如 markdown-parser / markdown-runtime / markdown-renderer
)
$deadline = (Get-Date).AddMinutes(11)
do {
  $ok = $true
  foreach ($u in $urls) {
    try { $req = [System.Net.HttpWebRequest]::Create($u); $req.Timeout = 15000
          $resp = $req.GetResponse(); $resp.Close() } catch { $ok = $false }
  }
  if ($ok) { "ALL READY"; break }
  "pending... $(Get-Date -Format HH:mm:ss)"; Start-Sleep -Seconds 60
} while ((Get-Date) -lt $deadline)
```

- Portal 显示 PUBLISHED 后，repo1 同步需要**几分钟~30 分钟**，期间 404 属正常
- 搜索索引（central.sonatype.com/search）更新更慢，约 1~2 小时
- 验证地址：`https://repo1.maven.org/maven2/io/github/zusrsoft/markdown-parser/1.5.2/`

### 7.4 消费方依赖验证

```toml
[versions]
markdown = "1.5.2"

[libraries]
markdown-parser = { module = "io.github.zusrsoft:markdown-parser", version.ref = "markdown" }
markdown-runtime = { module = "io.github.zusrsoft:markdown-runtime", version.ref = "markdown" }
markdown-renderer = { module = "io.github.zusrsoft:markdown-renderer", version.ref = "markdown" }
```

---

## 8. 安全注意事项

- 令牌、GPG 密码、私钥**绝不提交到仓库**；凭证只放 `GRADLE_USER_HOME\gradle.properties`（本机为 `D:\Dev-tools\gradle-repository\gradle.properties`）
- 写错位置的凭证副本（如 `C:\Users\<user>\.gradle\gradle.properties` 中的凭证段落）用完即删——2026-09 实战已清理过一次，并保留英文注释指明真实位置
- `RELEASE_TO_CENTRAL` 环境变量用完即清（`Remove-Item Env:RELEASE_TO_CENTRAL`），避免日常本地发布误签名
- 日志/文档中引用凭证时只写属性名和长度，不回显内容
- 公钥、密钥 ID、指纹是公开信息，可以写进文档和 issue
- 令牌泄露应立即到 Portal 重新生成（Account → Generate User Token 会作废旧令牌）

---

## 9. 附录

### 9.1 GPG 命令速查

```powershell
gpg --gen-key                                   # 生成密钥
gpg --list-secret-keys --keyid-format short     # 列出密钥（看短 ID）
gpg --armor --export-secret-keys 88DE87D5       # 导出私钥（配置 signingInMemoryKey 用）
gpg --armor --export 88DE87D5                   # 导出公钥
gpg --keyserver keyserver.ubuntu.com --send-keys 88DE87D5   # 上传公钥
gpg --keyserver keyserver.ubuntu.com --recv-keys 88DE87D5   # 验证公钥可查询
# 测试自己是否记得 GPG 密码：
"test" | gpg --batch --pinentry-mode loopback -u 88DE87D5 --clearsign
```

### 9.2 本机密钥信息（公开部分）

- uid：`zusr <zusrsoft@163.com>`
- 短 ID：`88DE87D5`
- 指纹：`9FD4A322C71073ED27A10A0636C9087F88DE87D5`
- 公钥已上传：keyserver.ubuntu.com、keys.openpgp.org（均已邮箱验证）

### 9.3 相关仓库与文档

| 仓库/文件                | 说明                                         |
| ------------------------ | -------------------------------------------- |
| `Markdown`（本仓库）     | 发布 markdown 三件套，门控 `RELEASE_TO_CENTRAL` |
| `..\latex`               | 上游 latex（已发布 1.5.4）                    |
| `..\codehigh`            | 上游 codehighlight（已发布 1.1.2）            |
| `..\diagram`             | 上游 diagram（已发布 1.0.4）                  |

---

*最后更新：2026-09-06（基于 markdown 1.5.2 + 上游三库同日发布实战整理）*
