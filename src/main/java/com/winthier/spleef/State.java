package com.winthier.spleef;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum State {
    INIT(60),
    COUNTDOWN(30),
    /**
     * Main game. Seconds are ignored. Use Save.suddenDeathTime
     * instead!
     */
    SPLEEF(2 * 60),
    END(60);

    public final long seconds;
}
