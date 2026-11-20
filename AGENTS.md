# Mei 项目协作说明

## 项目结构

- 这是 Kotlin / Jetpack Compose Android 应用，主模块为 `app`；Gradle 配置使用 Kotlin DSL，依赖版本集中在 `gradle/libs.versions.toml`。
- 页面和可复用组件在 `app/src/main/java/com/ljyh/mei/ui/`；网络接口在 `data/network/` 与 `data/network/api/`；播放服务在 `playback/`；Room 数据库及迁移在 `di/AppDatabase.kt`。
- 图片 URL 的尺寸处理统一放在 `app/src/main/java/com/ljyh/mei/utils/image/ImageUrls.kt`。添加同类逻辑前先查已有工具方法。
- `tools/reverse-engineering/` 和外部 `api-enhanced` 是协议研究参考；应用实现以 `app/` 内代码为准。不要把抓包中的 Cookie、令牌或设备标识写入源码、测试或文档。

## 架构文档入口

- 开始涉及应用结构、状态归属、导航或跨设备布局的任务前，先读 [整体架构与修改入口](docs/architecture.md)，再按其中的索引阅读专题与实际源码。
- 歌单拆分与恢复机制见 [歌单架构](docs/playlist-detail-architecture.md)，手机／平板装配见 [平板架构](docs/tablet-ui.md)。这些文档描述渐进重构现状，不代表所有旧页面已经迁移；具体行为以当前分支源码为准。
- 修改公共边界、状态所有者或布局选择规则时，同步更新相应文档；总览保留职责与入口，具体行为放在专题中。

## 修改约定

- 开始编辑前检查当前分支和工作区状态；保留与当前任务无关的修改，不自行清理、重置或提交。
- UI 图片优先使用项目现有的 Coil 组件和缓存。列表或封面墙应让图片逐张呈现；避免为了显示首张图而等待全部请求完成。
- 调整 EAPI / XEAPI 接口时，逐个核对请求路径、参数、加密方式和响应结构；不要根据路径前缀推断所有接口都可用同一种协议替换。
- 改动 Room 数据库版本或表结构时，检查已发布版本到新版本的迁移路径，避免用户升级后启动崩溃或丢失数据。
- 播放相关的歌词、封面等预加载由播放服务触发，结果进入共享数据源；页面负责消费状态。

## 图片加载

- 页面、播放服务和后台任务共用应用级 Coil ImageLoader 及缓存，不另建加载器。普通 UI 直接向现有 Coil 组件传入图片 URL，由 `ArtworkRequestInterceptor` 根据组件解析后的实际像素尺寸（包含屏幕密度）自动选择缩略图，不在各页面拼接 CDN 参数或自行定义请求规格。
- 图片规格统一由 `ImageUrls.kt` 中的 `ArtworkSize` 定义，目前为 192、512、1024px；按已知长边向上归档，未知尺寸默认 512px，超过最大档也不自动请求原图。这里规范的是图片请求规格，不是组件布局的 dp 尺寸；新增规格应在公共入口统一评估。
- 普通页面使用缩略图。两种全屏播放器统一使用 `rememberPlayerArtworkRequest(url, originalCover)` 遵循原图设置：关闭时固定请求 1600px，开启时请求原图，显式绕过普通页面自动分档，不受 1024px 上限影响。全屏图片预览使用 `rememberOriginalArtworkRequest(url)`。这些高清请求优先使用已有内存或磁盘缓存占位，不为占位额外下载小图；等待高清图期间显示占位，高清图失败时保留占位。保存图片使用 `ImageRequest.Builder.originalArtwork(url)` 显式请求原图。
- 取色、背景采样、通知栏和预加载等没有组件布局尺寸的请求，使用 `ImageRequest.size` 指定处理尺寸，再由中间层统一归档；下一首封面只预加载小图，由播放服务触发并进入共享缓存。
- 网易云缩略图参数统一归一化为 WebP、质量 80 和公共尺寸档位，替换重复或冲突的旧尺寸参数，保留其他查询参数及动态图片能力。本地路径、content URI 和其他 CDN 不套用网易云参数；其他来源的服务端缩略须单独适配。
- 带有 `|`（含 `%7C`）处理链或 `watermark` 操作的网易云图片 URL 保留原样，不平铺替换其中的 `type`、`thumbnail` 等参数，不额外追加缩图；这类链接沿用接口提供的尺寸，原图模式也保留其水印处理链。
- 不使用缺少尺寸区分的自定义缓存键，避免缩略图覆盖原图或不同规格互相污染。列表和封面墙仍逐张呈现，不等待整批图片完成。实现细节见 `docs/image-loading.md`。

