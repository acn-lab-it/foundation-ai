param(
  [string]$MongoUri = $env:MONGO_URI
    ? $env:MONGO_URI
    : "mongodb://localhost:27017",
  [string]$DbName = $env:MONGO_DB,
  [string[]]$Collections
)

if (-not $DbName) {
  # Try to read from application.properties
  $appProps = "src\main\resources\application.properties"
  if (Test-Path $appProps) {
    $line = Select-String -Path $appProps -Pattern "^quarkus.mongodb.database=" | Select-Object -First 1
    if ($line) { $DbName = $line -replace ".*=", "" }
  }
  if (-not $DbName) { $DbName = "local_db" }
}

# Determine collections to export
if ($Collections -and $Collections.Count -gt 0) {
  $collections = $Collections
} else {
  $collections = @("policy","whathappened","prompts","chat_memory","email_parsing_result","final_output_json")
}

$exportDir = "db\collections"
New-Item -ItemType Directory -Force -Path $exportDir | Out-Null

foreach ($coll in $collections) {
  Write-Host "Exporting $DbName.$coll ..."
  $out = Join-Path $exportDir ("$coll.json")
  # Requires mongoexport in PATH
  mongoexport --uri=$MongoUri --db=$DbName --collection=$coll --jsonArray --out=$out
}
