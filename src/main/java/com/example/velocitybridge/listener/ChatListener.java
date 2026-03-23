package com.example.velocitybridge.listener;

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

    private final ProxyServer server;
    private final PluginConfig config;
    private final DiscordManager discordManager;
    private final MiniMessage miniMessage;
    private final RomajiConverter romajiConverter;

    public ChatListener(ProxyServer server, PluginConfig config, DiscordManager discordManager) {
        this.server = server;
        this.config = config;
        this.discordManager = discordManager;
        this.miniMessage = MiniMessage.miniMessage();
        this.romajiConverter = new RomajiConverter();
    }

    /**
     * async = true により別スレッドで実行。
     * ローマ字変換の HTTP リクエストがあってもメインスレッドをブロックしない。
     *
     * フォーマット (<message> 1つのみ) の内容:
     *   - 変換が行われた場合 → chat.romaji-display の形式で合成
     *       例: "こんにちは (konnichiha)"
     *   - 変換されなかった場合 → 元テキストをそのまま表示
     *       例: "hello world"
     */
    @Subscribe(async = true)
    public void onPlayerChat(PlayerChatEvent event) {
        // デフォルト動作をキャンセル (バックエンドへの転送を止める)
        event.setResult(PlayerChatEvent.ChatResult.denied());

        Player player = event.getPlayer();
        String raw    = event.getMessage();

        // 先頭の $ はスキップ記号 → 除去したうえで変換しない
        boolean dollarSkip = raw.startsWith("$");
        String original    = dollarSkip ? raw.substring(1) : raw;

        // ローマ字変換を行うか判定
        // ・設定で無効 / $ プレフィックス / 全角文字含む → スキップ
        boolean doConvert = config.isRomajiConversionEnabled()
            && !dollarSkip
            && !containsFullWidth(original);

        String converted = doConvert ? romajiConverter.convert(original) : null;

        // 実際に変換された（元テキストと異なる）場合のみ合成フォーマットを使用
        boolean actuallyConverted = converted != null && !converted.equals(original);

        // <message> に埋め込む最終テキストを決定
        String displayMessage = actuallyConverted
            ? config.getRomajiDisplayFormat()
                .replace("{converted}", converted)
                .replace("{original}",  original)
            : original;

        String serverName = player.getCurrentServer()
            .map(s -> s.getServerInfo().getName())
            .orElse("Lobby");

        // MiniMessage でフォーマット適用
        // Placeholder.unparsed() でプレイヤー入力の MiniMessage タグを無効化 (インジェクション対策)
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
    }

    /**
     * 全角文字が1文字でも含まれているか判定する。
     * 含まれている場合はローマ字変換をスキップする。
     *
     * 対象: ひらがな / カタカナ / 漢字 / 全角英数・記号 / CJK 互換・拡張A など
     */
    private boolean containsFullWidth(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u3000' && c <= '\u9FFF')  // 全角スペース〜CJK統合漢字
             || (c >= '\uF900' && c <= '\uFAFF')  // CJK 互換漢字
             || (c >= '\uFF01' && c <= '\uFF60')  // 全角英数・記号 (！〜｀)
             || (c >= '\u3400' && c <= '\u4DBF')) // CJK 拡張A
            {
                return true;
            }
        }
        return false;
    }
}