## UI 一致性

- 玻璃材质仅用于顶栏、顶栏内的图标按钮及底部悬浮栏（tab 导航和收起的 mini player）。底部悬浮栏使用统一的玻璃参数和实色回退。sheet、menu、dialog、搜索框等其他界面不使用玻璃材质。此范围优先于 `docs/ui-chrome-alignment.md` 中与之冲突的旧描述。顶栏背景可由页面指定自动、透明或实色策略，开启降低透明度时顶栏始终使用实色。
- 顶栏中与返回按钮同级的悬浮图标操作统一复用同一种玻璃按钮容器，保持尺寸、圆角、透明度与按压反馈一致；图标本身使用清晰实色。搜索框或其他已有容器内部的操作沿用所在容器，不重复叠加玻璃；关闭玻璃时使用统一的普通按钮回退。实现细节见 `docs/ui-chrome-alignment.md`。
- 页面内容与底部悬浮 bar 共用稳定的左右边距（手机 20dp，平板 28dp），大标题、分区标题、首张卡片与列表封面对齐同一内容基准线；带封面的文字沿各自内容列对齐。居中顶栏标题、歌单等既有居中详情头部（封面、标题、信息与操作组）及播放器保留用途所需的居中布局；横向列表首尾用内部 contentPadding 对齐，正文不随底栏收缩或变形移动。使用公共边距定义并统一处理系统安全区，避免重复 padding。实现细节见 `docs/ui-chrome-alignment.md`。

## 验证与交付

- 小型 UI 修改先运行 `./gradlew :app:compileDebugKotlin`；数据、协议或播放行为变化再运行相关的定向测试。
- 按当前协作约定，不主动安装或更新 APK，也不进行视觉测试；需要时由用户另行提出。
- 汇报实际修改、验证结果和仍未确认的行为。编译通过不等于已在设备上验证视觉效果。

## 本 fork 的定制（v2.2.0 起）

- **播客**：`ui/screen/podcast/`（列表、详情、`PodcastViewModel`）；网络层 `WeApiService.getSubscribedRadios/getRadioPrograms`；模型 `data/model/podcast/PodcastModels.kt`；映射 `data/model/domain/PodcastMapper.kt`。播客节目的可播 id 是 `mainSong.id`，`program.id` 只能用于节目页和分享链接。
- **播客播放保护**：`playback/source/PodcastTrackRegistry.kt` 记录播客歌曲 id（**SharedPreferences 持久化**，`AppContext.onCreate` 调 `install`）；`MediaUriProvider` 对命中项固定 `standard` 音质并跳过 LX／解灰兜底（否则会白等 15 秒）。普通歌曲在目标音质取不到地址时，会再降级试一次 `standard`。
- **应用内更新**：`data/repository/UpdateRepository.kt` + `ui/screen/update/`，入口在 设置 → 检查更新；安装包下载到 `filesDir/updates`，经 `FileProvider`（authority `${applicationId}.fileprovider`）交给系统安装器；需要 `REQUEST_INSTALL_PACKAGES` 权限。Release 资源名由 `tools/ci/prepare-apks.py` 生成，格式 `Mei_<tag>_<abi>.apk`，更新逻辑按 ABI 匹配。
- **歌词页控制栏**：`PreferenceKeys.LyricsControlsAutoHideKey`（默认 false）控制是否 3 秒自动收起；点击歌词区域不再切换控制栏，只有上下滑动歌词才切换显隐。
- **发布**：`versionName` 与 tag 保持一致（当前 2.3.5 / versionCode 10）。推 `v*` 标签触发 `.github/workflows/main.yml` 出包，应用内更新读同一个 Releases 数据。

