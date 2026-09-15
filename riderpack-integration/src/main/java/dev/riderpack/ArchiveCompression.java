package dev.riderpack;

import dev.rbd.rules.WorldRules;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/** PNG memory images are already losslessly compressed. Avoid compressing them again on the tick thread. */
public final class ArchiveCompression {
    public static final WorldRules.Setting<Integer> LEVEL = WorldRules.integer("rbdArchiveCompressionLevel", () -> 0, 0, 9);
    public static void register() { }
    public static GZIPOutputStream open(OutputStream output, int bufferSize) throws IOException {
        return new ArchiveGzip(output, bufferSize, LEVEL.get());
    }
    public static final class ArchiveGzip extends GZIPOutputStream {
        public ArchiveGzip(OutputStream output, int bufferSize, int level) throws IOException {
            super(output, bufferSize);
            def.setLevel(level);
        }
    }
    private ArchiveCompression() { }
}
