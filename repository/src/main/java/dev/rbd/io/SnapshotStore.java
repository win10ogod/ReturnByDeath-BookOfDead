package dev.rbd.io;

import com.google.gson.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import static java.nio.file.StandardCopyOption.*;
import static java.nio.file.StandardOpenOption.*;

/** Offline snapshots. The caller must have joined the server thread before invoking a transaction. */
public final class SnapshotStore {
    public final Path world, control;
    public SnapshotStore(Path world, Path control) throws IOException {
        this.world = world.toAbsolutePath().normalize();
        this.control = control.toAbsolutePath().normalize();
        if (this.world.startsWith(this.control) || this.control.startsWith(this.world))
            throw new IOException("World and memory archive must be disjoint");
        Files.createDirectories(this.control);
    }
    public static Path controlFor(Path world) {
        Path absolute = world.toAbsolutePath().normalize();
        return absolute.getParent().resolve(".rbd").resolve(absolute.getFileName().toString());
    }
    public JsonObject active() throws IOException {
        Path file = control.resolve("active.json");
        return Files.exists(file) ? AtomicJson.read(file) : null;
    }
    public void prepare(String operation, JsonObject death) throws IOException {
        if (Files.exists(control.resolve("transaction.json"))) throw new IOException("Unresolved return transaction");
        JsonObject tx = new JsonObject();
        tx.addProperty("id", UUID.randomUUID().toString());
        tx.addProperty("operation", operation);
        tx.addProperty("phase", "PREPARED");
        JsonObject active = active();
        if (operation.equals("RESTORE") && active == null) throw new IOException("No committed return point");
        tx.addProperty("checkpoint", active == null ? "" : active.get("id").getAsString());
        tx.addProperty("ordinal", active == null ? 1 : active.get("ordinal").getAsLong() + 1);
        if (death != null) tx.add("death", death);
        AtomicJson.write(control.resolve("transaction.json"), tx);
    }
    public boolean pending() { return Files.exists(control.resolve("transaction.json")); }
    public void markClosed() throws IOException {
        JsonObject tx = AtomicJson.read(control.resolve("transaction.json"));
        tx.addProperty("closed", true);
        AtomicJson.write(control.resolve("transaction.json"), tx);
    }
    public void complete() throws IOException {
        if (!pending()) return;
        Path lockPath = control.resolve("transaction.lock");
        try (FileChannel channel = FileChannel.open(lockPath, CREATE, WRITE); FileLock ignored = channel.tryLock()) {
            if (ignored == null) throw new IOException("Return transaction already running");
            JsonObject tx = AtomicJson.read(control.resolve("transaction.json"));
            if (!tx.has("closed") || !tx.get("closed").getAsBoolean()) throw new IOException("World did not close normally; transaction retained");
            String id = uuid(tx.get("id").getAsString());
            if (tx.get("operation").getAsString().equals("CAPTURE")) capture(tx, id);
            else if (tx.get("operation").getAsString().equals("RESTORE")) restore(tx, id);
            else throw new IOException("Unknown transaction operation");
            AtomicJson.write(control.resolve("receipts").resolve(id + ".json"), tx);
            Files.delete(control.resolve("transaction.json"));
        } catch (OverlappingFileLockException e) { throw new IOException("Return transaction already running", e); }
    }
    private void capture(JsonObject tx, String id) throws IOException {
        Path snapshot = control.resolve("snapshots").resolve(id);
        if (!Files.exists(snapshot.resolve("manifest.json"))) {
            if (Files.exists(snapshot)) throw new IOException("Incomplete checkpoint retained at " + snapshot);
            Path stage = control.resolve("snapshots").resolve(id + ".partial");
            if (Files.exists(stage)) Files.move(stage, stage.resolveSibling(id + ".interrupted-" + UUID.randomUUID()));
            Files.createDirectories(stage);
            JsonObject manifest;
            try (WorldLock ignored = lockWorld(world)) { manifest = copyVerified(world, stage.resolve("tree")); }
            AtomicJson.write(stage.resolve("manifest.json"), manifest);
            Files.move(stage, snapshot, ATOMIC_MOVE);
        }
        verify(snapshot.resolve("tree"), AtomicJson.read(snapshot.resolve("manifest.json")));
        JsonObject pointer = new JsonObject();
        pointer.addProperty("id", id); pointer.add("ordinal", tx.get("ordinal"));
        JsonObject old = active();
        if (old != null && old.get("ordinal").getAsLong() > pointer.get("ordinal").getAsLong()) throw new IOException("Checkpoint regression rejected");
        AtomicJson.write(control.resolve("active.json"), pointer);
        tx.addProperty("phase", "COMMITTED");
    }
    private void restore(JsonObject tx, String id) throws IOException {
        String checkpoint = uuid(tx.get("checkpoint").getAsString());
        JsonObject pointer = active();
        if (pointer == null || !pointer.get("id").getAsString().equals(checkpoint)) throw new IOException("Only the active checkpoint may be restored");
        Path snapshot = control.resolve("snapshots").resolve(checkpoint);
        JsonObject manifest = AtomicJson.read(snapshot.resolve("manifest.json"));
        verify(snapshot.resolve("tree"), manifest);
        Path stage = control.resolve("staging").resolve(id);
        Path rejected = control.resolve("failed").resolve(id);
        Files.createDirectories(stage.getParent());Files.createDirectories(rejected.getParent());
        String phase = tx.get("phase").getAsString();
        if (phase.equals("PREPARED")) {
            if (Files.exists(rejected)) throw new IOException("Unexpected failed branch; refusing ambiguous restore");
            if (Files.exists(stage)) {
                try { verify(stage, manifest); }
                catch (IOException e) { Files.move(stage, stage.resolveSibling(stage.getFileName() + ".interrupted-" + UUID.randomUUID())); }
            }
            if (!Files.exists(stage)) copyVerified(snapshot.resolve("tree"), stage);
            tx.addProperty("phase", "STAGED"); AtomicJson.write(control.resolve("transaction.json"), tx);
            phase = "STAGED";
        }
        if (phase.equals("STAGED")) {
            // Persist intent before rename. Directory combinations make interruption recovery unambiguous.
            if (!Files.exists(rejected)) {
                try (WorldLock ignored = lockWorld(world)) { /* verify the Minecraft writer has released its lock */ }
                Files.move(world, rejected, ATOMIC_MOVE);
            } else if (Files.exists(world)) throw new IOException("Ambiguous original/failed world pair");
            tx.addProperty("phase", "OLD_MOVED"); AtomicJson.write(control.resolve("transaction.json"), tx);
            phase = "OLD_MOVED";
        }
        if (phase.equals("OLD_MOVED")) {
            if (Files.exists(stage) && !Files.exists(world)) Files.move(stage, world, ATOMIC_MOVE);
            else if (Files.exists(stage) || !Files.exists(world)) throw new IOException("Ambiguous replacement world");
            verify(world, manifest);
            tx.addProperty("phase", "INSTALLED"); AtomicJson.write(control.resolve("transaction.json"), tx);
        }
        verify(world, manifest);
        if (tx.has("death")) {
            AtomicJson.write(control.resolve("returns").resolve(id + ".json"), tx.getAsJsonObject("death"));
            AtomicJson.write(control.resolve("last_return.json"), tx.getAsJsonObject("death"));
        }
        tx.addProperty("phase", "COMMITTED");
    }
    public static JsonObject inventory(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid tree: " + root);
        JsonObject files = new JsonObject(); JsonArray dirs = new JsonArray();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                if (path.equals(root)) continue;
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.equals("session.lock")) continue;
                if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links cannot be snapshotted: " + path);
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { dirs.add(relative); continue; }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Non-regular file: " + path);
                JsonObject data = new JsonObject(); data.addProperty("size", Files.size(path)); data.addProperty("sha256", hash(path)); files.add(relative, data);
            }
        }
        JsonObject result = new JsonObject(); result.add("files", files); result.add("directories", dirs); return result;
    }
    public static JsonObject copyVerified(Path source, Path target) throws IOException {
        JsonObject before = inventory(source);
        long bytes = before.getAsJsonObject("files").entrySet().stream().mapToLong(e -> e.getValue().getAsJsonObject().get("size").getAsLong()).sum();
        Files.createDirectories(target.getParent());
        if (Files.getFileStore(target.getParent()).getUsableSpace() < bytes) throw new IOException("Insufficient space for the complete world snapshot");
        Files.createDirectory(target);
        for (JsonElement directory : before.getAsJsonArray("directories")) Files.createDirectories(target.resolve(directory.getAsString()));
        for (String relative : before.getAsJsonObject("files").keySet()) {
            Path out = target.resolve(relative); Files.copy(source.resolve(relative), out, COPY_ATTRIBUTES);
            try (FileChannel file = FileChannel.open(out, WRITE)) { file.force(true); }
        }
        verify(source, before); verify(target, before); return before;
    }
    public static void verify(Path root, JsonObject manifest) throws IOException {
        if (!inventory(root).equals(manifest)) throw new IOException("Snapshot checksum mismatch: " + root);
    }
    public static String hash(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) { byte[] buffer = new byte[65536]; int n; while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static String uuid(String value) throws IOException {
        try { if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException(); return value; }
        catch (IllegalArgumentException e) { throw new IOException("Invalid transaction identifier", e); }
    }
    private static WorldLock lockWorld(Path world) throws IOException { return new WorldLock(world); }
    private static final class WorldLock implements AutoCloseable {
        private final FileChannel channel; private final FileLock lock;
        WorldLock(Path world) throws IOException {
            if (!Files.isDirectory(world)) throw new IOException("World missing: " + world);
            Path path = world.resolve("session.lock");
            if (Files.isSymbolicLink(path)) throw new IOException("Invalid world lock");
            channel = FileChannel.open(path, CREATE, WRITE);
            try { lock = channel.tryLock(); if (lock == null) throw new IOException("World is still open"); }
            catch (IOException | RuntimeException e) { channel.close(); throw new IOException("World is still open", e); }
        }
        @Override public void close() throws IOException { lock.release(); channel.close(); }
    }
}
