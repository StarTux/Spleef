package com.winthier.minigames.spleef;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum State {
    INIT(60),
    WAIT_FOR_PLAYERS(60),
    COUNTDOWN(5),
    SPLEEF(2 * 60),
    END(60);

    public final long seconds;
}
