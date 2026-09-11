#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/core-tests
javac --release 21 -d build/core-tests src/main/java/dev/rbd/memory/SomaticState.java src/main/java/dev/rbd/core/*.java tests/CoreSelfTest.java
java -cp build/core-tests CoreSelfTest
