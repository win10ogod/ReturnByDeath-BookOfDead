# 常用指令範例

[首頁](README.md) · [指令](Commands.md) · [遊戲規則](Game-Rules.md) · [常用範例](Recipes.md) · [型態池](Form-Pool.md) · [疑難排解](Troubleshooting.md)

以下指令在聊天欄**逐行執行**；除查詢自己狀態外，都需要等級 2+ 權限。主控台省略開頭的 `/`，並把 `@s` 換成實際在線玩家名稱。

## 查看自己的進度

```mcfunction
/masked_invasion status
```

## 以目前規則測試第 1 波／第 8 波

先確認有完成的回歸點、在線存活的死歸持有者，且自己為生存／冒險模式。以下是兩個獨立範例，選一個執行；不能對已有入侵的玩家連續啟動兩場。

```mcfunction
/masked_invasion start @s 1
```

```mcfunction
/masked_invasion start @s 8
```

成功防守會更新真實進度；失敗仍走全世界死亡回歸流程。測試後取消目前事件使用 `/masked_invasion stop`，此指令會取消**所有據點**的入侵。

## 將準備期改成 7 個遊戲日

```mcfunction
/gamerule miIntervalDays 7
/gamerule miWarningTicks 1200
/gamerule miDefenseTicks 24000
```

依序為 7 遊戲日間隔、60 秒預告、20 分鐘防守。修改間隔會按新舊間隔差額調整尚在等待的玩家；已開始的預告和防守計時不會改寫。

## 放寬據點範圍

```mcfunction
/gamerule miArenaRadius 80
/gamerule miArenaHeight 40
```

下一場使用水平半徑 80 格、上下各 40 格；目前已建立的戰區維持原尺寸。

如果要允許離開戰區、穿越傳送門，並且取消開戰召回，兩項分別設定：

```mcfunction
/gamerule miConfinePlayers false
/gamerule miRecallAtStart false
```

只關 `miRecallAtStart` 不會取消越界召回；只關 `miConfinePlayers` 不會取消開戰召回。

## 降低後期數量與數值成長

```mcfunction
/gamerule miRaidersPerWave 1
/gamerule miMaxRaiders 16
/gamerule miHealthGrowthPercent 10
/gamerule miDamageGrowthPercent 5
```

新建立的波次每波增加 1 名、上限 16 名；之後生成的入侵者使用每波生命 10%、攻擊 5% 的線性成長。已生成敵人不會被重新配裝或重設屬性。

## 關閉防守時限

```mcfunction
/gamerule miDefenseTicks 0
```

下一場不限時；這不會取消死亡失敗條件，也不會取消正在進行的舊倒數。

## 允許入侵者掉裝備、增加勝利獎勵

```mcfunction
/gamerule miEquipmentDrops true
/gamerule miVictoryEmeralds 6
```

裝備掉落設定會用於之後生成的敵人；勝利結算時，每位防守者獲得 6 顆綠寶石。掉落仍受原模組與其他模組的死亡／掉落處理影響。

## 關閉入侵者主動必殺

```mcfunction
/gamerule miRiderSkills false
```

停止假面入侵主動觸發踢擊／拳擊；腰帶原生效果與普通攻擊仍存在，已發動的動作不會被這個規則撤銷。

## 取消目前入侵，或暫時停用整個玩法

取消現有入侵、繼續自然排程：

```mcfunction
/masked_invasion stop
```

取消現有入侵並停止入侵時鐘：

```mcfunction
/gamerule miEnabled false
```

恢復玩法：

```mcfunction
/gamerule miEnabled true
```

被取消的防守者按取消時的間隔重新排程；沒有參戰的玩家保留原剩餘準備時間。重新啟用不會替所有人清除波次或重新給完整 3 天。

## 還原全部預設規則

下列指令只還原 **30 項規則**，不重設已完成波次、型態 JSON 或正在進行的事件。已建立的戰區／倒數不會被回寫；若需要重新開始事件，可先由管理員使用 `stop` 取消全伺服器入侵。

```mcfunction
/gamerule miEnabled true
/gamerule miIntervalDays 3
/gamerule miWarningTicks 600
/gamerule miDefenseTicks 18000
/gamerule miPlayerDeathFailsRaid true
/gamerule miHoldCheckpointDuringRaid true
/gamerule miArenaRadius 48
/gamerule miArenaHeight 24
/gamerule miRecallAtStart true
/gamerule miConfinePlayers true
/gamerule miBoundaryParticles true
/gamerule miBaseRaiders 4
/gamerule miFirstEnhancedMin 1
/gamerule miFirstEnhancedMax 2
/gamerule miRaidersPerWave 2
/gamerule miRaidersPerExtraPlayer 2
/gamerule miMaxRaiders 32
/gamerule miSuperFormsFromWave 4
/gamerule miFinalFormsFromWave 8
/gamerule miBaseHealth 40
/gamerule miBaseDamage 3
/gamerule miHealthGrowthPercent 15
/gamerule miDamageGrowthPercent 10
/gamerule miSpawnMinDistance 6
/gamerule miSpawnMaxDistance 20
/gamerule miSpawnPerSecond 4
/gamerule miRiderSkills true
/gamerule miSkillIntervalTicks 200
/gamerule miEquipmentDrops false
/gamerule miVictoryEmeralds 3
```
