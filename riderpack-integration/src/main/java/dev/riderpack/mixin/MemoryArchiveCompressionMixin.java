package dev.riderpack.mixin;

import dev.riderpack.ArchiveCompression;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Changes only the archive's outer compression, preserving every JSON byte and existing readers. */
@Mixin(targets = "dev.rbd.memory.MemoryArchive$Segment", remap = false)
public abstract class MemoryArchiveCompressionMixin {
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "(Ljava/io/OutputStream;I)Ljava/util/zip/GZIPOutputStream;"))
    private GZIPOutputStream riderpack$archiveCompression(OutputStream output, int bufferSize) throws IOException {
        return ArchiveCompression.open(output, bufferSize);
    }
}
