package dev.rbd.api;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;
import java.nio.file.Path;

/** Optional integration hooks for mods retaining references outside ordinary level/player persistence.
 * Both events run on the server thread. Before precedes saving/closing levels; After follows replacement
 * of all levels and players, with existing play connections attached. No logout/login events are fired.
 */
public abstract class WorldReturnEvent extends Event {
    private final MinecraftServer server;
    private final Path archive;
    protected WorldReturnEvent(MinecraftServer server,Path archive){this.server=server;this.archive=archive;}
    public MinecraftServer getServer(){return server;}
    public Path getArchivePath(){return archive;}
    public static final class Before extends WorldReturnEvent {public Before(MinecraftServer server,Path archive){super(server,archive);}}
    public static final class After extends WorldReturnEvent {public After(MinecraftServer server,Path archive){super(server,archive);}}
}
