# 假面入侵：指令 Wiki

[首頁](README.md) · [指令](Commands.md) · [遊戲規則](Game-Rules.md) · [常用範例](Recipes.md) · [型態池](Form-Pool.md) · [疑難排解](Troubleshooting.md)

適用 **假面入侵 1.0.0**、Minecraft **1.21.1**、NeoForge **21.1.248**（騎士輪迴生存 **4.1**）。本 Wiki 與模組原始碼一起保存在儲存庫，供玩家及房主查閱。

[下載假面入侵](https://github.com/win10ogod/ReturnByDeath-BookOfDead/releases/tag/masked-invasion-v1.0.0) · [安裝依賴與玩法](../README.md)

## 快速查找

| 想做什麼 | 頁面 |
|---|---|
| 查詢進度、指定玩家或波次開戰、取消入侵 | [全部指令與權限](Commands.md) |
| 調整間隔、戰區、數量、成長、技能或獎勵 | [30 項遊戲規則：預設值、範圍、生效時機](Game-Rules.md) |
| 直接複製測試流程、休閒設定、還原預設 | [常用指令範例](Recipes.md) |
| 更換敵人型態、調整抽選權重及個別倍率 | [型態池與 reload](Form-Pool.md) |
| 指令紅字、一直準備中、失敗後等待持有者 | [疑難排解](Troubleshooting.md) |

## 最常用的指令

```mcfunction
/masked_invasion status
```

一般玩家可查看自己的進度。以下操作需要權限等級 **2 以上**：

```mcfunction
/masked_invasion start @s 1
/masked_invasion stop
/gamerule miIntervalDays 3
```

`start @s 1` 是啟動真正的第 1 波入侵：仍會套用預告、召回、戰區、死亡與世界回歸規則。`stop` 會取消**全伺服器所有目前入侵**，不是只取消自己的那一場。

## 使用前先確認

1. 房主、朋友與伺服器使用相同版本，並備齊模組依賴。
2. 目標玩家在線，處於生存或冒險模式。
3. 世界已有**完成的回歸點**，至少一位死歸持有者在線且存活。
4. 重生點有效，附近有地板與至少兩格可站立空間。

聊天欄輸入時保留 `/`；專服主控台通常省略開頭的 `/`，並以實際玩家名稱取代 `@s`。例如 `masked_invasion start Rider 1`。文中的 `Rider` 只是範例名稱。

時間換算以正常 **20 TPS** 為準：20 tick = 1 秒、1200 tick = 1 分鐘、24000 tick = 1 遊戲日。入侵間隔使用遊戲日時間，睡眠跳時也計入；預告與防守時限按實際伺服器 tick 遞減。

## 核對依據

本 Wiki 依目前發布版的 [指令與入侵流程](../src/main/java/dev/maskedinvasion/Invasions.java)、[遊戲規則](../src/main/java/dev/maskedinvasion/InvasionRules.java)、[波次計算](../src/main/java/dev/maskedinvasion/WavePlan.java)及[型態載入](../src/main/java/dev/maskedinvasion/FormCatalog.java)整理。遊戲規則與進度隨世界保存；世界回歸會還原回歸點中的狀態。
