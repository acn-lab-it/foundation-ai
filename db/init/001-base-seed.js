// Base seed generated from existing mongo-init.js minimal subset
const dbName = 'local_db';
const dbref = db.getSiblingDB(dbName);

// Ensure collections exist
['policy','whathappened','prompts'].forEach(c => dbref.createCollection(c));

// Import data if repository JSON files contain records
function importJsonArray(collName, filePath) {
  // mongosh doesn't have native file read here; this is a placeholder for manual or scripted imports
}

// You can copy portions from mongo-init.js into separate versioned scripts as needed.
