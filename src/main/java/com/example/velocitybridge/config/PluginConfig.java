package com.example.velocitybridge.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class PluginConfig {

    private final Map<String, Object> root;

    public PluginConfig(Path configPath) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(configPath)) {
            root = yaml.load(in);
        }
    }

    // ---- 内部ユーティリティ ----

    @SuppressWarnings("unchecked")
    private Object get(String key) {
        String[] parts = key.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) return null;
            current = (Map<String, Object>) next;
        }
        return current.get(parts[parts.length - 1]);
    }

    private String getString(String key, String def) {
        Object v = get(key);
        return v != null ? v.toString() : def;
    }

    private boolean getBoolean(String key, boolean def) {
        Object v = get(key);
        if (v == null) return def;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }

    private int getInt(String key, int def) {
        Object v = get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
    }

    // ---- Discord 設定 ----

    public String getDiscordToken()     { return getString("discord.token",      ""); }
    public String getDiscordChannelId() { return getString("discord.channel-id", ""); }
    public String getDiscordChatFormat(){ return getString("discord.formats.chat", "`[{server}]` **{player}**: {message}"); }
    public String getDiscordToMinecraftFormat() {
        return getString("discord.formats.discord-to-minecraft",
            "<aqua>[Discord]</aqua> <white><user></white> <dark_gray>»</dark_gray> <white><message></white>");
    }

    public int getJoinEmbedColor()   { return getInt("discord.embed-colors.join",   5763719);  }
    public int getLeaveEmbedColor()  { return getInt("discord.embed-colors.leave",  15548997); }
    public int getSwitchEmbedColor() { return getInt("discord.embed-colors.switch", 16776960); }

    // ---- チャット設定 ----

    public boolean isRomajiConversionEnabled() {
        return getBoolean("chat.romaji-conversion", false);
    }

    /**
     * ローマ字変換が実際に行われた場合の表示フォーマット。
     * 変換されなかった場合はこのフォーマットは使われない（かっこは表示されない）。
     *
     * 使えるプレースホルダー:
     *   {converted} 日本語変換後テキスト
     *   {original}  元のローマ字テキスト
     */
    public String getRomajiDisplayFormat() {
        return getString("chat.romaji-display", "{converted} ({original})");
    }

    public String getChatFormat() {
        return getString("chat.format",
            "<dark_gray>[<gray><server><dark_gray>]</dark_gray> <white><player></white> <dark_gray>»</dark_gray> <white><message></white>");
    }

    // ---- メッセージ設定 ----

    public String getJoinFormat() {
        return getString("messages.join",
            "<green>▶ <white><player></white> がネットワークに参加しました</green>");
    }

    public String getLeaveFormat() {
        return getString("messages.leave",
            "<red>◀ <white><player></white> がネットワークから退出しました</red>");
    }

    public String getServerSwitchFormat() {
        return getString("messages.switch",
            "<yellow>→ <white><player></white> が <aqua><from></aqua> から <aqua><to></aqua> に移動しました</yellow>");
    }
}
