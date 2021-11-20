package com.winthier.spleef;

import java.util.UUID;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Data
public final class SpleefPlayer {
    protected final SpleefGame game;
    protected final UUID uuid;
    protected String name = "";
    protected Type type = Type.SPECTATOR;
    protected Location spawnLocation;
    protected boolean died = false;
    protected boolean played = false;
    protected boolean winner = false;
    protected int lives = 0;
    protected int blocksBroken = 0;

    public enum Type { PLAYER, SPECTATOR; }

    public SpleefPlayer(final SpleefGame game, final UUID uuid) {
        this.game = game;
        this.uuid = uuid;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public Location getSpawnLocation() {
        if (spawnLocation == null) {
            spawnLocation = game.world.getSpawnLocation();
        }
        return spawnLocation;
    }

    /**
     * isAlive() check.
     */
    public boolean isPlayer() {
        return type == Type.PLAYER;
    }

    public boolean isSpectator() {
        return type == Type.SPECTATOR;
    }

    public void setPlayer() {
        type = Type.PLAYER;
    }

    public void setSpectator() {
        type = Type.SPECTATOR;
    }

    public void addBlockBroken() {
        ++blocksBroken;
    }

    public boolean isOnline() {
        return getPlayer() != null;
    }
}