## v2.3.0 起：播客体验与播放记忆

- **播客列表**：`PodcastsScreen` 用 `MeiCollectionPage(artworkColumns = null)` 渲染单列行，标题两行；顶部 `PodcastSearchField` 过滤已关注电台。电台内同样有搜索框，按节目名本地过滤。
- **排序**：`PodcastSort.OLDEST/NEWEST/NUMBER_ASC/NUMBER_DESC`，默认 `OLDEST`，选择存 `PodcastSortKey`。集数排序走 `compareNatural`（连续数字按整数比较，`042` < `132`）。
- **全量加载**：`PodcastViewModel.reload` 以 `asc = true` 连续拉完所有页（`PAGE_SIZE=50`，`MAX_PAGES=60`），排序与搜索只在本地做，因此不再出现早期集数翻不到。
- **播放记忆**：Room 表 `playback_progress`（`PlaybackProgress`，DB 19→20）。`MusicService` 每秒采样、每 10 秒落库，切歌与 `onDestroy` 补写；`onMediaItemTransition`（排除单曲循环）调用 `resumeFromProgress` 自动续播。阈值在 `PlaybackProgressPolicy`：短于 15 秒不记，距结尾 20 秒内视为听完并删除。
  - 清除入口：电台页右上角「清除本电台播放记忆」、播放设置「清除全部播放记忆」（`PlaybackMemoryViewModel`）。
- **字号**：`PlayerTitleFontSizeKey`（0 = 跟随主题）与 `PodcastTextSizeKey`（默认 15），在外观设置里按 px 直选。`Title.kt` 依据 `queueSource.type == PODCAST` 选播客字号，并给标题与副标题加 `basicMarquee`。
- **时间跳转**：`PlayerControlsSection.PodcastSeekRow` 仅在播客曲目显示 -30s/-5s/+5s/+30s，原有上一首/播放/下一首不变。
- **音乐库排序**：`LibrarySection` + `LibrarySectionOrderKey`，手机端五个区块由 `mobileLibraryContent` 按顺序分派到 `mobileXxxSection`；顶栏 `SwapVert` 打开 `LibraryLayoutDialog`（上下箭头）。平板布局暂未接入该顺序。
- **搜索**：`SearchType.Podcast`（type=1009），`SearchResponseParser` 解析 `result.djRadios`，结果行点击进入 `Screen.PodcastDetail`。接口可用性未在本机验证。

## v2.3.1 起：更新下载改造

国内直连 GitHub Release 资产（`objects.githubusercontent.com` / Fastly）通常只有 15~30 KB/s，22MB 要十几分钟，官方 API 还有未登录 60 次/小时的额度限制。`UpdateRepository` 相应拆成几层：

- **多源下载**：`MIRRORS` 内置 4 个公共加速反代（`ghfast.top`、`ghproxy.net`、`gh-proxy.com`、`github.moeyy.xyz`），`rankSources` 并发 `Range: bytes=0-49151` 探测实际吞吐后降序排列，官方直连永远最后兜底。这些是社区服务，失效是常态，只改 `MIRRORS` 列表即可增删。
- **断点续传**：下载写 `mei-<tag>.apk.part`，带 `Range: bytes=<已有长度>-`；只有长度校验通过才 `renameTo` 正式文件名。原实现开头就删旧包，慢网下每次重试都从 0 开始，这是「越下越久」的根因。
- **慢源切换**：每 `SPEED_WINDOW_MS`(8s) 统计一次，低于 `MIN_SPEED_BPS`(12 KB/s) 且还有备用源就换下一个，已下字节保留。
- **查询限流**：`latestRelease` 结果写 `filesDir/update-cache.json`，10 分钟内走缓存；「重新检查」传 `force = true` 但仍有 30 秒最小间隔。403/429 读 `X-RateLimit-Reset`，提示变成「额度已用完，HH:mm 后可重试」；查询失败且有缓存时退回缓存。
- **UI**：`DownloadProgress`（percent / 速度 / 当前源 / 已下字节 / 是否续传）经 `UpdateUiState.download` 到界面，显示「1.2 MB / 22.6 MB · 接着上次继续 · 来源 ghfast.top」。
- 首次从 v2.3.0 升级时用的还是旧下载逻辑，加速只在装到 2.3.1 之后生效。

