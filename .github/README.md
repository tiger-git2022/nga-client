# NGA 客户端(自用)（tiger-git2022/nga-client）

<p align="center">
  <a href="https://github.com/tiger-git2022/nga-client/releases/latest"><img src="https://img.shields.io/github/v/release/tiger-git2022/nga-client?style=for-the-badge&label=%E4%B8%8B%E8%BD%BD%E6%9C%80%E6%96%B0%E7%89%88%20%2F%20Download&color=2ea44f" alt="下载最新版 / Download latest"></a>
</p>

**[⬇ 下载最新 APK / Download the latest APK](https://github.com/tiger-git2022/nga-client/releases/latest)**：`nga-custom-v…apk`（arm64）。

[中文](#中文) | [English](#english) | [上游说明 / Upstream README](../README.md)

## 中文

基于 [Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE)（NGA 客户端开源版）的个人分支，`custom` 分支在上游 `master_release` 的基础上增加：

- **本地阅读进度**
  - 每个帖子记住上次停在第几页、哪个位置，以及最远读到第几楼。数据只存在手机本地（`read_progress.db`），最多 5000 个帖子，超出时删掉最久没看的。
  - 再次打开帖子直接回到上次的位置（不再弹提示条）。
  - 帖子列表（板块、搜索结果、浏览历史）里看过的帖子，回复数后面显示「（+K）」，比如「31（+6）」，K = 上次阅读之后新增的楼数（列表里的回复数 + 1 − 上次阅读时的总楼数），有新楼时变色；没看过的帖子只显示回复数。
  - 帖子页右侧的楼层滚动条：滑动时出现一根细滚动条，停下 0.2 秒后淡出滑走；滑动时按住滑块拖动可在本页内快速跳楼，拖动时旁边的玻璃气泡显示「拖到的楼 / 最后一楼」。滑块位置按楼数算，不会因为图片加载而跳动。
  - 打开指定楼层（pid）、只看某人、搜索回复时不记录，也不跳转。
  - 原版打开帖子总是从第 1 页开始，这里一并修正。
- **网页模式**（帖子无法用原生界面显示时自动切换，或点标题栏的地球图标）
  - 修复网页显示「未登录」：登录在 `ngabbs.com` 完成，cookie 只在那个域名下，而网页打开的是 `bbs.nga.cn`，服务器下发的游客 uid 还会覆盖登录 uid。现在打开网页前会把当前账号的 cookie 写到各个 NGA 域名的父域上。
  - 网页模式也记录阅读进度（按页面里每楼的 `a[name="l楼层"]` 锚点和 `__PAGE`），打开时直接到上次的页码和楼层；从网页返回原生页面时，跳到网页里看到的位置。
- **搜索错误提示**：NGA 搜不到结果、权限不足等情况会返回 4xx 状态码（比如板块内搜不到时是 HTTP 410），原版只显示「HTTP 410 Gone」。现在显示服务器正文里的真实原因，比如「没有符合条件的结果」。搜索结果解析也加了容错（`\xHH` 转义）。
- **搜板块列出全部结果**：原版搜板块直接进入服务器返回的第一个结果（还丢掉了合集的 stid）。现在列出全部结果（按相关度），每项显示是否合集、所属板块和 fid/stid，没有权限的灰掉；点进入，点右边的星星直接收藏/取消收藏。清空搜索框或点结果标题旁的 × 回到搜索历史。
- **收藏板块调整顺序**：「我的收藏」里长按一个板块拖到想要的位置，松手即保存。
- **玻璃效果**（做法同 S1 分支，再按 NGA 调整）
  - 首页、板块帖子列表、帖子页：状态栏和标题栏是一整块玻璃（后面内容的模糊 + 半透明的原顶栏色），标题栏下沿两角是圆角。内容从玻璃下面滑过；标题栏往下读时随内容滑走、往上滑时浮出来盖在内容上。首页的板块分类标签也在这块玻璃里。
  - 悬浮按钮、下拉刷新的转圈按钮、楼层滚动条的气泡也是玻璃。
  - 所有弹出菜单（标题栏三个点菜单、每楼的菜单、首页菜单）是玻璃，24dp 圆角，从标题栏下沿弹出、不盖住标题栏。白天浅色玻璃配深色字，夜间深色玻璃配浅色字。
  - 「设置 → 玻璃效果」可调模糊度（默认 10）和不透明度，白天、夜间分开调（默认都是 40%）。
  - 夜间页面底色是 #303030（和 S1 一致，原来接近纯黑），玻璃染色是 (120,120,126) 的灰。
  - 首页分类标签选中时文字加粗变亮，下面一条短的半透明白线。
  - 帖子页去掉了底部页码栏（「主题页面显示底部标签页」打开时）：左右滑动翻页，「⋮」菜单里有「跳页」。
  - 帖子正文是 WebView，不能直接画进玻璃（会让页面一直重绘、闪烁），玻璃里用的是它被挡住那一小块的半分辨率截图；楼里的图片加载出来后，要下一次滑动玻璃里才更新。
- **应用内更新**：启动时（每天最多一次）检查本仓库的最新 Release；「关于」页点版本号可以手动检查。
- 补上上游漏提交的 `lib_base_ui_compose/.../compose3/theme/Color.kt`，否则编译不过。

`master`、`master_release` 分支与上游保持一致，不在上面开发。

### 下载

<https://github.com/tiger-git2022/nga-client/releases/latest>

包名 `gov.anzong.androidnga.custom`，应用名「NGA客户端(自用)」，可以和官方版（`gov.anzong.androidnga`）同时安装。两者数据不互通，第一次需要重新登录。

### 版本号

和 [mihon 分支](https://github.com/tiger-git2022/mihon) 一样用自己的版本号，不跟上游的版本号：

| | 规则 | 例子 |
|---|---|---|
| tag / versionName | `vX.Y.Z` | `v1.0.3` |
| versionCode | `X × 10000000 + Y × 10000 + Z` | `10000003` |

`.github/fork-version` 里写 `X.Y`：加了新功能（或合入上游的大更新）的 PR 把 Y 加 1，大改动把 X 加 1；Z 是这个 X.Y 的第几次发布，从 0 开始自动递增，修 bug 不用改文件。
上游版本号（根目录 `build.gradle` 的 `appVersionName`）不要改，会写在每个 Release 的说明里，「关于」页也会显示。
第一个版本 `v5.1.1-t1`（versionCode 5011001）是旧规则发的，新规则的 versionCode 从 10000000 起，可以直接覆盖升级。

### GitHub Actions

| 工作流 | 触发 | 作用 |
|---|---|---|
| `tiger-ci.yml` | 合并到 `custom` 的 PR、手动 | 编译 custom 版（debug 签名），上传 APK |
| `tiger-release.yml` | 推送到 `custom`、手动、被同步工作流调用 | 编译签名 APK，发布到 Releases，并上传应用内更新用的 `update.json`。同一个提交只发布一次（手动运行可勾选 force） |
| `tiger-sync-upstream.yml` | 每 6 小时、手动 | 把上游 `master_release` 分支合并进 `custom`；无冲突且编译通过就推送并发布，否则开 Issue |

上游作者不是每个版本都发 GitHub Release（Play 上已是 5.1.x，Release 还停在 5.0.2），所以同步跟的是 `master_release` 分支，而不是 Release tag。
上游原有的 `build.yml`、`gradle-wrapper-validation.yml` 已在 `custom` 中删除，同步时会自动丢弃上游对 `.github/workflows` 的改动。

需要的仓库 Secrets：

- `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`：release 签名（和其他几个仓库共用同一把密钥）。
- `SYNC_TOKEN`（可选）：有 Contents 和 Workflows 写权限的 fine-grained PAT。上游提交改过工作流文件时，GitHub 不允许用 `GITHUB_TOKEN` 推送；没有这个 secret 时，同步会失败并开 Issue 说明。

### 本地编译

需要 JDK 17（用 JDK 21 时 Kotlin 和 Java 的目标版本不一致，会报 `Inconsistent JVM-target compatibility`）。

```sh
# debug 版（包名 gov.anzong.androidnga.debug）
./gradlew :nga_phone_base_3.0:assembleDebug
# 自用版，用自己的签名
./gradlew :nga_phone_base_3.0:assembleCustom -PngaSigningFile=/path/to/signing.properties
```

`signing.properties` 里写 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`；不传 `-PngaSigningFile` 时用 debug 签名。

### 代码位置

改动尽量放在独立文件里，对上游文件的修改集中在下面这些地方（新改动请加 `Fork` 注释）：

- `nga_phone_base_3.0/src/main/java/sp/phone/common/ReadProgressManager.kt`：阅读进度存储
- `.../sp/phone/ui/fragment/ArticleTabFragment.java`、`ArticleListFragment.java`：记录与恢复位置
- `.../sp/phone/ui/adapter/TopicListAdapter.java`：列表里回复数后面的「（+K）」
- `.../sp/phone/view/FloorScrollBar.kt`（新）、`ArticleListFragment.java`：帖子页楼层滚动条
- `.../gov/anzong/androidnga/activity/fragment/ForumWebFragment.kt`：网页模式的登录 cookie 和进度
- `.../sp/phone/mvp/model/convert/ErrorConvertFactory.java`、`TopicConvertFactory.java`：错误提示与解析容错
- `.../sp/phone/common/ForkUpdateChecker.kt`：应用内更新
- `.../gov/anzong/androidnga/activity/compose/ForkBoardSearch.kt`（新）、`SearchViewModel.kt`、`SearchActivity.kt`：板块搜索结果列表
- `.../gov/anzong/androidnga/activity/compose/board/ForkBookmarkReorder.kt`（新）、`ForumBoardView.kt`、`ForumBoardViewModel.kt`：收藏拖动排序
- `.../sp/phone/view/glass/`（新）：玻璃背景（`GlassDrawable`）、弹出菜单玻璃（`GlassPopupDrawable`）、帖子页和板块列表的安装逻辑（`ArticleGlass`）、设置（`GlassSettings`）；接入点在 `ArticleTabFragment.java`、`ArticleListFragment.java`、`TopicListFragment.java`、`SettingsFragment.java`、`FloorScrollBar.kt`
- `.../gov/anzong/androidnga/activity/compose/board/ForkGlassHome.kt`（新）、`NavigationDrawerFragment.kt`、`ForumBoardView.kt`：首页玻璃顶栏
- `res/values/fork_styles.xml`、`values-night/fork_styles.xml`、`styles.xml`（`AppTheme.PopupOverlay` 和应用主题的菜单样式）：所有弹出菜单的玻璃；`res/xml/fork_settings_glass.xml`：设置项；`proguard.cfg`：保留 `GlassPopupDrawable`（XML 里按类名加载）
- `.../sp/phone/view/InnerDividerDecoration.kt`（新）：帖子页只在楼与楼之间画分隔线

## English

A personal fork of [Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE). The `custom` branch adds, on top of upstream `master_release`:

- **Local reading progress**: the page, position and furthest floor of every thread are stored on the device (up to 5000 threads). Opening a thread returns to where you left off, and topic lists show the reply count as "31（+6）" (6 new replies since last read) for threads you have read. A floor scrollbar on the right of the thread page shows the current floor while scrolling and can be dragged to jump within the page. Links to a single post, author-only views and reply search are not tracked. Upstream always opened page 1; that is fixed too.
- **Web view mode**: fixes "未登录" (not logged in) — the login cookie lived only on `ngabbs.com` and a guest uid on the parent domain overrode it. Web view mode also records and restores progress, and the native view jumps to the position reached in the web view.
- **Readable search errors**: NGA answers "no results" and similar with 4xx codes (HTTP 410 for an empty in-board search); the server's message is shown instead of "HTTP 410 Gone". Parsing tolerates `\xHH` escapes.
- **Board search lists every match**: upstream jumped straight into the first result (and dropped the stid of collections). All matches are listed by relevance with their parent board; tap to open, tap the star to bookmark.
- **Reorder bookmarked boards**: long-press a board under "我的收藏" and drag it.
- **Glass**: the status bar and app bar of the home page, topic lists and threads are one pane of glass (a blur of the content behind, tinted with the original bar color, rounded bottom corners); content scrolls under it and the bar slides away while reading and floats back when scrolling up. Floating buttons, the pull-to-refresh circle, the floor bubble and all popup menus (24dp corners, opening below the bar) are glass too. Blur and opacity are adjustable under "设置 → 玻璃效果". The thread page drops its bottom page bar (swipe to change pages, "跳页" in the menu).
- **In-app update check** against this repository's releases (at launch, at most daily; manual from the About page).
- Restores `compose3/theme/Color.kt`, missing upstream.

Package `gov.anzong.androidnga.custom`, installs beside the official app. Versions are the fork's own `vX.Y.Z` (X.Y in `.github/fork-version`, Z counted by the release workflow), versionCode `X × 10000000 + Y × 10000 + Z`. Releases are built by `tiger-release.yml` on every push to `custom`; `tiger-sync-upstream.yml` merges upstream `master_release` every 6 hours and releases when it builds, or opens an issue. Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, optional `SYNC_TOKEN`. Build locally with JDK 17.
