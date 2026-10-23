package com.hammad.redmagictweaks.xposed;

import android.os.Handler;
import android.os.Message;

import com.hammad.redmagictweaks.Features;

import de.robv.android.xposed.XC_MethodHook;

/** cn.nubia.gamelab — AI trigger "toys": scale their internal delays (scan / click / cooldown). */
final class GameLabHooks {
    private GameLabHooks() {}

    static void install(ClassLoader cl) {
        H.hook(H.find(Handler.class, "sendMessageDelayed", Message.class, long.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                int idx = Cfg.choice(Features.GAME_AI_SPEED);
                int div = Cfg.hasNum(Features.GAME_AI_SPEED) ? Math.max(1, Math.min(10, Cfg.num(Features.GAME_AI_SPEED, 1)))
                        : idx > 0 && idx < Features.AI_SPEED_DIV.length ? Features.AI_SPEED_DIV[idx] : 1;
                if (div <= 1) return;
                long delay = (Long) p.args[1];
                if (delay < 20) return;
                if (!H.calledFromPrefix("cn.nubia.gamelab.toy")) return;
                long scaled = Math.max(1, delay / div);
                p.args[1] = scaled;
            }
        });
        Lx.i("GameLab: AI trigger speed hook ready");
    }
}
