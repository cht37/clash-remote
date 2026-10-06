# 自动构建与发行版

仓库使用 GitHub Actions 构建 Android APK。

- **Android CI**：推送 `main` / `codex/**` 分支或向 `main` 提交 PR 时，运行核心测试、Android 单元测试、Lint，并上传 Debug APK 和测试报告。也可在 Actions 页面手动运行。
- **Android Release**：推送 `v*` 标签时，运行测试、Release Lint，构建并验证正式签名的通用 APK，生成 SHA-256 校验文件，然后发布 GitHub Release。手动运行时，选择分支只构建签名 APK；选择版本标签则发布。

## 签名配置

在仓库 **Settings → Secrets and variables → Actions** 中配置四个 Repository Secrets：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 专用 JKS 密钥库的 Base64 内容，单行 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | 签名别名，当前为 `clash-remote` |
| `ANDROID_KEY_PASSWORD` | 签名私钥密码 |

GitHub 自动提供发布所需的 `GITHUB_TOKEN`，无需另建 PAT。发布工作流仅在签名步骤读取 Secrets；签名文件存放于临时目录，步骤结束即清理。PR 构建使用 Debug 签名。

备份密钥库与密码，并在所有后续发行版中保留同一签名。丢失密钥将无法对已安装的正式版本进行覆盖升级。正式签名与开发 Debug 签名不同，从 Debug APK 切换到正式 APK 时需要先卸载 Debug 版本。

## 发布步骤

1. 修改 `app/build.gradle.kts` 中的 `versionName`（例如 `0.3.0`），同时递增 `versionCode`。
2. 将变更合入 `main`，确认 Android CI 通过。
3. 更新本地 `main` 后创建并推送同名版本标签：

   ```sh
   git switch main
   git pull --ff-only origin main
   git tag v0.3.0
   git push origin v0.3.0
   ```

标签必须为 `v主版本.次版本.修订版本`，并与 `versionName` 一致；本次发布版本为 `0.3.0`，versionCode 为 3。不要对旧版本创建内容不同的同名标签，也不要替换已发布 APK。

构建成功后，发行版包含 `ClashRemote-<版本>-universal.apk` 和 `SHA256SUMS.txt`。工作流先创建草稿、上传附件，再公开发行版，避免更新检查读取到未上传 APK 的正式发行版。失败的草稿可通过 Actions 页面重新运行恢复；已公开发行版不可通过此工作流替换。

## 本地正式构建

配置以下环境变量后运行 `:app:assembleRelease`：

```text
ANDROID_KEYSTORE_PATH=<密钥库绝对路径>
ANDROID_KEYSTORE_PASSWORD=<密钥库密码>
ANDROID_KEY_ALIAS=clash-remote
ANDROID_KEY_PASSWORD=<私钥密码>
```

输出文件为 `app/build/outputs/apk/release/app-release.apk`。未提供签名环境变量时，本地 Release 构建仍可生成未签名 APK；发布工作流会检查签名 Secrets 并验证 APK 签名，缺失时终止发布。

工作流依赖固定到已验证的 Action commit SHA。参考：[GitHub Actions Secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)、[Gradle Setup](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md)。
