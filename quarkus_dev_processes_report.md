# Quarkus:dev Processes Report

Based on the system analysis, I've identified the following processes related to `quarkus:dev`:

## Process Hierarchy

```
PID 79455 (Maven Wrapper) - Parent: 77596
  └── PID 81092 (Quarkus Application Server) - Parent: 79455
       └── PID 81107 (JSpawnHelper) - Parent: 81092
```

## Detailed Process Information

### 1. Maven Wrapper Process (PID: 79455)
- **Main Class**: `org.apache.maven.wrapper.MavenWrapperMain`
- **Arguments**: `quarkus:dev`
- **Role**: Initiates the Quarkus development mode

### 2. Quarkus Application Server Process (PID: 81092)
- **JAR File**: `orchestrator-svc-dev.jar`
- **Network**: Listening on port 5005 (Debug port)
- **Role**: Runs the actual Quarkus application

### 3. JSpawnHelper Process (PID: 81107)
- **Role**: Helper process for Java spawning operations

## Network Information
- HTTP port configured as 8085 (from application.properties)
- Currently only listening on port 5005 for debugging
- No active HTTP listener detected on port 8085

## Status Summary
The Quarkus development mode is running with three related processes, but the application server appears to be only partially started.