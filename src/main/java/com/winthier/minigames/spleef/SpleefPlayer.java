package com.winthier.minigames.spleef;

import com.winthier.reward.RewardBuilder;
import java.util.UUID;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

@Data
public final class SpleefPlayer {
    enum Type { PLAYER, SPECTATOR; }
    final Spleef game;
    final UUID uuid;
    String name = "";
    Type type = Type.PLAYER;
    Location spawnLocation;
    boolean ready = false;
    boolean died = false;
    boolean played = false;
    boolean rewarded = false;
    boolean winner = false;
    long offlineTicks = 0;
    int lives = 0;
    int blocksBroken = 0;

    public SpleefPlayer(Spleef game, UUID uuid) {
        this.game = game;
        this.uuid = uuid;
    }

    Location getSpawnLocation() {
        if (spawnLocation == null) {
            spawnLocation = game.dealSpawnLocation();
        }
        return spawnLocation;
    }

    boolean isPlayer() {
        return type == Type.PLAYER;
    }

    boolean isSpectator() {
        return type == Type.SPECTATOR;
    }

    void setPlayer() {
        type = Type.PLAYER;
    }

    void setSpectator() {
        type = Type.SPECTATOR;
    }

    long addOfflineTick() {
        return offlineTicks++;
    }

    void addBlockBroken() {
        ++blocksBroken;
    }

    void reward() {
        if (played && !rewarded && blocksBroken > 50) {
            rewarded = true;
            RewardBuilder reward = RewardBuilder.create().uuid(uuid).name(name);
            reward.comment(String.format("Game of Spleef %s with %d blocks broken and %d lives left.", (winner ? "won" : "played"), blocksBroken, lives));
            ConfigurationSection config = game.getConfigFile("rewards");
            if (winner) reward.config(config.getConfigurationSection("win"));
            for (int i = 0; i < blocksBroken / 10; ++i) reward.config(config.getConfigurationSection("10_blocks_broken"));
            for (int i = 0; i < blocksBroken / 100; ++i) reward.config(config.getConfigurationSection("100_blocks_broken"));
            for (int i = 0; i < lives; ++i) reward.config(config.getConfigurationSection("life"));
            reward.store();
        }
    }
}
