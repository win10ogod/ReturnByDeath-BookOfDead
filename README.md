# 死亡回歸與死者之書

Minecraft **1.21.1** · NeoForge **21.1.235** · Java **21**

[下載模組](https://github.com/win10ogod/ReturnByDeath-BookOfDead/releases/latest) · [自動建置](https://github.com/win10ogod/ReturnByDeath-BookOfDead/actions)

死亡後回到共同回歸點，保留跨輪迴記憶。死者之書記錄生前經歷，讀者按順序追體驗，直至死亡的失聲與黑暗。

## 0.6.0：絕望的幻影

持有者的瘴氣預設達到 **10** 後，經過警告便會出現「絕望的幻影」。它複製目標當前的完整物品欄、裝備與 Curios 飾品，保留物品元件；複製品不會掉落。幻影會依玩家成長與觀察到的戰鬥習慣調整追擊，學習記錄保留跨回歸，並可追入其他維度。

生命降到一半進入二階段，回復至 75% 生命並隨機獲得一項加成。預設 **3 秒警告後**，殺戮光環每秒處決半徑 **32 格球形範圍**內除鎖定目標和幻影自身以外的生物，包含隊友、寵物及其他 Boss；圖騰與取消死亡的救命效果無法阻止這項處決。其他持有者被處決時仍可觸發全世界回歸，玩家維持原有連線。擊敗幻影預設降低 5 點瘴氣。

新增 **34 項原生遊戲規則**，可調整生成門檻、警告、光環範圍與間隔、隨機加成、學習速度及裝備使用。已實測騎士原生武器、Curios／Artifacts 飾品及暮色跨維度追擊；要求特定玩家身分或客戶端按鍵的第三方技能仍需個別適配。

## 安裝與回歸

客戶端和伺服器都安裝同版本的 `rbd-0.6.0.jar`，並移除舊版。首次進入單人世界會自動授予死亡回歸並建立初始回歸點；聊天訊息提供書庫座標。Esc 退出閱讀，F8 降低感官刺激。

專服直接使用 NeoForge 原有啟動方式即可。回歸在同一個伺服器程序內完成，保留玩家既有連線；世界暫停、存檔還原後繼續遊玩，不需 supervisor，也不需重新登入。耗時磁碟工作期間仍維持網路心跳。

完整備份需同時保留世界目錄及外部回歸資料：預設位於世界同層的 `.rbd/<世界名稱>/`，例如單人的 `saves/.rbd/<世界名稱>/`。升級後請勿直接以舊版讀取新存檔。

從舊版 supervisor 專服升級時，先正常停止伺服器與 supervisor，再用原始碼包內的工具轉換一次：

```sh
python repository/supervisor/migrate_connected.py --world /path/to/world --control /path/to/old-control
```

工具驗證並複製完整舊檔案至預設位置，保留原本的回歸點、記憶、權能和死亡分支；不覆蓋既有目的地。有未完成交易時需先以舊工具復原。完成後移除舊 supervisor 啟動方式，直接啟動 NeoForge；新版單人世界不需此轉換。

## 遊戲規則

建立世界的「遊戲規則」畫面可設定 **84 項 RBD 規則**。既有世界可從「編輯」→「遊戲規則」修改；多人服也能使用原生 `gamerule` 指令：

```mcfunction
gamerule rbdMaxHolders 2
gamerule rbdAutoBindOnJoin true
gamerule rbdMemoryDeathDwellSeconds 4.5
gamerule rbdCheckpointAdvancements "minecraft:story/enter_the_nether,minecraft:end/kill_dragon"
gamerule rbdPhantomMiasmaThreshold 10
gamerule rbdPhantomAuraRange 32
gamerule rbdPhantomEnabled true
```

涵蓋權能人數、角色與聲音記錄、影像解析度和間隔、感知、瘴氣、告知禁忌、書庫生成、閱讀條件及感官演出。小數與進度清單可直接在原生畫面編輯；清單留空可停用進度回歸點，初次覺醒仍建立起點。

RBD 規則按世界保存、同步至客戶端，設定變更會保留跨回歸。舊 `rbd-common.toml` 提供尚未設定對應世界規則時的初始值。玩家個人的感官強度及 F8 簡化效果仍可調整。

持有者預設 1 名，0 表示人數不限。調低上限只阻止新授權，不會撤銷既有資格。專服主控台使用 `rbd bind PlayerName`、`rbd unbind PlayerName` 或 `rbd unbind_uuid <UUID>`；`rbd status` 查看狀態。

## 多人與模組

任一持有者真正死亡，全服共同回歸；一般玩家死亡不觸發。每名持有者保留各自的記憶，同 tick 多人死亡各自封書、合併為一次世界還原。一般死亡時，原版免死圖騰與成功取消死亡的模組救命道具優先生效；幻影的殺戮光環使用上述處決規則。

已測試騎士工藝 **1.1.3**、暮色森林 **4.8.3345**、GeckoLib **4.9.2**、Player Animation Library **1.1.4** 合裝，包含多人連線、跨維度、變身裝備、模組附件、Boss、容器和進度的回復。詳細涵蓋範圍以原始碼中的實機測試為準，並非所有形態、任務和整合包都已驗收。

0.6.0 另完成兩名實際客戶端的 **41 項整合檢查**，涵蓋騎士武器發射、飾品原生效果、暮色首領處決、跨維度追擊、學習記錄與光環觸發回歸後維持連線。NeoForge GameTests 共 **12 項**，包含裝備元件複製、二階段、處決與範圍豁免、學習及規則保存。

世界目錄內的存檔整體還原。一般維度、實體和玩家使用原生載入與同步流程；模組若另外保留 JVM 快取、外部資料庫或跨服狀態，可透過 `WorldReturnEvent.Before/After` 整合。回歸點與執行中的資料包不一致時會暫停還原，保留連線並記錄原因。

玩家影像預設每 tick、原生解析度 PNG，歷史不自動刪除。0.5.2 使用平行無損編碼與直接 PNG 傳輸，減少第一人稱錄製造成的卡頓；升級時伺服器和客戶端需一併更新。NPC 使用主觀色彩光線投射；安裝前或未載入人物的過去沒有補錄。

## 建置與發布

在 `repository/` 執行 `bash gradlew --no-daemon build runGameTestServer`；Windows 使用 `gradlew.bat`。JAR 位於 `repository/build/libs/`。儲存與相容性測試工具位於 `repository/tools/`，舊版離線 supervisor 工具保留於 `repository/supervisor/`。

GitHub Actions 在 main、PR 和手動觸發時編譯及測試。推送與 `mod_version` 一致的 `v*` 標籤後，自動發布 JAR、原始碼包和 SHA-256；所有必要測試通過才發布。

新增程式及資源維持 All Rights Reserved。原始 MDK 模板授權見 [TEMPLATE_LICENSE.txt](repository/TEMPLATE_LICENSE.txt)。第三方模組 JAR 不隨本專案分發。
