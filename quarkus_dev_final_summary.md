# Quarkus:dev Processes - Final Summary

In response to your request for all processes related to `quarkus:dev`, I've identified **three key processes**:

1. **Maven Wrapper Process** (PID: 79455)
   - Initiates Quarkus development mode
   - Command: `./mvnw quarkus:dev`

2. **Quarkus Application Server** (PID: 81092)
   - Started by the Maven Wrapper
   - Only listening on debug port 5005

3. **JSpawnHelper Process** (PID: 81107)
   - Helper process for Java operations

The application appears to be **partially started** - debug port active, but HTTP port (8085) inactive.

Please refer to the detailed documents created:
- `quarkus_dev_processes_report.md` - Technical details
- `quarkus_dev_summary_and_recommendations.md` - Troubleshooting recommendations