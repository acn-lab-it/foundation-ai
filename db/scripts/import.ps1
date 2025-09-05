param(
  [string]$MongoUri = $env:MONGO_URI
    ? $env:MONGO_URI
    : "mongodb://localhost:27017",
  [string]$DbName = $env:MONGO_DB,
  [string[]]$Collections
)

if (-not $DbName) {
  $appProps = "src\main\resources\application.properties"
  if (Test-Path $appProps) {
    $line = Select-String -Path $appProps -Pattern "^quarkus.mongodb.database=" | Select-Object -First 1
    if ($line) { $DbName = $line -replace ".*=", "" }
  }
  if (-not $DbName) { $DbName = "local_db" }
}

# Determine collections to import
if ($Collections -and $Collections.Count -gt 0) {
  $collections = $Collections
} else {
  $collections = Get-ChildItem -Path "db\collections" -Filter *.json | ForEach-Object { $_.BaseName }
}

foreach ($coll in $collections) {
  $file = Join-Path "db\collections" ("$coll.json")
  if (Test-Path $file) {
    Write-Host "Importing into $DbName.$coll from $file ..."
    mongosh --quiet --eval "db = db.getSiblingDB('$DbName'); db.$coll.drop();" --file NUL
    mongoimport --uri=$MongoUri --db=$DbName --collection=$coll --jsonArray --file=$file
  } else {
    Write-Warning "File not found for collection '$coll': $file. Skipping."
  }
}
