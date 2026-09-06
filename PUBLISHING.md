# Maven Central 发布手册（Markdown 仓库）

> 基于 latex 仓库（`io.github.zusrsoft:latex-*`）发布实战手册定制，适用于本仓库三模块发布。
> 发布目标：Sonatype Central Portal —— https://central.sonatype.com
> 命名空间：**io.github.zusrsoft**（已在 Portal 验证）

---

## 目录

1. [发布概览](#1-发布概览)
2. [前置条件（一次性准备）](#2-前置条件一次性准备)
3. [凭证配置（踩坑重灾区）](#3-凭证配置踩坑重灾区)
4. [项目发布配置说明](#4-项目发布配置说明)
5. [本地发布操作步骤](#5-本地发布操作步骤)
6. [常见校验错误对照表（踩坑实录）](#6-常见校验错误对照表踩坑实录)
7. [发布结果确认](#7-发布结果确认)
8. [安全注意事项](#8-安全注意事项)
9. [附录：GPG 命令速查](#9附录gpg-命令速查)

---

## 1. 发布概览

### 1.1 发布产物

一次完整发布会上传 3 个模块的全平台构件：

| 模块             | 坐标                                    |
| ---------------- | --------------------------------------- |
| markdown-parser  | `io.github.zusrsoft:markdown-parser`    |
| markdown-runtime | `io.github.zusrsoft:markdown-runtime`   |
| markdown-renderer| `io.github.zusrsoft:markdown-renderer`  |

每个模块包含以下 publication（vanniktech 插件自动生成）：

- `jvm` / `android`(aar) / `js`(klib) / `wasmJs`(klib) / `iosArm64`(klib) / `iosSimulatorArm64`(klib)
- `kotlinMultiplatform`（.module 元数据 + allMetadata jar）

每个 publication 自带：主构件、sources.jar、javadoc.jar（KMP 为空壳）、.pom、.module、`.asc` 签名。

> `markdown-preview` / `composeApp` / `androidapp` / benchmark 模块**不发布**（无 `mavenPublishing` 配置）。

### 1.2 发布链路

```
RELEASE_TO_CENTRAL=1 gradlew publishAllPublicationsToMavenCentralRepository
   → 编译全平台 → 签名(GPG) → 上传(Central Portal API)
   → Sonatype 校验(签名可验证/命名空间/POM 规范/javadoc/sources)
   → publishToMavenCentral(true) 自动发布 → PUBLISHED
   → 约 30 分钟内同步到 repo1.maven.org
```

### 1.3 外部依赖说明

`markdown-renderer` 编译期依赖 latex / codehighlight / diagram 三件套，统一引用 `io.github.zusrsoft` 命名空间（latex 1.5.4、codehighlight 1.1.2、diagram-render 1.0.4 均已于 2026-09-06 发布至 Central），且均为 `implementation`，**不会进入发布 POM**。消费者 POM 中只出现 `markdown-parser` / `markdown-runtime`（api 依赖），无需关心这些坐标。

> 依赖链发布顺序：latex → codehighlight / diagram → Markdown。若上游库发新版本，先发布上游，再更新本仓库 `libs.versions.toml` 的版本引用。

---

## 2. 前置条件（一次性准备）

### 2.1 Central Portal 账号与命名空间

1. https://central.sonatype.com 用 GitHub 账号（**zusrsoft**）登录
2. **Namespaces** 页面确认 `io.github.zusrsoft` 状态为 **Verified**
3. ⚠️ 命名空间必须与发布坐标 groupId 完全一致，否则上传直接被拒（见 6.5）

### 2.2 访问令牌（User Token）

Portal 右上角头像 → **Account** → **Generate User Token**，得到一对"用户名 + 密码"。
**等同于账号密码，切勿提交到仓库。**

### 2.3 GPG 密钥（本机已就绪）

- uid：`zusr <zusrsoft@163.com>`
- 短 ID：`88DE87D5`
- 指纹：`9FD4A322C71073ED27A10A0636C9087F88DE87D5`
- 公钥已上传：keyserver.ubuntu.com、keys.openpgp.org（均已邮箱验证）

---

## 3. 凭证配置（踩坑重灾区）

### 3.1 ⚠️ 坑一：确认 GRADLE_USER_HOME 的真实位置

**Gradle 只读 `GRADLE_USER_HOME` 指向目录下的 `gradle.properties`，不一定是 `~/.gradle`！**

```powershell
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
signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n\<单行转义私钥>
```

### 3.3 ⚠️ 坑二：私钥必须是"单行 \n 转义"格式

properties 文件不支持多行值。导出后每个换行处写字面 `\n`，armor 格式的头部空行（如有）必须保留，否则报 `Could not read PGP secret key`。

```powershell
# PowerShell 一键转换
$single = (gpg --armor --export-secret-keys 88DE87D5 | Where-Object { $_ -ne $null }) -join '\n'
```

### 3.4 ⚠️ 坑三：keyId 必须是 8 位短 ID

| keyId 写法                         | 结果                                                 |
| ---------------------------------- | ---------------------------------------------------- |
| `88DE87D5`（8 位短 ID）            | ✅ 正常                                               |
| `36C9087F88DE87D5`（16 位长 ID）   | ❌ `The key ID must be in a valid form (eg 00B5050F)` |
| `9FD4A322...88DE87D5`（40 位指纹） | ❌ `Could not read PGP secret key`                    |

短 ID = 指纹最后 8 位。

### 3.5 验证凭证是否被 Gradle 读到

```powershell
.\gradlew.bat help --init-script 探针脚本
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
    if (providers.environmentVariable("RELEASE_TO_CENTRAL").isPresent) {
        publishToMavenCentral(true)   // true = 校验通过后自动发布，无需手动点 Release
        signAllPublications()         // 使用 signingInMemory* 属性签名
    }

    coordinates("io.github.zusrsoft", "markdown-parser", rootProject.property("VERSION").toString())
    pom { /* name/description/url/license/developer/scm 齐全，满足 Central 校验 */ }
}
```

**门控说明**：不带 `RELEASE_TO_CENTRAL` 时，`publishToMavenLocal` 走纯本地发布（不签名）；
带 `RELEASE_TO_CENTRAL=1` 时本地预演也会启用签名，可提前验证签名配置。

### 4.3 版本号

- 版本唯一来源：根 `gradle.properties` 的 `VERSION`（当前 `1.5.2`，基于 release-1.5.2 tag 的 compileSdk 36 重编版）
- ⚠️ Central **不允许覆盖发布**：同命名空间下已发布的版本号永久占用（校验失败的部署不占用，可重试）
- 发布前用 https://central.sonatype.com/search?q=io.github.zusrsoft 检查版本占用

---

## 5. 本地发布操作步骤

### 第 1 步：确认版本号可用

到 https://central.sonatype.com/search?q=io.github.zusrsoft 检查目标版本号是否已发布过。

### 第 2 步（可选但推荐）：本地预演（含签名）

```powershell
$env:RELEASE_TO_CENTRAL = "1"
.\gradlew.bat publishToMavenLocal --no-configuration-cache
```

产物落到 `%GRADLE_USER_HOME%\m2`，可提前发现签名/POM 问题，不上传。

### 第 3 步：正式发布

```powershell
$env:RELEASE_TO_CENTRAL = "1"
.\gradlew.bat publishAllPublicationsToMavenCentralRepository --no-configuration-cache
```

- 首次全量编译约 4~6 分钟（全平台）
- 日志出现 `Validating deployment <uuid>...` 表示上传完成、进入 Sonatype 校验
- 自动发布模式下：校验通过 → 直接 `BUILD SUCCESSFUL`；校验失败 → 构建失败并打印全部错误明细

### 第 4 步：确认结果

见第 7 节。

---

## 6. 常见校验错误对照表（踩坑实录）

按排查顺序排列。

### 6.1 `Cannot perform signing task ... because it has no configured signatory`

**原因**：Gradle 根本没读到签名属性。

1. 凭证写错文件位置（`~/.gradle` vs 真实 `GRADLE_USER_HOME`）——最常见，见 3.1
2. 属性名拼写错误（大小写敏感）
3. Gradle 守护进程缓存旧属性（`.\gradlew.bat --stop` 后重试）

### 6.2 `Could not read PGP secret key`

**原因**：私钥内容或 keyId 无法解析。

1. keyId 是 40 位指纹或 16 位长 ID → 改为 8 位短 ID（见 3.4）
2. 私钥单行转义丢了头部空行 → 补回（见 3.3）
3. 私钥内容残缺（长度异常、头尾标记不完整）

### 6.3 `The key ID must be in a valid form (eg 00B5050F)`

keyId 给了 16 位长 ID。**必须 8 位短 ID**。加 `--stacktrace` 能看到真实原因。

> 技巧：签名失败先跑 `.\gradlew.bat markdown-parser:signJvmPublication --stacktrace` 单独定位。

### 6.4 `Could not find a public key by the key fingerprint`

**原因**：签名 GPG 公钥没上传 keyserver，或 Sonatype 缓存未刷新。

1. 确认公钥已上传：`https://keyserver.ubuntu.com/pks/lookup?search=0x<指纹>&op=vindex&fingerprint=on`
2. keys.openpgp.org 需完成邮箱验证
3. 都确认后就是缓存延迟——**等 10~60 分钟重试**，期间重试只会重复失败

### 6.5 命名空间被拒（namespace not verified / 403）

**原因**：groupId 与 Portal 已验证命名空间不一致。

- 本仓库 2026-09 与 latex 仓库同款问题：坐标 `io.github.huarangmeng`（原作者）vs Portal 命名空间 `io.github.zusrsoft`
- 解决：3 个 build.gradle.kts 的 `coordinates()` + POM url/scm/developer 切换为 zusrsoft；README badge 与依赖示例同步
- ⚠️ 切换命名空间对老用户是破坏性变更，需在 README 中提示迁移
- 依赖坐标：`gradle/libs.versions.toml` 中 latex / codehighlight / diagram 依赖已于 2026-09-06 一并切换至 `io.github.zusrsoft`（⚠️ diagram 1.0.4、codehighlight 1.1.2 需先发布到新命名空间，否则构建解析失败）

### 6.6 版本已存在

同版本号发布第二次会被拒。校验失败的部署（FAILED 状态）不占用版本号，可直接重试。

---

## 7. 发布结果确认

### 7.1 Portal 页面

https://central.sonatype.com/publishing/deployments 查看部署列表：

| 状态                 | 含义                                             |
| -------------------- | ------------------------------------------------ |
| PENDING / VALIDATING | 校验中                                           |
| VALIDATED            | 通过（自动发布模式下瞬间转 PUBLISHED）           |
| PUBLISHED            | ✅ 发布成功，永久生效                             |
| FAILED               | 校验失败，点击查看明细；不占用版本号，修复后重试 |

### 7.2 API 查询（脚本化确认）

```powershell
$pair = "<令牌用户名>" + [char]58 + "<令牌密码>"
$auth = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
Invoke-RestMethod -Uri "https://central.sonatype.com/api/v1/publisher/deployments" -Headers @{Authorization=$auth}
```

### 7.3 实际可下载验证（有同步延迟）

- Portal 显示 PUBLISHED 后，`repo1.maven.org` 同步需要几分钟~30 分钟，期间 404 属正常
- 验证地址：`https://repo1.maven.org/maven2/io/github/zusrsoft/markdown-parser/<版本>/`
- 搜索索引（central.sonatype.com/search）更新更慢，约 1~2 小时

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
- 写错位置的凭证副本（如 `~/.gradle\gradle.properties` 中的凭证段落）用完即删——本仓库 2026-09 发布前已清理过一次
- 日志/文档中引用凭证时只写属性名和长度，不回显内容
- 公钥、密钥 ID、指纹是公开信息，可以写进文档和 issue
- 令牌泄露应立即到 Portal 重新生成（Account → Generate User Token 会作废旧令牌）

---

## 9. 附录：GPG 命令速查

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

---

*最后更新：2026-09-06（基于 latex 1.5.4 发布实战手册定制，Markdown 首次发布 1.5.2）*
