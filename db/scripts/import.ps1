param(
  [string]$MongoUri = $env:MONGO_URI,
  [string]$DbName = $env:MONGO_DB,
  [string[]]$Collections
)

if (-not $MongoUri) {
  $MongoUri = "mongodb://localhost:27017"
}

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

# Opzione di parsing per EJSON.parse: true (relaxed) in linea con export; impostare a $false per canonico
$relaxed = $true
$relaxedJs = $relaxed.ToString().ToLower()

foreach ($coll in $collections) {
  $file = Join-Path "db\collections" ("$coll.json")
  if (Test-Path $file) {
    Write-Host "Importing into $DbName.$coll from $file ..."
    # Prepara contenuto del file come Base64 per evitare problemi di quoting/size
    $rawContent = Get-Content -LiteralPath $file -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($rawContent)) {
      Write-Warning "Empty file for collection '$coll': $file. Skipping."
      continue
    }
    $b64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($rawContent))
    $collJs = ($coll -replace "'", "\\'")
    $b64Js = ($b64 -replace "'", "\\'")
    $dbJs  = ($DbName -replace "'", "\\'")
    # Crea file JS temporaneo per evitare limite lunghezza riga di comando
    $tmpJsPath = [System.IO.Path]::ChangeExtension([System.IO.Path]::GetTempFileName(), ".js")
    $jsCode = @"
const c = '$collJs';
const raw = Buffer.from('$b64Js','base64').toString('utf8');
db = db.getSiblingDB('$dbJs');
db.getCollection(c).drop();
let docs;
try {
  docs = EJSON.parse(raw, { relaxed: $relaxedJs });
} catch (e) {
  print('Failed to parse EJSON for collection ' + c + ': ' + e);
  quit(1);
}
if (!Array.isArray(docs)) {
  print('Input is not a JSON array for collection ' + c);
  quit(1);
}
if (docs.length > 0) {
  db.getCollection(c).insertMany(docs, { ordered: false });
  print('Inserted ' + docs.length + ' docs into ' + c);
} else {
  print('No documents to insert for ' + c);
}
"@
    Set-Content -LiteralPath $tmpJsPath -Value $jsCode -Encoding UTF8
    try {
      mongosh --quiet --file "$tmpJsPath" "$MongoUri/$DbName" | Write-Output
    } finally {
      Remove-Item -LiteralPath $tmpJsPath -ErrorAction SilentlyContinue
    }
  } else {
    Write-Warning "File not found for collection '$coll': $file. Skipping."
  }
}
