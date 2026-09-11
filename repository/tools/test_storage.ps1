$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $gson = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson" -Recurse -Filter 'gson-2.10.1.jar' | Select-Object -First 1
    if (-not $gson) { throw 'Build the mod first to resolve Gson 2.10.1.' }
    New-Item -ItemType Directory -Force build/storage-tests | Out-Null
    & javac --release 21 -encoding UTF-8 -cp $gson.FullName -d build/storage-tests src/main/java/dev/rbd/io/AtomicJson.java src/main/java/dev/rbd/io/SnapshotStore.java src/main/java/dev/rbd/memory/SomaticState.java src/main/java/dev/rbd/memory/MemoryFrame.java src/main/java/dev/rbd/memory/MemoryArchive.java tests/StorageSelfTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Storage test compilation failed' }
    & java -cp "build/storage-tests;$($gson.FullName)" StorageSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Storage tests failed' }
} finally { Pop-Location }