## v2.3.2 起：播客播放界面与队列修复

- **封面遮挡标题**：`AppleMusicPlayer` 里暂停时封面 0.85、播放时放大到 1.0（约 +17.6%），`graphicsLayer` 以中心缩放，视觉下沿会多出 `(expandedVisualSize-expandedSize)/2 ≈ 20~30dp`，正好压住下方 `Title` 第一行。`normalPathBounds` 改为「底部固定、向上生长」：`top = expandedTop + expandedSize - expandedVisualSize`，并用 `topSafeArea` 兜底防止顶进状态栏。小屏走 `isCompactHeight`，标题本来就不显示。
- **队列标题截断**：`QueueTrackRow` 对 `metadata.isPodcast` 用 `maxLines = 2`，其余保持 1 行。评书类标题（「王玥波雍正剑侠图第7部 132」）末尾集数不再被省略号吃掉。
- **队列里播客项空白**（只有一个时长）：根因是 `PlaybackQueueSnapshot` 只存 `currentSong`，其余靠 `createPlaceholder`，再由 `PlaybackQueueManager.loadSongDetails` 走 `/song/detail` 补全；播客的 `mainSong.id` 在那里只返回时长，标题和封面是空的，于是替换成了空行。现在快照新增 `podcasts: List<PodcastQueueSong>?`（仅播客队列，约 25KB/161 集），恢复时优先用它 `toMediaMetadata().toMediaItem()` 重建，同时会 `PodcastTrackRegistry.mark`，不会白等 LX 兜底。
- **防御**：`loadMetadataAtIndices` 过滤掉 `title` 为空的返回，不再用残缺数据覆盖占位符；`savePlaybackQueue` 一次取 `player.mediaItems`，并对 `currentIndex` / `currentSong` 做一致性校验，避免 `ids` 与 `entries` 长度不一致导致 decode 校验失败、整份快照被丢弃。
- 旧快照（无 `podcasts` 字段）升级后第一次仍是空行，播放一次播客让新快照写入（10 秒内）即可。

## v2.3.3 起：播放缓存 key 与播客身份修复

用户反馈「播客内容比网易云官方 App 跑得快，进度条满了但还有一大段没播」，以及「-30 秒实际跳了几分钟」。根因不是 MP3 seek 精度，而是**缓存里混了两份文件**：

- **key 记的是请求音质，不是实际音质**：`MediaUriProvider` 原来的 `baseKey = "$mediaId|${quality}|full-v1"`，`quality` 取用户设置（默认 exhigh），而播客只走 `@PODCAST_LEVEL = "standard"`；普通歌曲在目标音质失败时也会降级到 standard。于是同一个 key 下可能先后写入码率不同的两个文件（时长相同、字节数不同），播放器按 A 文件的字节偏移去读 B 文件，时间轴和内容就此错开，误差随位置线性增长。
- **播客身份只在内存里**：`PodcastTrackRegistry` 原先纯内存，冷启动 / 从「最近播放」进 / 通知栏恢复时为空，同一集会被当普通歌曲先请求 exhigh，是上一条的主要触发路径。
- **「整集已缓存」短路从未生效**：`MusicService.createDataSourceFactory` 拼的 key 与实际写入的不一致，等于每次都重新解析地址。

改动：

