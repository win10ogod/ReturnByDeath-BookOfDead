param([Parameter(Mandatory=$true)][string]$GsonJar, [Parameter(Mandatory=$true)][string]$JavaBin)
$ErrorActionPreference = 'Stop'
$env:PATH = $JavaBin + ';' + $env:PATH
Push-Location (Join-Path $PSScriptRoot '..')
try {
    if (-not (Test-Path $GsonJar)) { throw 'Gson jar not found' }
    javac --release 21 -encoding UTF-8 -cp $GsonJar -d build/storage-tests-windows src/main/java/dev/rbd/io/AuthorityRoster.java src/main/java/dev/rbd/io/AtomicJson.java src/main/java/dev/rbd/io/SnapshotStore.java src/main/java/dev/rbd/memory/SomaticState.java src/main/java/dev/rbd/memory/MemoryFrame.java src/main/java/dev/rbd/memory/MemoryArchive.java src/main/java/dev/rbd/core/*.java tests/StorageSelfTest.java tests/LockProbe.java tests/CoreSelfTest.java tests/MortalitySelfTest.java tests/AuthoritySelfTest.java
    if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
    java -cp "build/storage-tests-windows;$GsonJar" StorageSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Java storage tests failed' }
    java -cp "build/storage-tests-windows;$GsonJar" CoreSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Core rules failed' }
    java -cp "build/storage-tests-windows;$GsonJar" MortalitySelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Mortality model failed' }
    java -cp "build/storage-tests-windows;$GsonJar" AuthoritySelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Authority model failed' }
    python tools/test_lock_interop.py --java java --classes build/storage-tests-windows
    if ($LASTEXITCODE -ne 0) { throw "Java/Python lock interoperability failed" }
    $previousMigrationClasspath = $env:RBD_MIGRATION_CLASSPATH
    $env:RBD_MIGRATION_CLASSPATH = (Resolve-Path build/storage-tests-windows).Path + ';' + $GsonJar
    Push-Location supervisor
    try { python -m unittest -v test_supervisor test_migration; if ($LASTEXITCODE -ne 0) { throw 'Supervisor/migration tests failed' } }
    finally { Pop-Location; $env:RBD_MIGRATION_CLASSPATH = $previousMigrationClasspath }
} finally { Pop-Location }
