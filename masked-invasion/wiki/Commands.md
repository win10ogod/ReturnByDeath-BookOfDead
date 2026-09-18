# 全部指令與權限

[首頁](README.md) · [指令](Commands.md) · [遊戲規則](Game-Rules.md) · [常用範例](Recipes.md) · [型態池](Form-Pool.md) · [疑難排解](Troubleshooting.md)

## 指令速查

目前只有以下 **4 個子指令**。`player`、`wave` 是語法佔位符，實際輸入時換成玩家名稱與正整數，不要輸入括號。

| 指令 | 權限 | 執行位置 | 用途 |
|---|---|---|---|
| `/masked_invasion status` | 一般玩家 | 玩家聊天欄 | 查詢自己的進度或入侵狀態 |
| `/masked_invasion start` | 等級 2+ | 玩家聊天欄 | 以自己為領隊開始下一波 |
| `/masked_invasion start <player>` | 等級 2+ | 玩家／主控台 | 以指定在線玩家為領隊開始下一波 |
| `/masked_invasion start <player> <wave>` | 等級 2+ | 玩家／主控台 | 指定領隊與波次 |
| `/masked_invasion stop` | 等級 2+ | 玩家／主控台 | 取消全伺服器所有目前入侵 |
| `/masked_invasion reload` | 等級 2+ | 玩家／主控台 | 重新讀取型態池 JSON |

單人世界需允許指令，專服需取得足夠 OP 權限。專服主控台使用玩家名稱，例如 `masked_invasion start Rider 8`；`status` 與不帶玩家的 `start` 需要玩家執行身分。

## status：查看自己的狀態

```mcfunction
/masked_invasion status
```

沒有正在進行的入侵時，顯示已完成波次與距下次入侵的約略分鐘數；分鐘數向上取整，睡眠跳時會縮短等待。尚未開始建立進度的新玩家會先顯示 0 波與預設間隔。

正在入侵時，顯示波次、尚餘入侵者數與狀態：

| 狀態 | 意義 |
|---|---|
| `PREPARING` | 正在準備據點區塊、確認重生點及落腳位置 |
| `WARNING` | 開戰前預告 |
| `ACTIVE` | 防守進行中 |
| `FAILED` | 已判定失敗，等待完成死亡回歸流程 |

「尚餘」包含已排定但尚未生成的入侵者。所有防守者離線時會暫停該場入侵；暫停是內部旗標，不會另顯示 `PAUSED` 狀態。

沒有 `/masked_invasion status <player>`。管理員從主控台查詢指定玩家時，可以使用原版執行身分指令：

```mcfunction
/execute as Rider run masked_invasion status
```

## start：手動開戰

```mcfunction
/masked_invasion start
/masked_invasion start Rider
/masked_invasion start @s 4
/masked_invasion start Rider 8
```

- 不指定波次時，以這次共同防守玩家中**最高已完成波次 + 1**安排。
- 指定波次時，`wave` 範圍為 **1～2147483647**；不會自動先完成前面的波次。
- 指定波次必須先填玩家：測試第 8 波應使用 `start @s 8`，不是 `start 8`。
- `player` 只接受單一在線玩家；玩家名稱或可解析成單一玩家的選擇器皆可。`@a` 不適合作為多人群組參數。
- 同維度、重生點足夠接近的生存／冒險玩家會自動加入共同防守；指令中的玩家不是唯一防守者。
- 領隊已有入侵、附近已有重疊戰區、沒有完成的回歸點、沒有在線存活持有者，或領隊為創造／旁觀模式，都不能正常開始。
- 指令仍使用 `miWarningTicks` 預告與目前的遊戲規則。先確認 `miEnabled` 為 `true`；關閉時手動建立的入侵也會被取消。

這是真正的入侵，不是獨立模擬。勝利會將每位防守者的已完成波次更新為「原進度與這場波次的較大值」，發放獎勵並重新排程。因此，已有第 8 波進度的人測試第 1 波，不會被降回第 1 波；成功測試第 8 波則會影響後續難度。

## stop：取消所有入侵

```mcfunction
/masked_invasion stop
```

影響全伺服器目前所有據點的入侵：取消事件、移除已載入的入侵者並解除封鎖，相關防守者依目前間隔重新排程。已卸載的入侵者在再次載入、檢查不到原入侵事件後也會清除。

這是取消，不算勝利、不推進已完成波次、不發放勝利獎勵，也不會因此觸發失敗回歸。已經開始的死亡回歸不會被此指令倒轉或撤銷。

沒有 `stop <player>`，也沒有 `reset`、`setwave` 或 `skip` 子指令；`stop` 不會重設歷史波次。

## reload：重新載入型態池

編輯**房主／伺服器遊戲目錄**下的 `config/masked_invasion-forms.json` 後執行：

```mcfunction
/masked_invasion reload
```

成功時顯示載入型態數；JSON、物品或相容性檢查失敗時，顯示錯誤並保留原本已載入的設定。

`reload` 不會重新載入模組 JAR，也不會更改遊戲規則或重排已建立的波次。不能刪除目前入侵事件仍引用的型態 ID；需要大幅更換型態池時，先結束入侵，再修改及重新載入。詳見[型態池](Form-Pool.md)。

## gamerule：查值與修改

`/gamerule` 是原版指令，假面入侵把可調整項目註冊為 `mi` 開頭的規則。需要等級 2+ 權限，規則名稱區分大小寫。

```mcfunction
/gamerule miIntervalDays
/gamerule miIntervalDays 5
/gamerule miRiderSkills false
```

第一行查目前值，後兩行修改值。這些是世界共用設定，不是個別玩家設定；完整列表與生效時機見[遊戲規則](Game-Rules.md)。
