package com.winthier.spleef;

import com.cavetale.core.command.AbstractCommand;

public final class SpleefCommand extends AbstractCommand<SpleefPlugin> {
    protected SpleefCommand(final SpleefPlugin plugin) {
        super(plugin, "spleef");
    }

    @Override
    protected void onEnable() { }
}
