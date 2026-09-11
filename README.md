# 死亡回歸與死者之書

Minecraft **1.21.1** · NeoForge **21.1.235** · Java **21**

[下載模組](https://github.com/win10ogod/ReturnByDeath-BookOfDead/releases/latest) · [自動建置](https://github.com/win10ogod/ReturnByDeath-BookOfDead/actions)

死亡後回到共同回歸點，保留跨輪迴記憶。死者之書記錄生前經歷，合資格的讀者按順序追體驗，直至死亡的失聲與黑暗。

## 安裝

將 Release 的 `rbd-0.4.0.jar` 放入遊戲實例的 `mods/`，升級時移除舊版 JAR。首次進入單人世界自動綁定持有者並建立初始回歸點；聊天訊息提供實體書庫座標。Esc 退出閱讀，F8 降低附加刺激。

完整備份需同時保留世界目錄及相應的外部回歸資料（單人預設 `saves/.rbd/<世界>/`）。0.3.0 權能資料會遷移為多人格式，升級後不要直接降版讀取。

## 多人

兩端安裝同版本模組。`config/rbd-common.toml` 修改後重啟：

```toml
maxHolders = 2
autoBindOnJoin = false
```

預設 1 名，0 表示不限制。調低上限不會自動剝奪已有資格。專服主控台使用 `rbd bind PlayerName`、`rbd unbind PlayerName` 或 `rbd unbind_uuid <UUID>`；`rbd status` 查看現況。

任一持有者真正死亡，全服共同回歸；普通玩家死亡不觸發。多人各自保留記憶，同 tick 多人死亡各自封書、合併為一次回歸。原版圖騰和成功取消死亡的模組救命道具優先生效。

專服使用 Python 3.11+ 的 supervisor 直接啟動 Java（先完成 NeoForge 安裝與 EULA 設定）：

```bash
python3 repository/supervisor/supervisor.py \
  --world /path/server/world --control /path/server/rbd-state/world \
  --cwd /path/server run -- java @user_jvm_args.txt \
  @libraries/net/neoforged/neoforge/21.1.235/unix_args.txt nogui
```

Windows 使用 `python` 和對應的 `win_args.txt`；控制目錄放在世界外、同一檔案系統。回歸會停止、替換世界並重啟伺服器，客戶端需重新連線。

## 相容與範圍

已實測騎士工藝 1.1.3、暮色森林 4.8.3345、GeckoLib 4.9.2、Player Animation Library 1.1.4 合裝：3 個客戶端、2 名持有者，包含圖騰、生命護符、跨維度、變身裝備、模組附件與 Boss／進度回復。

回歸涵蓋寫入世界目錄的已保存資料；外部資料庫與跨服服務需要另外整合。上述情境通過不代表所有形態、任務或整合包都已驗收。NPC 視覺目前使用主觀色彩光線投射；模組安裝前或未載入人物的過去沒有補錄。

玩家影像預設每 tick、原始解析度 PNG，歷史不自動刪除。可在配置中明確調整，長期遊玩需保留足夠磁碟空間。

## 建置與發布

在 `repository/` 執行 `bash gradlew --no-daemon build runGameTestServer`，Windows 使用 `gradlew.bat`。JAR 位於 `repository/build/libs/`。Python 測試在 `repository/supervisor/` 執行 `python -m unittest -v test_supervisor`。

GitHub Actions 在 main、PR 和手動觸發時編譯及測試；推送與 `mod_version` 一致的 `v*` 標籤後，自動發布 JAR、原始碼包和 SHA-256。所有測試通過才進入發布工作。

新增程式及資源維持 All Rights Reserved。原始 MDK 模板授權見 [TEMPLATE_LICENSE.txt](repository/TEMPLATE_LICENSE.txt)。第三方模組 JAR 不隨本專案分發。
