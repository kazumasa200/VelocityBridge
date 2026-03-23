package com.example.velocitybridge.listener;

import com.example.velocitybridge.config.PluginConfig;
import com.example.velocitybridge.discord.DiscordManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class ServerSwitchListener {

    private final ProxyServer server;
    private final PluginConfig config;
    private final DiscordManager discordManager;
    private final MiniMessage miniMessage;

    public ServerSwitchListener(ProxyServer server, PluginConfig config, DiscordManager discordManager) {
        this.server = server;
        this.config = config;
        this.discordManager = discordManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** プレイヤーがバックエンドサーバー間を移動したとき */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String toServer = event.getServer().getServerInfo().getName();

        // getPreviousServer() が empty = 初回接続 → スキップ (参加メッセージは ConnectionListener が担当)
        String fromServer = event.getPreviousServer()
            .map(s -> s.getServerInfo().getName())
            .orElse(null);

        if (fromServer == null) return;

        Component msg = miniMessage.deserialize(
            config.getServerSwitchFormat(),
            TagResolver.builder()
                .resolver(Placeholder.unparsed("player", player.getUsername()))
                .resolver(Placeholder.unparsed("from",   fromServer))
                .resolver(Placeholder.unparsed("to",     toServer))
                .build()
        );

        // 全プレイヤーにブロードキャスト
        server.getAllPlayers().forEach(p -> p.sendMessage(msg));

        // Discord に Embed で通知
        discordManager.sendServerSwitchEmbed(player.getUsername(), fromServer, toServer);
    }
}