- 新增 `playback/source/PlaybackCacheKey.kt`：统一 key 形状 `<mediaId>|<实际音质>|full-v2`（LX/解灰再追加 `|lx:<identity>`）。`VERSION` 升到 `full-v2`，因此 v1 条目自动失效；`isCurrent` 只认 v2，`isLegacy` 用于清理。
- `MediaUriProvider`：`requestKey` 只用于 URL 缓存查找；写入播放缓存时改用接口回报的 `data.level`（降级就记 standard），`RequestKey` 与实际内容严格对应。
- `PodcastTrackRegistry` 改为 SharedPreferences 持久化，`AppContext.onCreate` 调 `install(this)`；`AppContext` 另起一个 IO 协程用 `CacheManager.purgeLegacyEntries` 丢掉历史播客的 v1 条目。
- `CacheManager.findFullyCachedKey`：按 `"$mediaId|"` 前缀枚举缓存索引找完整条目，替代原来拼固定 key 的做法（后者永远不命中）。
- `StorageCacheManager.isMusicKey` 复用 `PlaybackCacheKey.isPlaybackKey`，v1/v2 都算音乐缓存，「清理音乐缓存」能一次清干净。
- `MusicService.resumeFromProgress`：播放器还没解析出时长时退回 `MediaMetadata.duration` 兜底；播客距结尾 90 秒内不自动续播（普通歌曲仍是 3 秒），避免「一进来就快没了」。

**升级代价**：v1 缓存不再命中，首次播放会重新下载。旧条目不会被复用，由 LRU 淘汰或用户在 设置 → 缓存 → 音乐缓存 手动清理。

## v2.3.4 起：队列显示与播客续播入口

- **队列标题统一单行**：`PlaylistBottomSheet.QueueTrackRow` 改成 13sp 单行 + `basicMarquee()`，封面 48→44、拖拽手柄 48→40、移除按钮 48→36、行高 64→56、边距收紧，文字区从约 168dp 涨到约 197dp。原实现只有 `metadata.isPodcast == true` 才给两行，占位符行走 else 分支，于是同一列表里两行/一行混杂——这也是用户看到「031 两行、032 一行」的原因。
- **队列元数据补全分流**：`PlaybackQueueManager.loadMetadataAtIndices` 按 `queueSource.type == PODCAST` 把占位符归到电台，走 `/dj/program/byradio`（`loadPodcastEpisodes`，一次取 500 条，按 `mainSong.id` 建索引）而不是 `/song/detail`；后者对播客 id 只返回时长，配上 2.3.2 的空标题过滤，会让那一行永远停在「加载中」。失败电台一小时冷却一次，`release()` 清缓存。
- **快照不再漏存播客项**：`MusicService.snapshotPodcastSongs` 原来要求 `tag` 是 `MediaMetadata`；被 `/song/detail` 结果替换过的条目 tag 是 `SongEntity`，会被整条跳过，下次恢复又退回占位符。现在只要是播客队列的项就存，tag 缺失时用 `MediaMetadata` 的标题/封面/时长兜底，空标题和「加载中...」不存。
- **播客续播记录**：新增 `data/model/domain/RecentPodcast.kt`（`RecentPodcastEpisode` + `RecentPodcastCodec`）与 `PreferenceKeys.RecentPodcastKey`。`MusicService.recordRecentPodcast` 在 `onMediaItemTransition` 写电台/集信息（同一集保留原进度），`updateRecentPodcastProgress` 跟随 10 秒落库更新位置；听普通歌曲不会覆盖。
- **音乐库播客区块**：`LibrarySection.PODCASTS` 由原来的单行入口改为「标题『我关注的播客』+ 通栏卡片」（`LibraryPodcastCard`，样式抄 `LibraryLikedPlaylist`）。有记录时卡片显示电台名、最近一集、`已听 mm:ss / mm:ss`，点卡进电台页、点右下播放键直接续播该集（单集 `ListQueue`，进度由记忆播放恢复）；无记录时显示「还没有收听记录，去挑一部评书」。手机与平板都走这个区块，`LibraryPodcastEntry.kt` 目前没有调用方。
- **电台页定位**：`PodcastDetailScreen` 首次进入时若当前播放的正是该电台的某一集，自动 `scrollToItem(index + 3)`（前三个 item 是封面头、搜索框、排序行），从卡片点进来不用再下滑找集数。

## v2.3.5 起：进度不再丢、听完留痕、队列显示收听状态

