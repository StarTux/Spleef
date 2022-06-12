package com.winthier.spleef;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Save {
    protected boolean event = false;
    protected long suddenDeathTime = 300L;
    protected long floorRemovalTime = 60L;
    protected Map<UUID, Integer> scores = new HashMap<>();

    protected int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    public void addScore(UUID uuid, int score) {
        scores.put(uuid, Math.max(0, getScore(uuid) + score));
    }
}
