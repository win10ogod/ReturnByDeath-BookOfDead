# 型態池與 reload

[首頁](README.md) · [指令](Commands.md) · [遊戲規則](Game-Rules.md) · [常用範例](Recipes.md) · [型態池](Form-Pool.md) · [疑難排解](Troubleshooting.md)

遊戲規則負責整體難度；型態池負責入侵者可抽到哪些腰帶、型態、武器與倍率。

## 檔案位置

房主／專服遊戲目錄：

```text
config/masked_invasion-forms.json
```

首次載入時若檔案不存在，模組會建立預設內容。連線玩家修改自己客戶端的同名檔案，不會改變房主的型態池。

完整預設檔可查閱 [default_forms.json](../src/main/resources/data/masked_invasion/default_forms.json)，1.0.0 內建 49 筆型態。最外層需保留 `"schema": 1` 與 `"forms": [...]`。

## 單筆型態範例

以下是 `forms` 陣列中的**一筆**，不是可直接取代整個檔案的完整設定：

```json
{
  "id": "arcle",
  "tier": "basic",
  "belt": "arcle",
  "forms": [],
  "weight": 2,
  "healthMultiplier": 1.0,
  "damageMultiplier": 1.0
}
```

空 `forms` 會沿用腰帶基本型態。要調整現有條目，直接修改對應 `id` 的那一筆；不要另貼入相同 ID。

| 欄位 | 必填 | 說明 |
|---|---|---|
| `id` | 是 | 此條目的唯一識別字；不能重複 |
| `tier` | 是 | `basic`、`enhanced`、`super`、`final` 之一 |
| `belt` | 是 | 已安裝且存在的騎士腰帶物品，必須是相容的 `RiderDriverItem` |
| `forms` | 是 | 型態物品 ID 陣列；可為空，每個物品必須與該腰帶相容 |
| `weapon` | 否 | 已存在的武器物品 ID；省略為空手 |
| `weight` | 否 | 同階級抽選權重，正整數，預設 `1`；`2` 相對 `1` 有兩倍抽選權重 |
| `healthMultiplier` | 否 | 正有限數；生命倍率，省略時依階級取預設 |
| `damageMultiplier` | 否 | 正有限數；攻擊倍率，省略時依階級取預設 |

未寫命名空間的**物品 ID**，會補成 `kamenridercraft:`；例如 `arcle` 代表 `kamenridercraft:arcle`。其他模組物品需填完整 `modid:item`。條目的 `id` 是型態池識別字，不能直接當成物品 ID 使用。

| 階級 | 名稱 | 省略時生命倍率 | 省略時攻擊倍率 |
|---|---|---:|---:|
| `basic` | 百界士兵 | 1.0 | 1.0 |
| `enhanced` | 百界士兵 | 1.5 | 1.25 |
| `super` | 百界大將 | 2.0 | 1.5 |
| `final` | 百界大將 | 2.5 | 1.75 |

四個階級都至少要有一筆型態，即使設定成很晚才出現超級／最終型態也一樣。型態物品的槽位必須在 1～5 之間，且同一條目不能出現重複槽位。

## 重新載入

1. 在房主／伺服器端編輯並保存 JSON。
2. 執行 `/masked_invasion reload`。
3. 看到「已重新載入 … 種入侵型態」才表示新型態池已套用；看到錯誤時修正檔案後再執行。

重新載入失敗會保留記憶體中的舊型態池，但磁碟上有誤的 JSON 仍需要修正；伺服器下次啟動會再次讀取它。

已建立的入侵事件會保留已抽好的型態 ID，不會因 `reload` 重抽。已生成的入侵者不會自動更換裝備或倍率；尚未生成的同 ID 入侵者會使用新定義。正在被現有事件引用的型態 ID 不能刪除，否則重新載入會被拒絕。

## 個別倍率與波次成長的關係

```text
生成時基礎生命 = miBaseHealth × healthMultiplier × [1 + (波次 − 1) × miHealthGrowthPercent / 100]
生成時基礎攻擊 = miBaseDamage × damageMultiplier × [1 + (波次 − 1) × miDamageGrowthPercent / 100]
```

例如預設第 4 波、生命倍率為 2 的超級型態：`40 × 2 × (1 + 3 × 0.15) = 116` 點基礎生命。這是模組設定的屬性基礎值；實際面板及戰鬥結果還受原模組腰帶效果、武器、其他模組修飾與 Minecraft 屬性範圍影響。
