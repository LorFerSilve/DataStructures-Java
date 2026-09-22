param([switch]$Demo)

$ErrorActionPreference = 'Stop'
$taskClasses = Join-Path $PSScriptRoot 'build\classes'
New-Item -ItemType Directory -Force -Path $taskClasses | Out-Null
$taskSources = @(
    Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' -File
    Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests') -Filter '*.java' -File
) | Select-Object -ExpandProperty FullName

& javac --release 17 -Xlint:all -Werror -d $taskClasses $taskSources
if ($LASTEXITCODE -ne 0) {
    throw 'Java compilation failed.'
}

& java -cp $taskClasses DataStructures.AllTests
if ($LASTEXITCODE -ne 0) {
    throw 'Tests failed.'
}

if ($Demo) {
    & java -cp $taskClasses DataStructures.Examples
    if ($LASTEXITCODE -ne 0) {
        throw 'Examples failed.'
    }
}
