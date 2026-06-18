package com.example.velocitybridge.listener;

import com.example.velocitybridge.VelocityBridgePlugin;
import com.example.velocitybridge.config.PluginConfig;
import com.example.velocitybridge.discord.DiscordManager;
import com.example.velocitybridge.translation.RomajiConverter;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
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
     * 1.19.1+ 署名付きチャット対応。
     *
     * Velocity は署名付きメッセージに対して denied() も message() も拒否し、
     * プレイヤーを切断する。使えるのは allowed() だけ。
     *
     * 【動作】
     * ・イベント結果は変更しない（allowed() のまま）
     * ・元メッセージはバックエンドにそのまま届く（送信元サーバーのプレイヤーに表示）
     * ・独自フォーマットは他サーバーのプレイヤーにのみブロードキャスト
     * ・Discord には常に送信
     *
     * 【送信元サーバーの表示をカスタムフォーマットにしたい場合】
     * FreedomChat を Velocity に導入すると署名が除去され、
     * denied() が安全に使えるようになります。
     * → その場合は config.yml で chat.use-denied: true に変更してください。
     */
    @Subscribe
    public EventTask onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String raw    = event.getMessage();

        // FreedomChat 等で署名除去済みなら denied() を使う（重複なし）
        if (config.isUseDenied()) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
        }
        // それ以外は allowed() のまま（署名付き対応）

        boolean dollarSkip = raw.startsWith("$");
        String original    = dollarSkip ? raw.substring(1) : raw;
        boolean doConvert  = config.isRomajiConversionEnabled()
            && !dollarSkip
            && !containsFullWidth(original);
        String serverName  = player.getCurrentServer()
            .map(s -> s.getServerInfo().getName())
            .orElse("Lobby");

        // 送信元サーバー（allowed 時に重複を避けるため記録）
        RegisteredServer senderServer = player.getCurrentServer()
            .map(ServerConnection::getServer)
            .orElse(null);

        return EventTask.async(() -> {
            String converted = doConvert ? romajiConverter.convert(original) : null;
            boolean actuallyConverted = converted != null && !converted.equals(original);

            String displayMessage = actuallyConverted
                ? config.getRomajiDisplayFormat()
                    .replace("{converted}", converted)
                    .replace("{original}",  original)
                : original;

            Component formatted = miniMessage.deserialize(
                config.getChatFormat(),
                TagResolver.builder()
                    .resolver(Placeholder.unparsed("player",  player.getUsername()))
                    .resolver(Placeholder.unparsed("server",  serverName))
                    .resolver(Placeholder.unparsed("message", displayMessage))
                    .build()
            );

            if (config.isUseDenied()) {
                // denied() 使用時: 全プレイヤーに送信（元メッセージはキャンセル済み）
                server.getAllPlayers().forEach(p -> p.sendMessage(formatted));
            } else {
                // allowed() 使用時: 送信元サーバー以外にのみ送信（重複回避）
                for (Player p : server.getAllPlayers()) {
                    boolean onSameServer = senderServer != null
                        && p.getCurrentServer()
                            .map(conn -> conn.getServer().equals(senderServer))
                            .orElse(false);
                    if (!onSameServer) {
                        p.sendMessage(formatted);
                    }
                }
            }

            // Discord へ送信
            discordManager.sendChatMessage(serverName, player.getUsername(), displayMessage);
        });
    }

    /**
     * 全角文字が 1 文字でも含まれているか判定する。
     */
    private boolean containsFullWidth(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u3000' && c <= '\u9FFF')
             || (c >= '\uF900' && c <= '\uFAFF')
             || (c >= '\uFF01' && c <= '\uFF60')
             || (c >= '\u3400' && c <= '\u4DBF'))
            {
                return true;
            }
        }
        return false;
    }
}
