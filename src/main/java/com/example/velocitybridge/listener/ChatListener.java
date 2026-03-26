package com.example.velocitybridge.listener;

import com.example.velocitybridge.VelocityBridgePlugin;
import com.example.velocitybridge.config.PluginConfig;
import com.example.velocitybridge.discord.DiscordManager;
import com.example.velocitybridge.translation.RomajiConverter;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class ChatListener {

    private final VelocityBridgePlugin plugin;
    private final ProxyServer server;
    private final PluginConfig config;
    private final DiscordManager discordManager;
    private final MiniMessage miniMessage;
    private final RomajiConverter romajiConverter;

    public ChatListener(VelocityBridgePlugin plugin, ProxyServer server,
                        PluginConfig config, DiscordManager discordManager) {
        this.plugin          = plugin;
        this.server          = server;
        this.config          = config;
        this.discordManager  = discordManager;
        this.miniMessage     = MiniMessage.miniMessage();
        this.romajiConverter = new RomajiConverter();
    }

    /**
     * 1.19.1+ 署名付きチャット対応のため async = true を使わない。
     *
     * 【なぜ async = true が問題か】
     * Minecraft 1.19.1 以降、チャットメッセージはクライアントの秘密鍵で署名される。
     * async = true のハンドラが denied() をセットするまでの間に Velocity が
     * バックエンドへ署名付きパケットを転送してしまい、
     * バックエンド Paper が「署名済みメッセージのキャンセルは不可」と判断して切断する。
     *
     * 【修正方針】
     * denied() だけをイベントスレッド上で同期的にセットし、
     * 重い処理（HTTP 変換・ブロードキャスト）は Velocity スケジューラで非同期実行する。
     *
     * 【サーバー管理者向け追加設定】
     *   velocity.toml       : force-key-authentication = false
     *   server.properties   : enforce-secure-profiles=false  (各バックエンドに設定)
     */
    @Subscribe  // async = true は使わない（1.19.1+ 署名付きチャット対応）
    public void onPlayerChat(PlayerChatEvent event) {
        // ── ① denied() を同期的にセット（最重要） ──────────────────────────
        // この処理が非同期だと 1.19.1+ でプレイヤー切断が発生する
        event.setResult(PlayerChatEvent.ChatResult.denied());

        // ── ② 以降の重い処理はスケジューラで非同期実行 ─────────────────────
        Player player      = event.getPlayer();
        String raw         = event.getMessage();
        boolean dollarSkip = raw.startsWith("$");
        String original    = dollarSkip ? raw.substring(1) : raw;
        boolean doConvert  = config.isRomajiConversionEnabled()
            && !dollarSkip
            && !containsFullWidth(original);
        String serverName  = player.getCurrentServer()
            .map(s -> s.getServerInfo().getName())
            .orElse("Lobby");

        server.getScheduler()
            .buildTask(plugin, () -> {
                // ローマ字変換（HTTP リクエストあり）
                String converted = doConvert ? romajiConverter.convert(original) : null;
                boolean actuallyConverted = converted != null && !converted.equals(original);

                // <message> に埋め込む最終テキストを決定
                //   変換あり → romaji-display の形式 ("こんにちは (konnichiha)")
                //   変換なし → 元テキストのみ          ("hello world")
                String displayMessage = actuallyConverted
                    ? config.getRomajiDisplayFormat()
                        .replace("{converted}", converted)
                        .replace("{original}",  original)
                    : original;

                // MiniMessage でフォーマット適用
                // Placeholder.unparsed() でプレイヤー入力の MiniMessage タグを無効化（インジェクション対策）
                Component formatted = miniMessage.deserialize(
                    config.getChatFormat(),
                    TagResolver.builder()
                        .resolver(Placeholder.unparsed("player",  player.getUsername()))
                        .resolver(Placeholder.unparsed("server",  serverName))
                        .resolver(Placeholder.unparsed("message", displayMessage))
                        .build()
                );

                // Velocity 配下の全プレイヤーにブロードキャスト
                server.getAllPlayers().forEach(p -> p.sendMessage(formatted));

                // Discord へ送信
                discordManager.sendChatMessage(serverName, player.getUsername(), displayMessage);
            })
            .schedule();
    }

    /**
     * 全角文字が 1 文字でも含まれているか判定する。
     * 含まれている場合はローマ字変換をスキップする。
     */
    private boolean containsFullWidth(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u3000' && c <= '\u9FFF')   // 全角スペース〜CJK統合漢字
             || (c >= '\uF900' && c <= '\uFAFF')   // CJK 互換漢字
             || (c >= '\uFF01' && c <= '\uFF60')   // 全角英数・記号
             || (c >= '\u3400' && c <= '\u4DBF'))  // CJK 拡張 A
            {
                return true;
            }
        }
        return false;
    }
}
