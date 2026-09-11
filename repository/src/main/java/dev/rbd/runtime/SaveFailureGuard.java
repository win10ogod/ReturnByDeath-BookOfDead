package dev.rbd.runtime;

import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Vanilla catches several save failures and only logs them, even on a clean process exit. */
public final class SaveFailureGuard extends AbstractAppender implements AutoCloseable {
    private static final Set<String> STORAGE=Set.of("MinecraftServer","PlayerDataStorage","LevelStorageSource",
        "SavedData","DimensionDataStorage","ChunkMap","ChunkStorage","IOWorker","RegionFile",
        "RegionFileStorage","SectionStorage","EntityStorage","ServerChunkCache","ServerLevel");
    private final AtomicReference<String> failure=new AtomicReference<>();
    private org.apache.logging.log4j.core.Logger root;
    public SaveFailureGuard(){super("RBD-save-guard-"+java.util.UUID.randomUUID(),null,null,true,Property.EMPTY_ARRAY);}
    public void attach() throws IOException {
        if(!(LogManager.getRootLogger() instanceof org.apache.logging.log4j.core.Logger logger))throw new IOException("Cannot observe Minecraft persistence failures");
        root=logger;start();root.addAppender(this);
    }
    @Override public void append(LogEvent event){
        String logger=event.getLoggerName();if(logger==null)return;
        String simple=logger.substring(logger.lastIndexOf('.')+1);
        if(STORAGE.contains(simple)&&event.getLevel().isMoreSpecificThan(Level.WARN)
            &&(event.getThrown()!=null||event.getLevel().isMoreSpecificThan(Level.ERROR)))
            failure.compareAndSet(null,logger+": "+event.getMessage().getFormattedMessage());
    }
    public void check() throws IOException {if(failure.get()!=null)throw new IOException("Minecraft persistence did not complete cleanly: "+failure.get());}
    @Override public void close(){if(root!=null)root.removeAppender(this);stop();}
}
