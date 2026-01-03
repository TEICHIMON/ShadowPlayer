# ShadowPlayer - 影子跟读播放器

一款专为语言学习设计的 Android 音频播放器，支持分句播放、重复播放、跟读间隔等功能。

## 功能特性

- ✅ **分句播放** - 根据 LRC 字幕自动分句
- ✅ **变速播放** - 支持 0.5x ~ 2.0x 播放速度
- ✅ **重复播放** - 每句可重复 1~10 次
- ✅ **跟读间隔** - 句子之间可设置 0~10 秒间隔，用于影子跟读
- ✅ **音量键控制** - 音量+ 上一句，音量- 下一句
- ✅ **字幕显示/隐藏** - 可切换字幕显示
- ✅ **点击跳转** - 点击字幕跳转到对应时间
- ✅ **字幕时间微调** - 调整字幕与音频的时间偏移
- ✅ **标签系统** - 支持嵌套标签，灵活分类音频
- ✅ **收藏功能** - 快速收藏常听的音频
- ✅ **书签功能** - 标记难句，方便回顾
- ✅ **播放记录** - 记录播放进度和次数

## 环境要求

- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 21
- Android SDK 35
- 最低支持 Android 8.0 (API 26)

## 如何运行

### 方法一：导入项目

1. 下载并解压项目文件
2. 打开 Android Studio
3. 选择 `File` → `Open` → 选择解压后的 `ShadowPlayer` 文件夹
4. 等待 Gradle 同步完成（首次可能需要几分钟）
5. 连接 Android 手机（需开启 USB 调试）或创建模拟器
6. 点击运行按钮 ▶️

### 方法二：如果 Gradle 同步失败

1. 确保网络能访问 Google Maven 仓库
2. 检查 `gradle/wrapper/gradle-wrapper.properties` 中的 Gradle 版本
3. 尝试 `File` → `Invalidate Caches / Restart`

## 使用说明

### 1. 添加音频文件

1. 打开应用，切换到「文件库」标签
2. 点击「文件夹」标签
3. 点击「添加文件夹」，选择包含音频和 LRC 文件的目录
4. 应用会自动扫描该目录下的音频文件

### 2. LRC 字幕文件

- 字幕文件需要与音频文件**同名**，扩展名为 `.lrc`
- 例如：`lesson01.mp3` 对应 `lesson01.lrc`
- 支持标准 LRC 格式：`[mm:ss.xx]歌词内容`

### 3. 播放控制

- **播放/暂停**：点击中间大按钮
- **上/下一句**：点击左右按钮，或使用音量键
- **跳转**：点击字幕列表中的任意句子
- **进度拖动**：拖动进度条

### 4. 播放设置

- **速度**：点击速度按钮选择 0.5x ~ 2.0x
- **重复次数**：设置每句重复 1~10 次
- **跟读间隔**：设置句子之间的间隔时间

## 项目结构

```
app/src/main/java/com/example/shadowplayer/
├── data/
│   ├── entity/          # 数据实体 (AudioFile, Tag, Bookmark)
│   ├── dao/             # 数据访问对象
│   ├── repository/      # 数据仓库
│   └── AppDatabase.kt   # Room 数据库
├── player/
│   ├── LrcParser.kt     # LRC 解析器
│   ├── AudioPlayer.kt   # ExoPlayer 封装
│   ├── SentencePlayer.kt # 分句播放核心逻辑
│   └── PlaybackSettings.kt # 播放设置
├── ui/
│   ├── player/          # 播放器界面
│   ├── library/         # 文件库界面
│   ├── settings/        # 设置界面
│   ├── navigation/      # 导航
│   └── theme/           # 主题
├── di/                  # Hilt 依赖注入
├── service/             # 后台播放服务
├── MainActivity.kt
└── ShadowPlayerApp.kt
```

## 技术栈

- **UI**: Jetpack Compose + Material 3
- **状态管理**: ViewModel + StateFlow
- **依赖注入**: Hilt
- **数据库**: Room
- **音频播放**: Media3 (ExoPlayer)
- **语言**: Kotlin

## 后续计划

- [ ] 睡眠定时功能
- [ ] AB 复读功能
- [ ] 云同步
- [ ] 更多字幕格式支持 (SRT, VTT)

## License

MIT License
