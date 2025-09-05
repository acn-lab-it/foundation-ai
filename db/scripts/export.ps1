param(
  [string]$MongoUri = $env:MONGO_URI,
  [string]$DbName = $env:MONGO_DB,
  [string[]]$Collections
)

if (-not $MongoUri) {
  $MongoUri = "mongodb://localhost:27017"
}

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
  # Requires mongosh in PATH
  # Nota: imposta relaxed a $true per JSON più "umano", oppure $false per formato canonico.
  $relaxed = $true
  # Build eval JS ensuring the collection name is a proper JS string literal and survives PowerShell arg parsing
  $collJs = ($coll -replace "'", "\\'")
  $relaxedJs = $relaxed.ToString().ToLower()
  $eval = @"
const c = '$collJs';
const docs = db.getCollection(c).find({}).toArray();
print(EJSON.stringify(docs, { relaxed: $relaxedJs }));
"@
  mongosh --quiet --eval $eval "$MongoUri/$DbName" | Out-File -FilePath $out -Encoding utf8
}
