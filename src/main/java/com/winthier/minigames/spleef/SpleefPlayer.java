package com.winthier.minigames.spleef;

import java.util.UUID;
import lombok.Data;
import org.bukkit.Location;

@Data
public class SpleefPlayer
{
    enum Type { PLAYER, SPECTATOR; }
    final Spleef game;
    final UUID uuid;
    String name = "";
    Type type = Type.PLAYER;
    Location spawnLocation;
    boolean ready = false;
    boolean died = false;
    long offlineTicks = 0;
    int lives = 0;
    
    public SpleefPlayer(Spleef game, UUID uuid)
    {
        this.game = game;
        this.uuid = uuid;
    }

    Location getSpawnLocation()
    {
        if (spawnLocation == null) {
            spawnLocation = game.dealSpawnLocation();
        }
        return spawnLocation;
    }

    boolean isPlayer() { return type == Type.PLAYER; }
    boolean isSpectator() { return type == Type.SPECTATOR; }
    void setPlayer() { type = Type.PLAYER; }
    void setSpectator() { type = Type.SPECTATOR; }
    long addOfflineTick() { return offlineTicks++; }
}
