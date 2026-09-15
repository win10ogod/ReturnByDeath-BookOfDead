package dev.riderpack;

import net.minecraft.server.MinecraftServer;

/** Quiesce Xaero's world-save readers before unload, while server tasks can still complete. */
public final class MapReturnGate {
    private static boolean paused;
    private static int readers;
    private MapReturnGate() {}
    public static synchronized boolean enter() {
        if (paused) return false;
        readers++;
        return true;
    }
    public static synchronized void leave() { readers--; }
    private static synchronized boolean idle() { return readers == 0; }
    public static void before(MinecraftServer server) {
        synchronized (MapReturnGate.class) { paused = true; }
        // A reader can hold Xaero's capability monitor while waiting for server.submit().
        // Pump that queue before LevelEvent.Unload tries to acquire the same monitor.
        server.managedBlock(MapReturnGate::idle);
    }
    public static synchronized void after() { paused = false; }
}
