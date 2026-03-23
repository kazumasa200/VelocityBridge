package com.example.velocitybridge.listener;

import com.example.velocitybridge.config.PluginConfig;
import com.example.velocitybridge.discord.DiscordManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class ConnectionListener {

    private final ProxyServer server;
    private final PluginConfig config;
    private final DiscordManager discordManager;
    private final MiniMessage miniMessage;

    public ConnectionListener(ProxyServer server, PluginConfig config, DiscordManager discordManager) {
        this.server = server;
        this.config = config;
        this.discordManager = discordManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** プレイヤーがプロキシに接続したとき */
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();

        Component msg = miniMessage.deserialize(
            config.getJoinFormat(),
            TagResolver.builder()
                .resolver(Placeholder.unparsed("player", player.getUsername()))
                .build()
        );

        // 全プレイヤーにブロードキャスト
        server.getAllPlayers().forEach(p -> p.sendMessage(msg));

        // Discord に Embed で通知
        discordManager.sendJoinEmbed(player.getUsername());
    }

    /** プレイヤーがプロキシから切断したとき */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        // ログイン完了前の切断は無視 (認証失敗などのノイズを除外)
        if (event.getLoginStatus() != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {
            return;
        }

        Component msg = miniMessage.deserialize(
            config.getLeaveFormat(),
            TagResolver.builder()
                .resolver(Placeholder.unparsed("player", player.getUsername()))
                .build()
        );

        // 切断したプレイヤー本人を除く全プレイヤーにブロードキャスト
        server.getAllPlayers().stream()
            .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
            .forEach(p -> p.sendMessage(msg));

        // Discord に Embed で通知
        discordManager.sendLeaveEmbed(player.getUsername());
    }
}
