# Quarkus:dev Summary and Recommendations

## Summary of Findings

I've identified three processes related to `quarkus:dev` on your system:

1. **Maven Wrapper Process** (PID: 79455)
   - Initiates the Quarkus development mode
   - Command: `./mvnw quarkus:dev`

2. **Quarkus Application Server** (PID: 81092)
   - Started by the Maven Wrapper
   - Only listening on debug port 5005, not on HTTP port 8085

3. **JSpawnHelper Process** (PID: 81107)
   - Helper process started by the Quarkus Application Server

## Current Status

The Quarkus development mode appears to be **partially started**:
- Debug port (5005) is active
- HTTP port (8085) is not active
- Application is not fully operational

## Recommendations

If you need to fully start or restart the Quarkus application:

1. **Terminate the current processes**:
   ```bash
   kill 79455 81092 81107
   ```

2. **Start Quarkus in dev mode with console output**:
   ```bash
   cd /Users/francesco.stumpo/IdeaProjects/Allianz/bmp/foundation-ai
   ./mvnw quarkus:dev
   ```
   This will show you any startup errors in real-time.

3. **Check for MongoDB availability**:
   The application is configured to connect to MongoDB at `mongodb://localhost:27017`.
   Ensure MongoDB is running:
   ```bash
   docker ps | grep mongo
   # or
   ps aux | grep mongod
   ```

4. **Verify port availability**:
   Make sure port 8085 is not being used by another application:
   ```bash
   lsof -i:8085
   ```

5. **Check application logs**:
   ```bash
   ls -la target/quarkus.log
   # If exists:
   tail -n 100 target/quarkus.log
   ```

By following these recommendations, you should be able to get the Quarkus application fully operational in development mode.