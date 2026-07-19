# 🎵 NovaBeat

全功能音乐播放器 Android 应用

## ✨ 功能特性

- 🎨 **Material Design 3 动态主题** - 支持 Material You 动态取色
- 🎵 **Hi-Res 无损音频** - 基于 Media3 ExoPlayer
- 📝 **歌词同步** - 实时歌词滚动显示
- 🖼️ **专辑封面** - 自动获取专辑封面图片
- 🔍 **网易云音乐搜索** - 在线搜索歌曲
- 🎛️ **10段均衡器** - 专业音频调节
- 📊 **频谱可视化** - 实时音频频谱显示
- 🎬 **炫酷开屏动画** - Logo弹跳 + 文字滑入
- 🌙 **深色模式** - 支持系统深色模式
- 📱 **自适应图标** - 支持各种设备形状

## 🛠️ 技术栈

| 组件 | 技术 |
|------|------|
| 播放引擎 | Media3 ExoPlayer 1.2.1 |
| UI框架 | Material Design 3 + DynamicColors |
| 图片加载 | Glide 4.16.0 |
| JSON解析 | Gson 2.11.0 |
| 均衡器 | Android Audio Equalizer |
| 频谱 | Visualizer API |
| 最低版本 | Android 7.0 (API 24) |
| 目标版本 | Android 14 (API 34) |

## 📦 项目结构

```
NovaBeat/
├── app/src/main/kotlin/com/novabeat/music/
│   ├── data/
│   │   ├── model/         # 数据模型
│   │   ├── remote/        # 网络API服务
│   │   └── repository/    # 数据仓库
│   ├── service/           # 播放服务
│   └── ui/
│       ├── MainActivity   # 主界面
│       ├── SplashActivity # 开屏动画
│       └── screens/       # Fragment页面
├── app/src/main/res/
│   ├── layout/            # 布局文件
│   ├── drawable/          # 矢量图标
│   └── mipmap/            # 应用图标
└── gradle/                # Gradle配置
```

## 🚀 构建

```bash
# 克隆仓库
git clone https://github.com/2386866276/NovaBeat.git
cd NovaBeat

# 构建APK
./gradlew assembleRelease

# 安装到设备
adb install app/build/outputs/apk/release/app-release.apk
```

## 📄 开源协议

本项目采用 [GNU General Public License v3.0](LICENSE) 开源协议。

## 🙏 致谢

- [Material Design 3](https://m3.material.io/) - UI设计规范
- [ExoPlayer](https://developer.android.com/guide/topics/media/exoplayer) - 媒体播放引擎
- [Glide](https://bumptech.github.io/glide/) - 图片加载库
- [网易云音乐](https://music.163.com/) - 音乐数据源

---

⭐ 如果这个项目对你有帮助，请给个Star支持一下！