- **进度判定改由元数据时长主导**：`MusicService.sampleProgress` 改成 `mediaMetadata.durationMs → MediaMetadata.duration → player.duration` 的取值顺序。ExoPlayer 对 VBR mp3 的 `duration` 常常偏小，原来拿它判「距结尾不足 20 秒」，会把没听完的集数误判成听完，接着 `dao.delete()` 掉记录，下次点开就从 0 开始——这是「进度丢失」的主因。
- **听完不再删行**：`playback_progress` 加 `completed` 列（DB 20 → 21，`MIGRATION_20_21` 默认 0）。`PlaybackProgressRepository.record` 到结尾附近写 `completed = true, positionMs = 0`，重听时被新进度覆盖回 false；`resumePositionOf` 对 `completed` 返回 null 并**不再顺手删行**。删行会让列表和队列分不清「听完了」和「没点过」。
- **短曲不判听完**：`PlaybackProgressPolicy.MIN_END_TRACK_MS = 60_000`；`positionMs > durationMs` 视为时长不可信，既不判听完也不算比例。旧 `shouldKeep` 已移除。
- **落库节奏**：采样仍是每秒，落库 10 秒 → 5 秒（`ticks % 5`），并跳过「暂停时位置没变」的重复写入。界面直接读这张表，间隔太长会看到明显滞后。
- **播客身份判定不再只靠登记表**：`persistProgress` 收 `isPodcast` 参数，由 `queueSource.type == PODCAST || PodcastTrackRegistry.contains(id)` 决定；`PodcastTrackRegistry` 上限 512 → 2048，超限**按登记顺序淘汰最旧 256 条**（原来 `ids.clear()` 全清，清完历史播客会被当成普通歌曲，进度链路当场断掉）。持久化格式改为有序字符串 `ids_ordered`，兼容读旧的 `ids` 集合。
- **播客页进度标注**：`PodcastDetailScreen.PodcastProgramRow` 收 `PlaybackProgress?`，副标题追加「已听 12:30 / 51:08」或「已听完」，下面加 2dp `ListenProgressBar`（听完用次要色、未听完用 primary、没听过的不画）。封面区显示「共 161 集 · 已播 40 集」，`completedCount` 按整台节目算，不受搜索框影响。
- **队列也显示收听状态**：`PlaylistBottomSheet.QueueTrackRow` 对播客行把副标题换成「未播放 · 47:12 / 已听 12:30 / 51:08 / 已听完」，并加同样的 2dp 进度条；普通歌曲行不变。进度来自 `service.progressRepository.observeBySong()`，纯本地无请求；**当前播放那一行用 `player.currentPosition` 每秒刷新**，否则会停在上一次落库的位置。行高仍是 56dp。
- **卡片进度改读本地表**：`LibraryPodcastCard` 新增 `progress: PlaybackProgress?`，优先用表里的位置，偏好里的副本只做兜底——原来的副本依赖 `updateRecentPodcastProgress` 的登记表判断，断链后会永远停在 0:00。
- **卡片播放键排整张节目表**：新增 `PodcastPlaybackViewModel`（`ui/screen/podcast/`），点播放键时后台按 500/页拉完整节目表，用 `ListQueue.startingAt(songId)` 以该集为起点入队，才能连续播上一集/下一集；按钮转圈表示准备中，拉不到才回落到单集并用 Toast 说明。原来直接塞单集 `ListQueue`，点完队列里就只剩一首。
- **电台页加载抗限流**：`PodcastViewModel` 分页 50 → 500（`MAX_PAGES = 3`），161 集一次到位；网络类错误退避重试 2 次（800ms / 2s）；错误文案按码区分（405/429/460/503 → 「查询过于频繁，请等几十秒再试」，301/302 → 登录失效）。命中频控进入 30 秒冷却（`retryInSeconds`），冷却期间点重试不发请求，底部提示倒计时；失败时保留已加载列表，不用整页错误面板顶掉。`PodcastRepository.programs` 的异常文案同步改造。

**已知限制**：升级前听完的集数，记录已被旧代码删除，升级后这些集会显示「未播放」，要等下次听到 15 秒以上才有记录；旧数据无法追溯。小时级节目在列表里仍按「分钟:秒」显示（如 `125:03`），与 `TimeUtils.formatDuration` 的既有约定一致。
