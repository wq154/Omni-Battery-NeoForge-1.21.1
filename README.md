# 万能电池 · Omni-Battery (NeoForge 1.21.1)

作者：Ayaka · mod id：`omnibattery` · 版本：1.0.0-neoforge.1.21.1

从 1.20.1 Forge 版移植到 NeoForge 1.21.1，功能与原版 1:1 完整并做了外观与集成扩展。

## 特性

- 5 级电池：低级 / 中级 / 高级 / 精英 / 终极，容量从 10G FE 到 Long.MAX_VALUE
- 立体 3D 电池模型（顶盖 + 主体 + 底盘），金属五色区分
- 玻璃观察窗竖向电量条，随充放电实时变化
- 方块正面实时显示电量百分比：
  - `0%` 黑 · `1-25%` 红 · `26-50%` 黄 · `51-100%` 蓝
  - 充电中：绿色 + ⚡ 闪电符号
- 中文 GUI（220x196）：圆形能量表、−/+ 按钮、模式切换、范围显示开关
- 机器贴纸：模式切换（供电 / 吸电 / 过载 / 清除）
- 贴纸左键空气切换"自动贴标"，放置机器时自动贴当前模式的标签
- Curios 可选集成：贴纸可放入饰品栏 CHARM 槽
- Jade 可选集成：Tooltip 显示真实 long 容量（不再卡 2.14G）

## 编译

需要 JDK 21：

```
gradle\gradle-8.14\bin\gradle.bat build
```

产物：`build/libs/omni-battery-neoforge-1.21.1-1.0.0-neoforge.1.21.1.jar`

## 依赖

- 必需：NeoForge `[21.1,)`、Minecraft 1.21.1
- 可选：Jade `[15.0,)`、Curios `[9.0,)`
