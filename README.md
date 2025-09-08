# orchestrator-svc

This project uses Quarkus, the Supersonic Subatomic Java Framework.
If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Table of Contents
- [Where to set systemPrompt for FNOLEmailAssistantAgent](#where-to-set-systemprompt-for-fnolemailassistantagent)
- [Running the application in dev mode](#running-the-application-in-dev-mode)
- [Packaging and running the application](#packaging-and-running-the-application)
- [Creating a native executable](#creating-a-native-executable)
- [Provided Code](#provided-code)
  - [REST](#rest)
- [Gestione DB Mongo con Volume File Share su Azure](#gestione-db-mongo-con-volume-file-share-su-azure)
  - [0. Creazione della Container App](#0-creazione-della-container-app)
  - [1. Creazione e associazione del File Share](#1-creazione-e-associazione-del-file-share)
  - [2. Creazione della cartella dati](#2-creazione-della-cartella-dati)
  - [3. Montaggio del volume nella Container App](#3-montaggio-del-volume-nella-container-app)
  - [4. Creazione utente admin](#4-creazione-utente-admin)
  - [Note Importanti](#note-importanti)
    - [Nuova Revisiione](#nuova-revisiione)
    - [Se il disco si corrompe](#se-il-disco-si-corrompe)
    - [Connessione al DB](#connessione-al-db)

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/orchestrator-svc-1.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

# Gestione DB Mongo con Volume File Share su Azure

Guida passo passo per configurare **MongoDB** in **Azure Container Apps** con persistenza tramite **Azure File Share**.

---

# 0. Creazione della Container App
Quando crei la Container App, configura i dettagli del container:

- **Name**: `Nome dell'app`
- **Image source**: `Docker Hub or other registries`
- **Image type**: `Public`
- **Registry login server**: `docker.io`
- **Image and tag**: `mongo:latest`

---

## 1. Creazione e associazione del File Share
1. Crea un **File Share (SMB)** nello **Storage Account**. 
   1. Trova Storage Account per il resource group su cui crei le risorse
   2. Dentro Storage Account vai su **Data Storage** > **File shares**
   3. Crea un nuovo **File share** con le impostazioni del caso
2. Associalo in **Container Apps Environment** della Container App target, così che sia visibile come volume.
   1. Dentro **Settings** > **Azure Files** ti chiederà i dettagli facilmente recuperabili
   2. Per recuperare la **key**:
       - Vai sul File Share creato → **Connect**.
       - Leggi lo script di connessione → troverai la storage key.
3. Gli altri dati richiesti (Storage account name, Share name, ecc.) si recuperano dalle proprietà dello storage.

---

## 2. Creazione della cartella dati
- Vai all’interno del File Share, crea una cartella dedicata, ad esempio: `/dbdata`
---

## 3. Montaggio del volume nella Container App
1. Vai in **Volumes** e registra il File Share.
2. In **Containers → Volume mounts**:
- **Mount path**:`/mnt/azurefiles`
- **Subpath**: `dbdata` → *(IMPORTANTE: senza `/` iniziale).*
- **Mount options**: `uid=999,gid=999,dir_mode=0770,file_mode=0660,nobrl`
3. In **Properties** → imposta l’override:
- **Command override**: `mongod`
- **Args override**: `--dbpath, /mnt/azurefiles, --bind_ip_all, --auth`

---

## 4. Creazione utente admin
Dalla **Console** della Container App, crea manualmente un utente:

```bash
mongosh --quiet --eval '
db.getSiblingDB("admin").createUser({
  user: "IL_TUO_USER",
  pwd:  "LA_TUA_PW",
  roles: [ { role: "root", db: "admin"} ]
})
'
```

## Note Importanti

### Nuova Revisiione
Ogni volta che salvi una **nuova revisione** della Container App,  
**SPEGNI prima il container** per evitare corruzione dei file  
(il file `mongod.lock` al 98% non verrà rimosso correttamente durante creazione nuova revisione e corromperà disco).

---

### Se il disco si corrompe
1. Vai nel **File Share**.
2. Crea una nuova cartella (es. `/dbdata2`).
3. Aggiorna la configurazione del volume con il nuovo **subpath**.
4. Ripeti i passi sopra:
    - `command + args`
    - creazione utente admin

---

### Connessione al DB

Esempio di connection string:
```
mongodb://IL_TUO_USER:LA_TUA_PW@<NOME_HOST>:27017
```

- **IL_TUO_USER** → l’utente creato in `admin`.
- **LA_TUA_PW** → la password scelta.
- **<NOME_HOST>** → il FQDN o l’IP pubblico della Container App (se esposto).

⚠️ Se la password contiene caratteri speciali (`@`, `!`, `#`, ecc.),  
deve essere **percent-encodata** (es. `P@ss!word` → `P%40ss%21word`).

# orchestrator-svc

This project uses Quarkus, the Supersonic Subatomic Java Framework.
If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Table of Contents
- [Where to set systemPrompt for FNOLEmailAssistantAgent](#where-to-set-systemprompt-for-fnolemailassistantagent)
- [Running the application in dev mode](#running-the-application-in-dev-mode)
- [Packaging and running the application](#packaging-and-running-the-application)
- [Creating a native executable](#creating-a-native-executable)
- [Provided Code](#provided-code)
  - [REST](#rest)
- [Gestione DB Mongo con Volume File Share su Azure](#gestione-db-mongo-con-volume-file-share-su-azure)
  - [0. Creazione della Container App](#0-creazione-della-container-app)
  - [1. Creazione e associazione del File Share](#1-creazione-e-associazione-del-file-share)
  - [2. Creazione della cartella dati](#2-creazione-della-cartella-dati)
  - [3. Montaggio del volume nella Container App](#3-montaggio-del-volume-nella-container-app)
  - [4. Creazione utente admin](#4-creazione-utente-admin)
  - [Note Importanti](#note-importanti)
    - [Nuova Revisiione](#nuova-revisiione)
    - [Se il disco si corrompe](#se-il-disco-si-corrompe)
    - [Connessione al DB](#connessione-al-db)

## Where to set systemPrompt for FNOLEmailAssistantAgent

Il systemPrompt per FNOLEmailAssistantAgent si imposta a runtime passando il valore al metodo chat(...) dell'agente.

Nel codice dell'agente:
- L'annotazione @SystemMessage("{{systemPrompt}}") definisce che il messaggio di sistema verrà riempito con la variabile systemPrompt.
- Il parametro del metodo chat annotato con @V("systemPrompt") fornisce tale variabile.

Esempio di utilizzo:

```java
@Inject FNOLEmailAssistantAgent emailAgent;

String sessionId = "<session-id>";
String systemPrompt = "Sei un assistente che estrae dati strutturati dalle email...";
String userMessage = "<contenuto email o istruzioni utente>";

String risposta = emailAgent.chat(sessionId, systemPrompt, userMessage);
```

In altre parole: "dove si imposta il systemPrompt?" → viene passato come secondo argomento di chat(), e tramite @V("systemPrompt") finisce nel @SystemMessage("{{systemPrompt}}").

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/orchestrator-svc-1.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

# Gestione DB Mongo con Volume File Share su Azure

Guida passo passo per configurare **MongoDB** in **Azure Container Apps** con persistenza tramite **Azure File Share**.

---

# 0. Creazione della Container App
Quando crei la Container App, configura i dettagli del container:

- **Name**: `Nome dell'app`
- **Image source**: `Docker Hub or other registries`
- **Image type**: `Public`
- **Registry login server**: `docker.io`
- **Image and tag**: `mongo:latest`

---

## 1. Creazione e associazione del File Share
1. Crea un **File Share (SMB)** nello **Storage Account**. 
   1. Trova Storage Account per il resource group su cui crei le risorse
   2. Dentro Storage Account vai su **Data Storage** > **File shares**
   3. Crea un nuovo **File share** con le impostazioni del caso
2. Associalo in **Container Apps Environment** della Container App target, così che sia visibile come volume.
   1. Dentro **Settings** > **Azure Files** ti chiederà i dettagli facilmente recuperabili
   2. Per recuperare la **key**:
       - Vai sul File Share creato → **Connect**.
       - Leggi lo script di connessione → troverai la storage key.
3. Gli altri dati richiesti (Storage account name, Share name, ecc.) si recuperano dalle proprietà dello storage.

---

## 2. Creazione della cartella dati
- Vai all’interno del File Share, crea una cartella dedicata, ad esempio: `/dbdata`
---

## 3. Montaggio del volume nella Container App
1. Vai in **Volumes** e registra il File Share.
2. In **Containers → Volume mounts**:
- **Mount path**:`/mnt/azurefiles`
- **Subpath**: `dbdata` → *(IMPORTANTE: senza `/` iniziale).*
- **Mount options**: `uid=999,gid=999,dir_mode=0770,file_mode=0660,nobrl`
3. In **Properties** → imposta l’override:
- **Command override**: `mongod`
- **Args override**: `--dbpath, /mnt/azurefiles, --bind_ip_all, --auth`

---

## 4. Creazione utente admin
Dalla **Console** della Container App, crea manualmente un utente:

```bash
mongosh --quiet --eval '
" +
"db.getSiblingDB("admin").createUser({
  user: "IL_TUO_USER",
  pwd:  "LA_TUA_PW",
  roles: [ { role: "root", db: "admin"} ]
})
'```

## Note Importanti

### Nuova Revisiione
Ogni volta che salvi una **nuova revisione** della Container App,  
**SPEGNI prima il container** per evitare corruzione dei file  
(il file `mongod.lock` al 98% non verrà rimosso correttamente durante creazione nuova revisione e corromperà disco).

---

### Se il disco si corrompe
1. Vai nel **File Share**.
2. Crea una nuova cartella (es. `/dbdata2`).
3. Aggiorna la configurazione del volume con il nuovo **subpath**.
4. Ripeti i passi sopra:
    - `command + args`
    - creazione utente admin

---

### Connessione al DB

Esempio di connection string:
```
mongodb://IL_TUO_USER:LA_TUA_PW@<NOME_HOST>:27017
```

- **IL_TUO_USER** → l’utente creato in `admin`.
- **LA_TUA_PW** → la password scelta.
- **<NOME_HOST>** → il FQDN o l’IP pubblico della Container App (se esposto).

⚠️ Se la password contiene caratteri speciali (`@`, `!`, `#`, ecc.),  
- deve essere **percent-encodata** (es. `P@ss!word` → `P%40ss%21word`).


## DB versioning and sync (MongoDB)

We keep versioned collection snapshots and scripts under db/.
- Collections JSON: db/collections/*.json
- Init/Migrations: db/init/*.js (executed in order by seed.ps1)
- Scripts:
  - Bash (primary): db\scripts\export.sh, db\scripts\import.sh, db\scripts\seed.sh
  - PowerShell (wrappers on Windows via WSL): db\scripts\export.ps1, db\scripts\import.ps1, db\scripts\seed.ps1

Defaults
- Mongo URI: mongodb://localhost:27017 (override with env MONGO_URI)
- Database name: read from src\main\resources\application.properties quarkus.mongodb.database or env MONGO_DB

Examples
- Linux/WSL:
  - ./db/scripts/export.sh
  - ./db/scripts/import.sh
  - ./db/scripts/seed.sh
- Windows (PowerShell wrappers → WSL):
  - .\db\scripts\export.ps1
  - .\db\scripts\import.ps1
  - .\db\scripts\seed.ps1

Filter specific collections
- Export only policy and prompts:
  - .\db\scripts\export.ps1 -Collections policy,prompts
- Import only prompts:
  - .\db\scripts\import.ps1 -Collections prompts
