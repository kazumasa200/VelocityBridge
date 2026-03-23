# VelocityBridge

Velocity プロキシ向けのチャットブリッジプラグインです。

## 機能

| 機能 | 説明 |
|------|------|
| グローバルチャット | Velocity 配下の全サーバーにチャットをブロードキャスト |
| Discord 連携 | チャット・入退室・サーバー移動を Discord に通知 |
| Discord → Minecraft | Discord の指定チャンネルへの投稿を全員にブロードキャスト |
| ローマ字変換 | Google Input Tools API でローマ字入力を日本語に変換 |
| MiniMessage 対応 | 全メッセージのフォーマットを MiniMessage 形式で自由に編集可能 |

---

## 必要環境

- Velocity **3.3.0** 以上
- Java **17** 以上
- （Discord 連携を使う場合）Discord Bot Token

---

## ビルド

```bash
./gradlew build
```

`build/libs/VelocityBridge-1.0.0-SNAPSHOT.jar` が生成されます。

---

## インストール

1. ビルドした JAR を Velocity の `plugins/` フォルダに配置
2. Velocity を起動すると `plugins/velocitybridge/config.yml` が自動生成される
3. `config.yml` を編集して再起動

---

## Discord Bot のセットアップ

### 1. Bot を作成する

1. [Discord Developer Portal](https://discord.com/developers/applications) を開く
2. **New Application** → アプリ名を入力して作成
3. 左メニュー **Bot** → **Reset Token** でトークンを発行してコピーしておく

### 2. Privileged Gateway Intents を有効化する

Discord → Minecraft の受信機能を使う場合は必須です。

Bot ページの **Privileged Gateway Intents** セクションで以下を **ON** にする:

- `SERVER MEMBERS INTENT`（任意）
- **`MESSAGE CONTENT INTENT`**（必須）

### 3. Bot をサーバーに招待する

OAuth2 → URL Generator で以下のスコープと権限を選択して生成した URL にアクセスする:

| スコープ | 権限 |
|----------|------|
| `bot` | `Send Messages` / `Embed Links` / `Read Message History` |

### 4. チャンネル ID を取得する

Discord の **設定 → 詳細設定 → 開発者モード** を ON にし、
対象チャンネルを右クリック → **「IDをコピー」**

---

## config.yml

```yaml
discord:
  token: "YOUR_BOT_TOKEN_HERE"
  channel-id: "YOUR_CHANNEL_ID_HERE"

  formats:
    # Minecraft → Discord チャット ({server} / {player} / {message})
    chat: "`[{server}]` **{player}**: {message}"

    # Discord → Minecraft ブロードキャスト (MiniMessage 形式 / <user> / <message>)
    discord-to-minecraft: "<aqua>[Discord]</aqua> <white><user></white> <dark_gray>»</dark_gray> <white><message></white>"

  embed-colors:
    join:   5763719   # 緑
    leave:  15548997  # 赤
    switch: 16776960  # 黄

chat:
  romaji-conversion: false

  # 変換が行われた時だけ使われるフォーマット ({converted} / {original})
  # 変換されなかった場合はかっこごと非表示になる
  romaji-display: "{converted} ({original})"

  # Minecraft 内チャット (MiniMessage 形式 / <player> / <server> / <message>)
  format: "<dark_gray>[<gray><server><dark_gray>]</dark_gray> <white><player></white> <dark_gray>»</dark_gray> <white><message></white>"

messages:
  join:   "<green>▶ <white><player></white> がネットワークに参加しました</green>"
  leave:  "<red>◀ <white><player></white> がネットワークから退出しました</red>"
  switch: "<yellow>→ <white><player></white> が <aqua><from></aqua> から <aqua><to></aqua> に移動しました</yellow>"
```

---

## ローマ字変換

`chat.romaji-conversion: true` にすると有効になります。

変換には [Google Input Tools API](https://inputtools.google.com/) を使用します（非公式・無料・APIキー不要）。

### 変換がスキップされる条件

| 条件 | 例 |
|------|----|
| メッセージの先頭に `$` がある | `$konnichiha` → `konnichiha`（そのまま送信） |
| ひらがな・カタカナ・漢字・全角文字が含まれる | `こんにちは` / `Hello！` |

### 表示フォーマット

`romaji-display` で変換結果の表示形式を変更できます。

```yaml
# デフォルト: "こんにちは (konnichiha)"
romaji-display: "{converted} ({original})"

# 変換後のみ表示: "こんにちは"
romaji-display: "{converted}"

# 前後を逆にする: "konnichiha → こんにちは"
romaji-display: "{original} → {converted}"
```

変換されなかった場合は `romaji-display` は**使われず**、元テキストのみ表示されます（かっこも出ません）。

---

## Discord の通知一覧

| イベント | 形式 | 内容 |
|----------|------|------|
| チャット | テキスト | `[server] **player**: message` |
| 参加 | Embed（緑） | プレイヤーがネットワークに参加 |
| 退出 | Embed（赤） | プレイヤーがネットワークから退出 |
| サーバー移動 | Embed（黄） | 移動元・移動先をフィールドで表示 |

Embed にはプレイヤーのスキン顔アイコン（[mc-heads.net](https://mc-heads.net)）が自動で付与されます。

---

## MiniMessage リファレンス

フォーマットには [MiniMessage](https://docs.advntr.dev/minimessage/format.html) タグが使えます。

```
<red>赤</red>
<bold>太字</bold>
<#FF5500>カスタムカラー</#FF5500>
<gradient:red:blue>グラデーション</gradient>
<hover:show_text:'ホバー表示'>テキスト</hover>
```

プレイヤーが入力したメッセージ内の MiniMessage タグは**無効化**されます（インジェクション対策）。

---

## ライセンス

MIT
