param(
  [string]$MongoUri = $env:MONGO_URI
    ? $env:MONGO_URI
    : "mongodb://localhost:27017",
  [string]$DbName = $env:MONGO_DB
)

if (-not $DbName) {
  $appProps = "src\main\resources\application.properties"
  if (Test-Path $appProps) {
    $line = Select-String -Path $appProps -Pattern "^quarkus.mongodb.database=" | Select-Object -First 1
    if ($line) { $DbName = $line -replace ".*=", "" }
  }
  if (-not $DbName) { $DbName = "local_db" }
}

# Run all js in db\init in order
$initDir = "db\init"
if (Test-Path $initDir) {
  Get-ChildItem -Path $initDir -Filter *.js | Sort-Object Name | ForEach-Object {
    Write-Host "Applying init script $($_.Name) ..."
    mongosh $MongoUri/$DbName $_.FullName
  }
} else {
  Write-Host "No init scripts found."
}
