#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
gson_cache="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/com.google.code.gson/gson"
if command -v rg >/dev/null 2>&1; then
    gson_jar=$(rg --files "$gson_cache" -g 'gson-2.10.1.jar' | head -1)
else
    gson_jar=$(find "$gson_cache" -name 'gson-2.10.1.jar' -print -quit)
fi
if [ -z "$gson_jar" ]; then echo 'Run the Gradle build first to resolve Gson 2.10.1.' >&2; exit 1; fi
mkdir -p build/storage-tests
javac --release 21 -cp "$gson_jar" -d build/storage-tests src/main/java/dev/rbd/io/OrderedIo.java src/main/java/dev/rbd/io/AtomicJson.java src/main/java/dev/rbd/io/SnapshotStore.java src/main/java/dev/rbd/memory/SomaticState.java src/main/java/dev/rbd/memory/MemoryFrame.java src/main/java/dev/rbd/memory/MemoryArchive.java tests/StorageSelfTest.java
java -cp "build/storage-tests:$gson_jar" StorageSelfTest
