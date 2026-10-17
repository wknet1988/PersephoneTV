## Persephone TV __TAG__

Android TV 直播 / 影视播放器。Jetpack Compose 手写电视端焦点管理，遥控器操作，无触摸依赖。

### 下载

| 文件 | 说明 |
| --- | --- |
| `PersephoneTV-__TAG__-release.apk` | 推荐，直接安装 |
| `PersephoneTV-__TAG__-debug.apk` | 带调试信息，排查问题用 |

### 安装

```bash
adb connect 192.168.x.x:5555     # 电视/盒子需先在设置里打开「网络调试」
adb install -r PersephoneTV-__TAG__-release.apk
```

也可以用 U 盘把 apk 拷到电视上，用文件管理器点开安装（需允许「未知来源」）。

### 说明

- 支持 Android 5.0 (API 21) 及以上，横屏，为遥控器设计
- 不内置任何内容源。首次使用请到「设置」填自己的 M3U / JSON 地址，或到「找源」检索公开索引
- release 包暂用 debug 签名，可直接安装；正式上架需自行配置签名

**完整变更**：https://github.com/wknet1988/PersephoneTV/commits/__TAG__
**使用文档**：https://github.com/wknet1988/PersephoneTV#readme
