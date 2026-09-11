$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
New-Item -ItemType Directory -Force build/core-tests | Out-Null
$files = Get-ChildItem -Path src/main/java/dev/rbd/memory/SomaticState.java,src/main/java/dev/rbd/core/*.java | ForEach-Object { $_.FullName }
& javac --release 21 -d build/core-tests @files tests/CoreSelfTest.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp build/core-tests CoreSelfTest
exit $LASTEXITCODE
