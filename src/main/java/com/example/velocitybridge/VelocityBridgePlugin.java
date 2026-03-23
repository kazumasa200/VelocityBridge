package com.example.velocitybridge;

import com.example.velocitybridge.config.PluginConfig;
import com.example.velocitybridge.discord.DiscordManager;
import com.example.velocitybridge.listener.ChatListener;
import com.example.velocitybridge.listener.ConnectionListener;
import com.example.velocitybridge.listener.ServerSwitchListener;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(
    id          = "velocitybridge",
    name        = "VelocityBridge",
    version     = "1.0.0-SNAPSHOT",
    description = "グローバルチャット・Discord連携・ローマ字変換 Velocity プラグイン",
    authors     = {"YourName"}
)
public class VelocityBridgePlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private PluginConfig config;
    private DiscordManager discordManager;

    @Inject
    public VelocityBridgePlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            saveDefaultConfig();
            config = new PluginConfig(dataDirectory.resolve("config.yml"));
        } catch (IOException e) {
            logger.error("config.yml の読み込みに失敗しました", e);
            return;
        }

        discordManager = new DiscordManager(config, logger);
        discordManager.setProxyServer(server);
        discordManager.connect();

        server.getEventManager().register(this, new ChatListener(server, config, discordManager));
        server.getEventManager().register(this, new ConnectionListener(server, config, discordManager));
        server.getEventManager().register(this, new ServerSwitchListener(server, config, discordManager));

        logger.info("VelocityBridge が有効化されました！");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (discordManager != null) {
            discordManager.disconnect();
        }
        logger.info("VelocityBridge が無効化されました。");
    }

    private void saveDefaultConfig() throws IOException {
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }
        Path configFile = dataDirectory.resolve("config.yml");
        if (!Files.exists(configFile)) {
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile);
                }
            }
        }
    }

    public ProxyServer getServer()           { return server; }
    public Logger getLogger()                { return logger; }
    public PluginConfig getPluginConfig()    { return config; }
    public DiscordManager getDiscordManager(){ return discordManager; }
}
