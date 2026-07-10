# 🐟 一二残局

> 中国象棋残局刷题软件 — 内置皮卡鱼引擎对弈、云库查询、深度分析

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://developer.android.com)
[![Min SDK: 24](<https://img.shields.io/badge/Min%20SDK-24-orange.svg>)](https://developer.android.com/about/versions/nougat)

---

## 🎯 是什么

「一二残局」是一款中国象棋残局练习 APP。核心玩法：

- 🎯 给你一个残局局面，你来走棋，**皮卡鱼引擎**扮演对手
- 🏆 你的目标是：**执红将杀对方（或困毙），执黑守和（六十回合规则）**
- ↩️ 走错了可以悔棋、重来，做不出来可以标记"不会"
- ⏭️ 做完自动跳到下一题，进度实时保存

## ✨ 功能特性

### 🧩 刷题

- 内置多个难度题库
- 选关网格：绿色=已完成，蓝色=当前题，灰色=未做
- 做题计时、正确率统计、进度自动保存
- 悔棋、重来、标记"已会"/"不会"

### 🐟 皮卡鱼引擎对弈

- 基于 [Pikafish](https://github.com/official-pikafish/Pikafish) 引擎的 AI 对手
- 自动判断胜负、和棋、长将违规
- 支持执黑方解题（棋盘翻转，`2#` FEN 前缀）

### 🔍 拆解分析页面

- 自由走棋，红黑双方都能操控
- 皮卡鱼无限深度分析，实时显示评分和最佳走法
- [chessdb.cn](https://www.chessdb.cn) 云库查询，自动推荐走法
- 中文棋谱显示，点击跳转到任意步骤

### 错题本

📕 错题本功能说明：

#### 进入方式

🚪 进入方式：

- 🎯 在主界面**双击**回合指示器（左上角的红帅/黑将图标）
- ❓ 点"不会"的题目会自动加入错题本

#### 错题本功能

📋 错题本功能操作：

| 操作                   | 说明                          |
| ---------------------- | ----------------------------- |
| 👆**单击**缩略图 | 回到主界面做这道错题          |
| ✋**长按**缩略图 | 删除这道错题                  |
| 📋**粘贴**       | 从剪贴板导入 FEN 到错题本     |
| 📤**导出**       | 把错题本所有 FEN 复制到剪贴板 |

#### 错题本文件位置

📂 错题本文件位置：

```
/storage/emulated/0/canju12/tiku/错题本.txt
```

✏️ 你可以直接编辑这个文件来批量管理错题。

### 🎨 自定义自定义外观与音效

🎨 APP 支持替换棋子、棋盘、背景和音效，只需把文件放到指定目录即可。

#### 自定义文件目录

📁 自定义文件目录：

```
/storage/emulated/0/canju12/uistyle/
```

#### 可替换的文件

🖼️ 可替换的文件清单：

| 文件名                | 用途     | 建议尺寸  |
| --------------------- | -------- | --------- |
| 🖼️`Board.png`     | 棋盘背景 | 10:9 比例 |
| 🏠`room.png`        | 页面背景 | 全屏      |
| 🔊`move.mp3`        | 走棋音效 | 短促音效  |
| **🟥 红方棋子** |          |           |
| `rr.png`            | 红车     | 正方形    |
| `rn.png`            | 红马     | 正方形    |
| `rc.png`            | 红炮     | 正方形    |
| `rp.png`            | 红兵     | 正方形    |
| `rk.png`            | 红帅     | 正方形    |
| `ra.png`            | 红仕     | 正方形    |
| `rb.png`            | 红相     | 正方形    |
| **⬛ 黑方棋子** |          |           |
| `br.png`            | 黑车     | 正方形    |
| `bn.png`            | 黑马     | 正方形    |
| `bc.png`            | 黑炮     | 正方形    |
| `bp.png`            | 黑卒     | 正方形    |
| `bk.png`            | 黑将     | 正方形    |
| `ba.png`            | 黑士     | 正方形    |
| `bb.png`            | 黑象     | 正方形    |

> 💡 如果自定义目录里没有某个文件，APP 会使用内置默认图片。所以你只需要替换想换的就行。

### 自定义题库

📚 你可以添加自己的题库文件，APP 会自动识别。

#### 题库文件位置

📂 题库文件位置：

```
/storage/emulated/0/canju12/tiku/
```

#### 文件格式

📝 文件格式说明：

- 📄 文件扩展名：`.txt`
- 📋 每行一个 FEN 字符串，代表一个残局局面
- 🔄 如果该局面需要**黑方在下方**（你执黑），在 FEN 前加 `2#` 前缀
- 💬 以 `#` 或 `//` 开头的行是注释，会被忽略
- ⬜ 空行会被忽略

#### 示例文件

✏️ 示例文件：

```
# 我的自定义题库
3ak4/4a4/4b4/9/9/4R4/9/9/9/4K3 w - - 0 1
2#3a1k3/4a4/4b4/9/9/4R4/9/9/9/4K3 w - - 0 1
// 上面带2#的题黑方在下面
```

#### FEN 格式说明

🔤 FEN 是国际通用的棋局记录格式，中国象棋 FEN 示例：

```
3ak4/4a4/4b4/9/9/4R4/9/9/9/4K3 w - - 0 1
```

- `/` 分隔每一行（从上到下，红方在上）
- 🔢 数字表示连续空格数
- 🔤 字母代表棋子：k=将/帅，a=士/仕，b=象/相，n=马，r=车，c=炮，p=兵/卒
- 🔠 小写 = 黑方，大写 = 红方
- 🎯 `w` = 红方先走，`b` = 黑方先走

#### 管理自定义题库

🗂️ 管理自定义题库：

- ✋ 在题库对话框中**长按**自定义题库 → 可删除该 `.txt` 文件
- 🏷️ 文件名会作为题库的显示名称

## 📥 安装

### 从源码编译

1. 安装 [Android Studio](https://developer.android.com/studio)（需要 JDK 21+）
2. 克隆仓库：
   ```bash
   git clone https://github.com/liudada1980/canju12.git
   cd 12canju
   ```
3. 用 Android Studio 打开项目
4. 等待 Gradle 同步完成
5. 连接 Android 设备或启动模拟器（Android 7.0+）
6. 点击 **Run** 或执行：
   ```bash
   ./gradlew assembleDebug
   ```

### 编译产物

编译成功后 APK 位于：

```
app/build/outputs/apk/debug/app-debug.apk
```

### 环境要求

| 项目        | 要求                                    |
| ----------- | --------------------------------------- |
| Android SDK | compileSdk 36, minSdk 24 (Android 7.0+) |
| JDK         | 21+                                     |
| Gradle      | 9.4.1                                   |
| AGP         | 9.2.1                                   |

## 🏗️ 项目结构

```
app/src/main/
├── java/com/canju12/zhuzicanju/
│   ├── MainActivity.java          # 主界面：做题、导航
│   ├── AnalyzeActivity.java       # 拆解分析页面
│   ├── WrongBookActivity.java     # 错题本页面
│   ├── ChessView.java             # 自定义棋盘 View（走棋、渲染、FEN 解析）
│   ├── TikuManager.java           # 题库加载（assets + 外部存储）
│   ├── TikuData.java              # 题库数据模型
│   ├── ProgressManager.java       # 做题进度持久化
│   ├── WrongBookManager.java      # 错题本管理
│   ├── PikafishEngine.java        # 皮卡鱼 UCI 引擎封装
│   ├── ChineseRules.java          # 中国象棋特殊规则（长将/长捉判负）
│   ├── PositionValidator.java     # 局面合法性校验
│   ├── UciToChinese.java          # UCI 走法 → 中文棋谱转换
│   ├── QuestionGridAdapter.java   # 选关网格适配器
│   ├── TikuSelectorAdapter.java   # 题库列表适配器
│   ├── WrongBookAdapter.java      # 错题本适配器
│   └── App.java                   # Application 子类
├── assets/
│   ├── NeizhiTiku/                # 内置题库
│   ├── YincangTiku/               # 隐藏题库
│   └── Engine/                    # 皮卡鱼引擎二进制 + NNUE 权重
└── res/
    ├── layout/                    # 界面布局
    ├── drawable/                  # 默认棋盘/背景/图标
    ├── mipmap-*/                  # 应用图标
    └── values/                    # 字符串、颜色、样式
```

## 📂 数据文件

APP 运行时在外部存储创建以下文件：

| 路径                                                              | 用途                |
| ----------------------------------------------------------------- | ------------------- |
| `/storage/emulated/0/canju12/tiku/*.txt`                        | 自定义题库文件      |
| `/storage/emulated/0/canju12/tiku/错题本.txt`                   | 错题本数据          |
| `/storage/emulated/0/canju12/tiku/progressManager/progress.txt` | 做题进度            |
| `/storage/emulated/0/canju12/uistyle/*.png`                     | 自定义棋子/棋盘图片 |
| `/storage/emulated/0/canju12/uistyle/move.mp3`                  | 自定义走棋音效      |

## 🤝 贡献

欢迎贡献！请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解如何参与。

## 📜 许可证

```
一二残局 - 中国象棋残局刷题软件
Copyright (C) 2026 刘霸天-长沙

本程序是自由软件：你可以再分发之和/或依照由自由软件基金会发布的
GNU 通用公共许可证修改之，无论是版本 3 许可证，还是（按你的决定）任何以后版都可以。

发布该程序是希望它能有用，但是并无保障;甚至连可销售和符合某个特定的目的都不保证。
请参看 GNU 通用公共许可证，了解详情。

你应该随程序获得一份 GNU 通用公共许可证的复本。如果没有，请看 <https://www.gnu.org/licenses/>。
```

本项目基于 [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0) 许可证开源。

## 🙏 致谢

- [Pikafish](https://github.com/official-pikafish/Pikafish) — 开源中国象棋引擎
- [chessdb.cn](https://www.chessdb.cn) — 云端棋局数据库
- 竹子系列软件作者 **Kalion** — 鼓励与启发
- 天天象棋棋谱导出软件作者 **qwert**
- 极简象棋作者 **夕幻**
- 清弈作者 **步武**
- 弈潜作者 **青灯古卷**
- **夏添**大佬 — 海量题库
- **reasonix** 界面 — 实用功能
- DeepSeek、GLM、千问、豆包等大模型 — 开发辅助

## 💬 交流

QQ 群：635808985 · 1003608168 · 94846686

有问题或建议欢迎加群交流！作者其实啥也不懂，需要更新代码的需要先给我讲清楚流程……

---

## 📖 使用说明

### 行棋目标

- 执红：取胜（将杀或困毙）
- 执黑：守和（六十回合规则）

### 自定义题库

安卓文件夹路径：`/storage/emulated/0/canju12/`

软件会自动加载 `canju12/tiku` 文件夹内的 txt 题库，txt 题库的格式为每行一个 FEN。如果你想执后手，可以在 FEN 前面填加 `2#` 这两个字符（特别适合用来练习需要守和的残局，这种情况需要把红棋调整为进攻方）。

### 自定义行棋声音、背景、棋盘、棋子

软件会自动读取 `canju12/uistyle` 文件夹素材文件：

- 行棋声音为 `move.mp3`
- 所有图片均为 PNG 格式
- 棋盘名为 `board`，背景为 `room`
- 红车马炮兵帅仕相分别为 `rr` `rn` `rc` `rp` `rk` `ra` `rb`
- 黑车马炮卒将士象分别为 `br` `bn` `bc` `bp` `bk` `ba` `bb`
- 默认棋盘尺寸为 9:10，也支持天天象棋的 678:750 尺寸的棋盘

### 其他功能

- 双击左下角显示为红帅/黑将的图标进入错题本
- 双击右下角 Readme 进入致谢和使用说明

---

## 📜 开源声明

一二残局 - 中国象棋残局刷题软件
Copyright (C) 2026 刘霸天-长沙

本程序是自由软件：你可以再分发之和/或依照由自由软件基金会发布的 GNU 通用公共许可证修改之，无论是版本 3 许可证，还是（按你的决定）任何以后版都可以。

发布该程序是希望它能有用，但是并无保障;甚至连可销售和符合某个特定的目的都不保证。请参看 GNU 通用公共许可证，了解详情。

你应该随程序获得一份 GNU 通用公共许可证的复本。如果没有，请看 [https://www.gnu.org/licenses/](https://www.gnu.org/licenses/)。

本项目基于 [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0) 许可证开源。

刘霸天于长沙，2026年6月20日

> 🔥🔥刘霸天为您加油！🔥🔥🔥
