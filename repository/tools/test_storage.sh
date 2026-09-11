#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
gson_jar=$(rg --files "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/com.google.code.gson/gson" -g 'gson-2.10.1.jar' | head -1)
if [ -z "$gson_jar" ]; then echo 'Run the Gradle build first to resolve Gson 2.10.1.' >&2; exit 1; fi
mkdir -p build/storage-tests
javac --release 21 -cp "$gson_jar" -d build/storage-tests src/main/java/dev/rbd/io/AtomicJson.java src/main/java/dev/rbd/io/SnapshotStore.java src/main/java/dev/rbd/memory/SomaticState.java src/main/java/dev/rbd/memory/MemoryFrame.java src/main/java/dev/rbd/memory/MemoryArchive.java tests/StorageSelfTest.java
java -cp "build/storage-tests:$gson_jar" StorageSelfTest
