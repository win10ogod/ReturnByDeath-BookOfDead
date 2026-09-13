package dev.rbd.io;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Retires only committed, obsolete world copies after the replacement world has reopened. */
public final class SnapshotRetention {
    private record Request(Path control, String worldName, boolean legacy, int checkpoints, int failed, Consumer<Exception> error) {}
    private record Copy(Path path, long order) {}
    private static final Map<Path, Request> pending = new HashMap<>();
    private static final Set<Path> running = new HashSet<>();

    /** Coalesced per world; no directory traversal, hashing or deletion on the game thread. */
    public static void schedule(Path control, String worldName, boolean legacy, int checkpoints, int failed, Consumer<Exception> error) {
        var request = new Request(control.toAbsolutePath().normalize(), worldName, legacy, checkpoints, failed, error);
        synchronized (pending) {
            pending.put(request.control, request);
            if (!running.add(request.control)) return;
        }
        Thread worker = new Thread(() -> {
            for (;;) {
                Request next;
                synchronized (pending) {
                    next = pending.remove(request.control);
                    if (next == null) { running.remove(request.control); return; }
                }
                try { prune(next.control, next.worldName, next.legacy, next.checkpoints, next.failed); }
                catch (Exception errorValue) { next.error.accept(errorValue); }
            }
        }, "rbd-checkpoint-retention");
        worker.setDaemon(true); worker.setPriority(Thread.MIN_PRIORITY); worker.start();
    }

    public static void prune(Path control, String worldName, boolean legacy, int checkpoints, int failed) throws IOException {
        if (checkpoints < 1 || failed < 0) throw new IllegalArgumentException("Invalid checkpoint retention");
        if (!directory(control) || Files.exists(control.resolve("fault.json")) || Files.exists(control.resolve("transaction.json")) || Files.exists(control.resolve("request.json"))) return;
        Path pointerFile = control.resolve(legacy ? "active_checkpoint.json" : "active.json");
        if (!regular(pointerFile)) return;
        JsonObject pointer = AtomicJson.read(pointerFile);
        String active = identifier(pointer, legacy ? "checkpoint_id" : "id");
        long ordinal = pointer.get("ordinal").getAsLong();
        Path snapshots = control.resolve("snapshots"), receipts = control.resolve("receipts");
        // A durable pointer alone is insufficient: do not clean up an incomplete replacement.
        if (!directory(snapshots) || !directory(receipts) || !directory(snapshots.resolve(active)) || !regular(snapshots.resolve(active).resolve("manifest.json"))) return;
        JsonObject committed = receipt(receipts, active, "CAPTURE", legacy);
        if (committed == null || committed.get("ordinal").getAsLong() != ordinal) return;
        List<Copy> obsolete = new ArrayList<>();
        for (Path path : children(snapshots)) {
            String id = path.getFileName().toString();
            if (!canonical(id) || id.equals(active) || !directory(path)) continue;
            JsonObject receipt = receipt(receipts, id, "CAPTURE", legacy);
            if (receipt == null || !receipt.has("ordinal")) continue;
            long order = receipt.get("ordinal").getAsLong();
            // Capture ordinals only advance. Never touch this pass's active copy or a newer one.
            // A concurrent capture has finished reading an older copy before publishing its pointer.
            if (order < ordinal) obsolete.add(new Copy(path, order));
        }
        retire(control, "retired-checkpoints", obsolete, checkpoints - 1);
        Path rejected = control.resolve("failed");
        List<Copy> branches = new ArrayList<>();
        String prefix = "." + worldName + ".rbd-rejected-";
        boolean migrated = regular(control.resolve("migration.json"));
        if (directory(rejected)) for (Path path : children(rejected)) {
            String name = path.getFileName().toString();
            String id = canonical(name) ? name : (legacy || migrated) && name.startsWith(prefix) ? name.substring(prefix.length()) : "";
            if (!canonical(id) || !directory(path) || receipt(receipts, id, "RESTORE", legacy) == null) continue;
            branches.add(new Copy(path, Files.getLastModifiedTime(receipts.resolve(id + ".json"), LinkOption.NOFOLLOW_LINKS).toMillis()));
        }
        retire(control, "retired-branches", branches, failed);
    }

    private static JsonObject receipt(Path receipts, String id, String operation, boolean legacy) throws IOException {
        Path file = receipts.resolve(id + ".json");
        if (!regular(file)) return null;
        JsonObject value = AtomicJson.read(file);
        // Migration preserves supervisor receipts alongside later native receipts.
        boolean supervisorReceipt = !value.has("id") && value.has("request_id");
        if (supervisorReceipt && !legacy && !regular(receipts.getParent().resolve("migration.json"))) return null;
        if (!value.has(supervisorReceipt ? "request_id" : "id") || !value.has("operation")) return null;
        if (!id.equals(identifier(value, supervisorReceipt ? "request_id" : "id")) || !operation.equals(value.get("operation").getAsString())) return null;
        if (!supervisorReceipt && (!value.has("phase") || !value.get("phase").getAsString().equals("COMMITTED"))) return null;
        return value;
    }
    private static String identifier(JsonObject value, String key) throws IOException {
        String id = value.has(key) ? value.get(key).getAsString() : "";
        if (!canonical(id)) throw new IOException("Invalid committed checkpoint identifier");
        return id;
    }
    private static boolean canonical(String id) {
        try { return UUID.fromString(id).toString().equals(id); }
        catch (IllegalArgumentException e) { return false; }
    }
    private static boolean directory(Path path) { return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS); }
    private static boolean regular(Path path) { return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS); }
    private static List<Path> children(Path path) throws IOException {
        try (var entries = Files.list(path)) { return entries.toList(); }
    }
    private static void retire(Path control, String category, List<Copy> copies, int keep) throws IOException {
        Path trash = control.resolve(category);
        if (Files.exists(trash, LinkOption.NOFOLLOW_LINKS) && !directory(trash)) throw new IOException("Invalid retired checkpoint directory");
        Files.createDirectories(trash);
        // Finish an interrupted background deletion without following any symbolic links.
        for (Path path : children(trash)) if (canonical(path.getFileName().toString()) && directory(path)) erase(path);
        copies.sort(Comparator.comparingLong(Copy::order).reversed().thenComparing(c -> c.path.toString()));
        for (int i = keep; i < copies.size(); i++) {
            Path from = copies.get(i).path;
            Path target = trash.resolve(UUID.randomUUID().toString());
            try { Files.move(from, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (NoSuchFileException alreadyRetired) { continue; }
            erase(target);
        }
    }
    private static void erase(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(directory); return FileVisitResult.CONTINUE;
            }
        });
    }
    private SnapshotRetention() {}
}
