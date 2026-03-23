package com.example.velocitybridge.discord;

import com.example.velocitybridge.config.PluginConfig;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.time.Instant;

public class DiscordManager {

    // Minecraft アバター画像 API (mc-heads.net / crafatar は無料で利用可能)
    private static final String AVATAR_URL = "https://mc-heads.net/avatar/%s/32";

    private final PluginConfig config;
    private final Logger logger;
    private ProxyServer proxyServer;
    private JDA jda;

    public DiscordManager(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    /** Minecraft へのブロードキャスト用に ProxyServer をセットする */
    public void setProxyServer(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    // ---- 接続管理 ----

    public void connect() {
        String token = config.getDiscordToken();
        if (token.isBlank() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            logger.warn("[VelocityBridge] Discord トークンが未設定です。Discord 連携は無効になります。");
            return;
        }
        try {
            jda = JDABuilder.createLight(token,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT)   // メッセージ内容取得に必須 (要 Privileged Intent 有効化)
                .addEventListeners(new DiscordMessageListener())
                .build()
                .awaitReady();
            logger.info("[VelocityBridge] Discord Bot に接続しました。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[VelocityBridge] Discord 接続が中断されました", e);
        } catch (Exception e) {
            logger.error("[VelocityBridge] Discord への接続に失敗しました。トークンを確認してください。", e);
        }
    }

    public void disconnect() {
        if (jda != null) {
            jda.shutdown();
            logger.info("[VelocityBridge] Discord Bot を切断しました。");
        }
    }

    public boolean isConnected() {
        return jda != null;
    }

    // ---- 内部ユーティリティ ----

    private TextChannel getChannel() {
        if (jda == null) return null;
        String id = config.getDiscordChannelId();
        if (id.isBlank() || id.equals("YOUR_CHANNEL_ID_HERE")) return null;
        return jda.getTextChannelById(id);
    }

    private void send(TextChannel channel, net.dv8tion.jda.api.entities.MessageEmbed embed) {
        channel.sendMessageEmbeds(embed).queue(
            null,
            err -> logger.warn("[VelocityBridge] Discord への Embed 送信に失敗: {}", err.getMessage())
        );
    }

    // ---- 送信メソッド ----

    /**
     * チャットメッセージをテキストで Discord に送信する。
     *
     * フォーマットで使えるプレースホルダー:
     *   {server}  バックエンドサーバー名
     *   {player}  プレイヤー名
     *   {message} 表示用メッセージ（変換済みの場合は "日本語 (romaji)" 形式で渡される）
     */
    public void sendChatMessage(String serverName, String playerName, String message) {
        TextChannel channel = getChannel();
        if (channel == null) return;

        String text = config.getDiscordChatFormat()
            .replace("{server}",  serverName)
            .replace("{player}",  playerName)
            .replace("{message}", message);

        channel.sendMessage(text).queue(
            null,
            err -> logger.warn("[VelocityBridge] Discord へのメッセージ送信に失敗: {}", err.getMessage())
        );
    }

    /**
     * プレイヤー参加を Embed で Discord に通知する。
     */
    public void sendJoinEmbed(String playerName) {
        TextChannel channel = getChannel();
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setAuthor(playerName + " がサーバーに参加しました",
                       null,
                       String.format(AVATAR_URL, playerName))
            .setColor(config.getJoinEmbedColor())
            .setTimestamp(Instant.now());

        send(channel, embed.build());
    }

    /**
     * プレイヤー退出を Embed で Discord に通知する。
     */
    public void sendLeaveEmbed(String playerName) {
        TextChannel channel = getChannel();
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setAuthor(playerName + " がサーバーから退出しました",
                       null,
                       String.format(AVATAR_URL, playerName))
            .setColor(config.getLeaveEmbedColor())
            .setTimestamp(Instant.now());

        send(channel, embed.build());
    }

    /**
     * サーバー間移動を Embed で Discord に通知する。
     */
    public void sendServerSwitchEmbed(String playerName, String fromServer, String toServer) {
        TextChannel channel = getChannel();
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setAuthor(playerName + " がサーバーを移動しました",
                       null,
                       String.format(AVATAR_URL, playerName))
            .addField("移動元", fromServer, true)
            .addField("移動先", toServer,   true)
            .setColor(config.getSwitchEmbedColor())
            .setTimestamp(Instant.now());

        send(channel, embed.build());
    }

    // ---- Discord → Minecraft リスナー ----

    private class DiscordMessageListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            // Bot 自身のメッセージは無視 (無限ループ防止)
            if (event.getAuthor().isBot()) return;

            // 設定チャンネル以外は無視
            String channelId = config.getDiscordChannelId();
            if (!event.getChannel().getId().equals(channelId)) return;

            // ProxyServer が未設定の場合は無視
            if (proxyServer == null) return;

            String authorName = event.getAuthor().getName();
            String message    = event.getMessage().getContentDisplay();

            // Minecraft 内にブロードキャスト
            Component formatted = MiniMessage.miniMessage().deserialize(
                config.getDiscordToMinecraftFormat(),
                TagResolver.builder()
                    .resolver(Placeholder.unparsed("user",    authorName))
                    .resolver(Placeholder.unparsed("message", message))
                    .build()
            );

            proxyServer.getAllPlayers().forEach(p -> p.sendMessage(formatted));
        }
    }
}
