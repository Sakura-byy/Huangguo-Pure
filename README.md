<div align="center">

# Huangguo Pure

**以「液态玻璃」为设计语言的第三方黄果短剧客户端**

原生 Android · 无广告 · 无推荐算法 · 深 / 浅双主题全适配 ·仅1.9MB安装包

[![Release](https://img.shields.io/github/v/release/Zihium/Huangguo-Pure?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC)](https://github.com/Zihium/Huangguo-Pure/releases)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-green)]()
[![Size](https://img.shields.io/badge/APK-2.0MB-orange)]()

</div>

---

## 简介

Huangguo Pure 是一个第三方黄果短剧聚合播放客户端。界面全部由代码构建，Liquid Glass玻璃控件对身后内容做像素采样，边缘折射、按压形变、拖动时光纹跟手。

## 液态玻璃 Dock

底部 Dock 从 v1.1.0 起基于 Kyant0/AndroidLiquidGlass（Compose）重建。按压时胶囊放大到 78dp，上下各越出 64dp 栏体 7dp，左右对称溢出栏体圆角；胶囊内部能看见被折射掰弯的栏体边框。横向拖动跟手，松手吸附；拖动越快胶囊横向拉得越长（最多到槽位的 30%，高度不变），松手速度归零就缩回原长。光纹角度跟随手指落点，拖动越快折射越深、色散越宽；图标强调色跟着指示器实时位置切换。

胶囊与栏体两端的边距固定 4dp（首槽左缘、末槽右缘一样），放大时向两侧溢出同样的距离，不会出现一端贴边、另一端空一截。

浅色模式：

![Dock 演示 · 浅色](docs/screenshots/dock-light.gif)

深色模式：

![Dock 演示 · 深色](docs/screenshots/dock-dark.gif)

拖动时胶囊跨过相邻图标，边框被折射进胶囊内部，色散沿边缘展开：

| 浅色 · 跨栏拖动 | 浅色 · 单键按压 |
|:---:|:---:|
| ![浅色拖动](docs/screenshots/dock-drag-light-1.jpg) | ![浅色按压](docs/screenshots/dock-drag-light-2.jpg) |

| 深色 · 跨栏拖动 | 深色 · 单键按压 |
|:---:|:---:|
| ![深色拖动](docs/screenshots/dock-drag-dark-1.jpg) | ![深色按压](docs/screenshots/dock-drag-dark-2.jpg) |

实现方式：页面是原生 View（RecyclerView 等），Compose 的 `layerBackdrop` 采不到，所以 Java 侧把玻璃所在矩形从原生视图树截成位图（静止 3 倍降采样、连续采样时 4 倍，采样 30fps，自适应节流倍数 3），Compose 侧画进 `rememberCanvasBackdrop`，再由 backdrop 库做 vibrancy / blur / lens / 色散。触摸、抬手惯性、切页、启动各有一段逐帧采样窗口；按在 Dock 上拖动时暂停采样，把主线程让给 Compose 的跟手动画。

胶囊的位置、拉伸、速度都在 `graphicsLayer` / canvas backdrop 的绘制阶段读状态，不进 composition —— 否则每个 MOVE 事件都会重组整条 Dock 并重新 measure。

## 截图 · 浅色模式

| 首页 | 发现 · 剧集 | 发现 · 专题 |
|:---:|:---:|:---:|
| ![首页](docs/screenshots/light-home.jpg) | ![发现剧集](docs/screenshots/light-discover.jpg) | ![发现专题](docs/screenshots/light-topics.jpg) |

| 发现 · 博客 | 排行榜 | 我的 |
|:---:|:---:|:---:|
| ![发现博客](docs/screenshots/light-blog.jpg) | ![排行榜](docs/screenshots/light-rank.jpg) | ![我的](docs/screenshots/light-mine.jpg) |

| 离线缓存 | 批量多选 | 搜索 |
|:---:|:---:|:---:|
| ![离线缓存](docs/screenshots/light-cache.jpg) | ![批量多选](docs/screenshots/light-batch.jpg) | ![搜索](docs/screenshots/light-search.jpg) |

| 详情页 | 播放器竖屏 | 播放器横屏 |
|:---:|:---:|:---:|
| ![详情页](docs/screenshots/light-detail.jpg) | ![竖屏播放器](docs/screenshots/light-player.jpg) | ![横屏播放器](docs/screenshots/light-player-landscape.jpg) |

| 设置 | 关于 |
|:---:|:---:|
| ![设置](docs/screenshots/light-settings.jpg) | ![关于](docs/screenshots/light-about.jpg) |

## 截图 · 深色模式

深色不是反色。玻璃 tint、描边、文字、chip、进度条各自有一套取值；压在海报 scrim 上的标题与简介保持恒白；Dock 的容器色、指示器着色、图标基础色随主题切换。

| 首页 | 发现 · 剧集 | 发现 · 专题 |
|:---:|:---:|:---:|
| ![深色首页](docs/screenshots/dark-home.jpg) | ![深色发现](docs/screenshots/dark-discover.jpg) | ![深色专题](docs/screenshots/dark-topics.jpg) |

| 发现 · 博客 | 排行榜 | 我的 |
|:---:|:---:|:---:|
| ![深色博客](docs/screenshots/dark-blog.jpg) | ![深色排行榜](docs/screenshots/dark-rank.jpg) | ![深色我的](docs/screenshots/dark-mine.jpg) |

| 离线缓存 | 批量多选 | 搜索 |
|:---:|:---:|:---:|
| ![深色缓存](docs/screenshots/dark-cache.jpg) | ![深色多选](docs/screenshots/dark-batch.jpg) | ![深色搜索](docs/screenshots/dark-search.jpg) |

| 详情页 | 评论区 | 播放器竖屏 |
|:---:|:---:|:---:|
| ![深色详情](docs/screenshots/dark-detail.jpg) | ![深色评论](docs/screenshots/dark-comments.jpg) | ![深色竖屏](docs/screenshots/dark-player.jpg) |

| 播放器横屏 | 设置 | 关于 |
|:---:|:---:|:---:|
| ![深色横屏](docs/screenshots/dark-player-landscape.jpg) | ![深色设置](docs/screenshots/dark-settings.jpg) | ![深色关于](docs/screenshots/dark-about.jpg) |

## 账户与云同步

登录后本地数据推送到账号，换设备也在。

| 数据 | 行为 |
|---|---|
| 收藏 | 本地记录立即生效，登录后双向合并；云端新增的条目落回本地 |
| 观看历史 | 播放进度按集存，进「我的」时从云端刷新 |
| 我的喜欢 | 按「剧 + 集」隔离，与站点 `itemKey(videoId, ep)` 对齐 |
| 评分 | 打分先写本地，再提交云端；进详情页先亮本地星，云端返回后覆盖 |

鉴权用站点 `?token=` 查询参数（`Bearer` 头一律 401）。登录接口 `POST /api/account/loginByPassword`，用户信息 `GET /api/account/me` 带回 counts 与 interactions。

## 详情页评分

大评分块是五颗液态玻璃星，支持按住左右拖动打分，松手提交。星级在玻璃上做折射，没有外围矩形边框。已在简介行上方显示「发布于」时间。

## 离线缓存

缓存不只是存视频，还能断网打开详情。

- 缓存一集时同时落一份详情快照（JSON），断网进详情页直接渲染色快照；没有快照但有缓存记录时，用本地记录拼一份最小的离线详情
- 视频文件按 `Download/Huangguo/<剧名>/第N集.mp4` 归档，每集路径单独记表，播放时直接喂给 Extractor
- 进「我的」时后台核对文件是否还在（`stat` / MediaStore 探活），不占主线程
- 探活补了长度与首字节校验：MediaStore 里文件被外部删掉后记录仍在，`openAssetFileDescriptor` 照样能打开，只看「打开成功」会误判成还在
- 本地文件播不动时（合并出来的 mp4 缺索引、拷贝未完成），4 秒内进度没走出去就清记录、自动改在线播，不把用户卡在黑屏上
- 生成 `onPlayerError` 也会回落在线
- 占用有上限：图片磁盘缓存常规 64MB、低内存机 40MB，边播边缓存 180MB，均为 LRU；每日启动后跑一次维护，把图片缓存收到上限的 80%

## 我的

四个分段：我的收藏 / 观看历史 / 我的喜欢 / 离线缓存，点按钮就地切换，不再各开一个页面；左右滑动也能切。长按任意卡片进多选，可上下滑动连续勾选；「全部删除」是批量栏最右的黄色圆钮，留在安全区内，点开弹液态玻璃确认框。

收藏的剧有更新时，卡片右下角挂「有更新 N 集」角标；收藏星用强调色金黄。

## 评论

- 二级回复：评论下「展开 N 条回复」，回复列表内再次回复会带上被回复者
- 点赞列在右侧，与「发表」同列并放大
- 点任意一条评论即进入回复该条，输入框停在键盘以上可视区的上端，不被输入法盖住
- 长按评论正文复制全文；长按不会吃掉点击，松手仍可进入回复态
- 站内人机号不展示：昵称是「省级行政区 + 网友 / 网页 / 网民 / 网虫」形态的（含数字尾巴）直接跳过，剧集评论、二级回复、博客留言共用同一套解析

## 博客

「发现 → 博客」分「全部 / 黑料吃瓜 / 短剧剧评」三类，左右滑动切换；滑到边界继续滑，接到相邻一级页面。分类页没有 JSON 接口，直接抓站点栏目页 HTML 正则解析。

## 搜索与排行

搜索页下方直接带热播榜 TOP10，点条目进详情，点「去排行页」收起搜索层并切到排行 tab。排行榜封面加圆角遮罩，热播 / 推荐 / 飙升三榜切换。

## 全手势播放器

| 手势 | 功能 |
|---|---|
| 双击左 / 右 1/3 画面 | 后退 / 快进 10 秒（画面提示 + 震动） |
| 双击中间 | 暂停 / 播放 |
| 左右横滑画面 | 拖动进度（低灵敏度，带时间预览） |
| 长按画面 | 2.0x 倍速加速 |
| 长按 + 上滑 | 锁定倍速，下滑恢复 |
| 单击中间 | 显示 / 隐藏控制层 |
| 底栏「画面」按钮 | 自适应原比例 / 铺满全屏 / 强制 16:9 / 强制 4:3 |

- 播放器顶栏 / 底栏是真玻璃：每 300ms 从 `TextureView` 取一帧，裁出面板区域喂给玻璃引擎，玻璃里透出的是正在播的内容
- 竖屏控制条是悬浮玻璃卡片，功能按钮纯图标；横屏顶栏与底栏铺满屏宽，零边距贴边
- 断点续播，逐集记忆；音频焦点变化、插拔耳机自动暂停
- 边播边缓存（`SimpleCache`，300MB LRU），下一集静默预载；切集后删上一集缓存

## 设置中心

- 外观：跟随系统 / 浅色 / 深色，改动即时生效。切主题时先换调色板再重建当前页，整页重新加载；播放器不参与，正在播的视频不重建
- 软件清理：分两级——先按剧名列，点进去按集数列，可单集删也可整剧删；标题下方显示当前缓存路径
- 线路切换：从发布页拉全部可用线路，6 路并发测速，按延迟排序（ms 徽标，<300ms 高亮），点击即切。手动选择只在本次运行内有效，下次启动仍自动连最快的那条
- 检测更新：应用内直接下载 GitHub Release 的 APK 并调起安装，不跳浏览器
- 更新日志：从仓库实时拉取
- 关于：版本、开发者、GitHub、Telegram 群、开源许可、分享给好友（微信 / QQ / 复制分享链接）

## v1.2.1 更新内容


**新增功能**

1. **复制分享链接**：关于页的分享菜单去掉朋友圈，留下微信、QQ，第三个换成「复制分享链接」，复制的是一段带文案的文本，可以直接粘到任何地方
2. **长按复制**：评论正文、二级回复长按复制全文并提示「已复制到剪贴板」。剧情简介可以直接拖选。评论没有用 `setTextIsSelectable`，那个属性会让 TextView 吃掉点击事件，点评论就进不了回复态
3. **人机评论过滤**：昵称是「省级行政区 + 网友 / 网页 / 网民 / 网虫」形态的（包括「广东网友12」这种带数字尾巴的）不展示。判据只有昵称形态，不看正文和 IP
4. **胶囊随速度拉伸**：横向拖动时胶囊朝行进方向拉长，最多到槽位的 30%，高度不变；拖得越快越长，松手速度归零立刻缩回原长
5. **每日缓存维护**：24 小时跑一次，启动后 1.5 秒的空闲窗口里把图片缓存收到上限的 80%，不挡首屏
6. **缓存占用可查**：`ImageLoader` 暴露 `diskUsage()`，设置页与清理弹窗显示的是实际释放量（含图片缓存）
7. **横屏播放器悬浮控件**：顶栏、底栏都改成悬浮圆角卡片，底部工具条改成八等分的大按钮

**改动**

| 项 | 说明 |
|---|---|
| 评论发布 | 三个发评论接口改走 `authPath`，带上站点要求的 `?token=`；未登录先提示登录 |
| 输入框定位 | 详情页、博客页是 edge-to-edge，`adjustResize` 不生效。改为读 IME insets（减掉导航栏），内容区底部按键盘高度垫高，输入框停在键盘以上可视区的上端 |
| 深浅色切换 | 判断从「只有跟随系统才重载」改成「实际明暗 ≠ 当前调色板就重载」，先换调色板再 `recreate`，不再留半帧旧配色 |
| 搜索页 | 安全区垫进页面内部，状态栏区域由极光填满，去掉那条近黑底 |
| Dock 边距 | 槽位从栏体内缩 4dp 开始排，胶囊位置公式原先漏了这 4dp，导致首槽贴边、末槽空 8dp。补上后两端各留 4dp，放大时对称溢出 |
| Dock 跟手 | 拖动期间位置直接写入 `Animatable`（`snapTo`），弹簧只用于抬手吸附；坐标按「按下槽位 + 手指总位移」绝对换算；位移与拉伸移到绘制阶段读状态 |
| Dock 模糊 | 3.5 → 2.0dp |
| 博客列表 | 封面加 12dp 圆角，三个分类页一致 |
| 线路策略 | 启动时清掉本地 `host`，先用上次测速最快的那条，测速出来后无条件切最快 |
| 极光背景 | 改自调度（66ms 一步），窗口不可见时停止；手指在屏上、滚动、惯性期间冻结重绘，相位按活跃时长累计 |
| 播放器计时 | 播放中 500ms 一次，暂停 / 缓冲降到 1s |
| 列表 | `RecyclerView` 加 `setHasFixedSize(true)` 与 `setItemViewCacheSize(6)` |

**缓存自动清理**

- 内存上限按 `largeMemoryClass / 8` 算（夹在 16–48MB），低内存机型 12MB；注册 `ComponentCallbacks2`，`TRIM_MEMORY_RUNNING_LOW` 就丢内存图，`COMPLETE` 再清磁盘
- 磁盘 LRU 只在独立低优先级线程扫，45 秒最多一次；取图路径上不做目录遍历；读命中不回写 `lastModified`
- 写入先落 `.tmp` 再 `rename`，进程被杀不留半张图；启动时后台清残留
- 上限：常规 64MB、低内存 40MB；边播边缓存 300MB → 180MB
- 清理缓存先 `release` 播放器的 `SimpleCache`（它握着 `media_cache` 的文件锁与索引），再清图片缓存，最后删 `cacheDir` / `codeCache`

## v1.2.0 更新内容

**新增**

1. 账号体系：登录 / 注册、资料修改、退出，收藏 / 历史 / 喜欢 / 评分四类数据云同步
2. 详情页大评分块：五颗液态玻璃星，可拖动打分，云端同步
3. 批量下载选集：在选集网格上按住划选，已缓存集显示勾标
4. 离线缓存补全：详情快照、每集文件路径落表、断网可读、本地播放
5. 「我的」四分段就地切换 + 左右滑动切段 + 长按多选 + 批量删除（黄色圆钮，带确认框）
6. 我的喜欢：接站点集级点赞，与云端同步
7. 评论二级回复；点赞列移到右侧；点评论自动跳输入框到画面上半部分
8. 发现 → 博客：全部 / 黑料吃瓜 / 短剧剧评三个分类，可左右滑动
9. 搜索页内嵌热播榜 TOP10 与「去排行页」入口
10. 收藏「有更新 N 集」角标；收藏星改强调色金黄
11. 专题卡片评分遮罩；排行榜封面圆角
12. 应用内下载更新（带 FileProvider 安装）
13. 设置页显示缓存路径；软件清理改两级菜单（剧名 → 集数）
14. 缓存文件检测：进「我的」自动核对，文件被删的条目自动消失
15. 播放缓存前探活；播不动自动回落在线，不再黑屏

**修复**

| 问题 | 原因 |
|---|---|
| 观看历史页闪退 | 卡片方法内部已 `addView`，外层又加了一次 |
| 深浅色切换花屏 | 只 `recreate()` 没换调色板，先 `applyPalette` 再延迟重建 |
| 缓存详情断网打不开 | 没有详情快照，补 `detail` 表与 `offlineDetail` 兜底 |
| 缓存视频离线播不了 | 没记每集文件 URI，补 `cached_file` 表与本地播放分支 |
| 搜索页「去排行页」无效 | 回调没接通搜索层关闭与 tab 切换 |
| Tab 栏偶发不跟手 | 拖动期间背景采样抢主线程，按在 Dock 上时暂停采样 |
| 批量「全部删除」位置与颜色 | 改为右侧固定圆钮、强调色、留安全区 |
| 缓存记录与真实文件不一致 | `fileAlive` 只判断打开成功，补长度与首字节校验 |

## 构建与体积

工程默认开 R8（full mode）+ 资源裁剪 + 单 ABI（arm64-v8a）+ 语言裁剪（zh）。

v1.2.1 相比 v1.2.0：

| 项 | v1.2.0 | v1.2.1 |
|---|---|---|
| APK | 2,091,216 字节 | 2,041,366 字节（−49,850，−2.4%） |
| `classes.dex` | 3,202,532 字节 | 3,182,344 字节 |
| 解压总量 | 3,719,361 字节 | 3,640,097 字节 |
| APK 内文件数 | 362 | 353 |

这一版新增的压缩手段：

- `-assumenosideeffects` 删掉全部 `Log.d / v / i / w / e` 调用，连拼接的字符串一起消失
- 排除 OkHttp 的 `publicsuffixes.gz`：那份公共后缀表只服务 Cookie 域名匹配，本项目不用 Cookie
- 排除 Kotlin `*.kotlin_builtins`：只有 `kotlin-reflect` 会读，本项目没有反射
- `resConfigs` 从 `zh / en` 收到只剩 `zh`

运行时开销的改动：

- 极光背景改自调度（66ms 一步），不再用 `ValueAnimator`；窗口不可见、手指在屏上、列表滚动与惯性期间不重绘，相位按活跃时长累计，恢复时不跳变
- Dock 玻璃采样 30fps，连续采样时降采样 4 倍，自适应节流倍数 3
- 封面加载 64MB 磁盘 LRU（`cacheDir/img`，存解密后的原始字节），同一 URL 并发请求在锁上排队，解码按目标宽度采样、`RGB_565`；读命中不回写时间戳
- 「我的」页缓存探活放在后台单线程（`verifyCacheFilesAsync`），列表先画出来，失效条目几百毫秒后自己消失
- 详情页拿到渲染结果后再后台核对一遍缓存集数，只重画选集网格

## 技术栈

| 项 | 实现 |
|---|---|
| 页面层 | Java（纯代码构建，无 XML 布局） |
| Dock 玻璃栏 | Compose + Kyant0/AndroidLiquidGlass（backdrop 1.0.6 / shapes 1.2.0） |
| 播放器与弹层玻璃 | Java 自研 `LiquidGlassDrawable`（BitmapMesh 折射 + 三通道色散） |
| 播放内核 | Media3 / ExoPlayer 1.4.1（HLS + Progressive） |
| 本地存储 | SQLite（收藏 / 历史 / 点赞 / 缓存 / 评分 / 详情快照，v8 表结构） |
| 图片加载 | 自研 `ImageLoader`（内存 LRU + 磁盘 LRU + 采样解码 + 每日维护） |
| 最低支持 | Android 7.0（API 24）；玻璃折射与色散的完整体验需 Android 12+ |
| 体积 | APK 2,041,366 字节（arm64-v8a） |

第三方依赖只有上面列出的这些：AndroidLiquidGlass（backdrop / shapes）、Media3、OkHttp、AndroidX。图片缓存与清理策略参考了 Glide、Coil 的做法（读命中不写时间戳、独立线程低频 trim、临时文件 rename 后再改名），代码是本项目自己写的，没有引入这两个库。

## 构建

```bash
./gradlew assembleDebug

# Release 签名放本地 gradle.properties（不提交仓库）：
# HG_STORE_FILE=../keystore/your.jks
# HG_STORE_PASS=xxx
# HG_KEY_ALIAS=xxx
# HG_KEY_PASS=xxx
./gradlew assembleRelease
```

## 目录结构

```
app/src/main/java/com/huangguo/pure/
├── MainActivity / HomePage / CategoryPage / RankPage / MinePage   # 一级页面
├── DetailActivity / TopicActivity / TopicPage / PostActivity      # 二级页面
├── MineLibrary / CommentBox / BlogPage / CategoryPage             # 内容组件
├── SearchPage / SettingsActivity / AboutActivity / AccountActivity # 搜索与设置
├── PlayerActivity                                                  # 全手势播放器
├── GlassHeader / GlassSurface / LiquidGlassDrawable / GlassDrawable # 玻璃引擎（Java）
├── DockBackdrop / Backdrop                                         # Dock 原生采样
├── glass/                                                          # Dock Compose 玻璃栏
├── Api / Auth / Sync / Store / Mirror                              # 接口、鉴权、同步、本地库
├── ImageLoader / CryptoImage                                       # 图片加载与图床解密
├── HlsDownloader / MediaSave / CleanHelper                         # 下载与清理
└── Update                                                          # 版本检测与应用内安装
```

## License

[MIT](LICENSE) © 2026 Zihium
