package com.hammad.redmagictweaks;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile for Safe mode: one tap pauses every tweak (settings kept), another brings them back. */
public class SafeModeTile extends TileService {
    private volatile boolean busy;

    @Override public void onStartListening() { update(); }

    @Override public void onClick() {
        if (busy) return;
        busy = true;
        final boolean on = !Safety.safeMode(this);
        Tile t = getQsTile();
        if (t != null) { t.setSubtitle("Restarting…"); t.updateTile(); }
        new Thread(() -> {
            try { Safety.setSafeMode(getApplicationContext(), on); } finally { busy = false; update(); }
        }).start();
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = Safety.safeMode(this);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Safe mode");
        t.setSubtitle(on ? "Tweaks paused" : "Off");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_shield));
        t.updateTile();
    }
}
