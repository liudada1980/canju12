# 🤝 参与贡献

感谢你对「一二残局」的关注！欢迎各种形式的贡献。

---

## 📜 行为准则

- 友善、尊重、包容
- 专注技术讨论，避免人身攻击
- 尊重不同观点和经验水平

---

## 🐛 报告 Bug

1. 在 [Issues](../../issues) 中搜索是否已有相同问题
2. 如果没有，点击 **New Issue** → 选择 **Bug Report**
3. 请包含以下信息：
   - **设备信息**：手机型号、Android 版本
   - **APP 版本**：设置中查看或在标题栏长按查看
   - **复现步骤**：一步步描述如何触发 Bug
   - **预期行为**：你期望发生什么
   - **实际行为**：实际发生了什么
   - **截图/录屏**：如果可能
   - **日志**：如果有 `adb logcat` 输出更佳

---

## 💡 功能建议

1. 在 [Issues](../../issues) 中搜索是否已有相同建议
2. 点击 **New Issue** → 选择 **Feature Request**
3. 描述：
   - **需求场景**：你遇到什么问题？
   - **建议方案**：你希望怎样解决？
   - **替代方案**：你考虑过的其他方案

---

## 🔧 代码贡献

### 环境准备

1. 安装 [Android Studio](https://developer.android.com/studio)（需 JDK 21+）
2. Fork 本仓库
3. 克隆你 Fork 的仓库：
   ```bash
   git clone https://github.com/liudada1980/canju12.git
   cd 12canju
   ```
4. 添加上游仓库：
   ```bash
   git remote add upstream https://github.com/liudada1980/canju12.git
   ```
5. 用 Android Studio 打开项目，等待 Gradle 同步

### 开发流程

1. **创建分支**：

   ```bash
   git checkout -b feature/你的功能名
   # 或
   git checkout -b fix/你的修复名
   ```
2. **编写代码**：

   - 遵循现有代码风格
   - 新增的 `.java` 文件必须添加 GPL 许可证头：
     ```java
     /*
      * 一二残局 - 中国象棋残局刷题软件
      * Copyright (C) 2026 刘霸天-长沙
      *
      * 本程序是自由软件：你可以再分发之和/或依照由自由软件基金会发布的
      * GNU 通用公共许可证修改之，无论是版本 3 许可证，还是（按你的决定）任何以后版都可以。
      *
      * 发布该程序是希望它能有用，但是并无保障;甚至连可销售和符合某个特定的目的都不保证。
      * 请参看 GNU 通用公共许可证，了解详情。
      *
      * 你应该随程序获得一份 GNU 通用公共许可证的复本。如果没有，请看 <https://www.gnu.org/licenses/>。
      */
     ```
   - 修改已有文件时保留原有的 GPL 许可证头
3. **测试**：

   - 确保编译通过：`./gradlew assembleDebug`
   - 在真机或模拟器上测试功能
   - 检查是否影响已有功能
4. **提交**：

   ```bash
   git add .
   git commit -m "简要描述你的改动"
   ```

   提交信息规范：

   - `feat: 添加 XXX 功能`
   - `fix: 修复 XXX 问题`
   - `refactor: 重构 XXX`
   - `docs: 更新文档`
   - `style: 代码格式调整`
   - `chore: 构建或辅助工具变动`
5. **推送并创建 PR**：

   ```bash
   git push origin feature/你的功能名
   ```

   然后在 GitHub 上创建 Pull Request。

### 代码风格

本项目使用 Java，遵循以下约定：

- 缩进：4 空格
- 字符编码：UTF-8
- 行宽：尽量不超过 120 字符
- 命名：
  - 类名：`UpperCamelCase`（如 `ChessView`）
  - 方法名/变量名：`lowerCamelCase`（如 `loadCurrentQuestion`）
  - 常量：`UPPER_SNAKE_CASE`（如 `UNLOCK_TIME_SECONDS`）
- 注释：中文注释即可，与现有代码保持一致
- 中文用户界面文字使用中文，代码标识符使用英文

### PR 审查

- 每个 PR 至少需要一位维护者审查
- 审查要点：
  - 功能是否正确
  - 代码风格是否一致
  - 是否有 GPL 许可证头
  - 是否影响已有功能
- 审查通过后合并

---

## 📚 贡献题库

你可以为项目贡献新的题库文件：

1. 准备一个 `.txt` 文件，每行一个 FEN
2. 如果需要执黑棋守和，请将进攻方调整为红方，在FEN前面添加2#两个字符
3. 在文件开头添加注释说明题库来源
4. 通过 PR 提交到 `app/src/main/assets/NeizhiTiku/` 目录

示例：

```
# 三步杀练习
# 难度：初级
# 来源：XX比赛真题
3ak4/4a1P2/9/9/9/9/9/9/9/4K4 w - - 0 1
9/2P6/4k4/9/9/9/9/9/9/5K3 w - - 0 1
```

---

## 🎨 贡献素材

你可以贡献棋子、棋盘、背景等视觉素材：

1. 图片格式：PNG
2. 棋子尺寸：正方形，建议 128×128 或 256×256 像素
3. 棋盘比例：10:9 或 678:750（天天象棋比例）
4. 文件命名遵循项目约定（见 README.md "自定义外观" 部分）
5. 通过 PR 提交到 `app/src/main/res/drawable/` 目录

---

## 🌐 翻译

如果你想帮助翻译 APP 界面：

1. 复制 `app/src/main/res/values/strings.xml`
2. 创建对应语言的目录，如 `values-en/`（英语）、`values-ja/`（日语）
3. 翻译字符串内容
4. 通过 PR 提交

---

## 📋 许可证

所有贡献的代码将按照 [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0) 许可证发布。提交 PR 即表示你同意你的贡献在 GPL-3.0 下授权。

---

> 🔥🔥刘霸天为您加油！🔥🔥🔥
