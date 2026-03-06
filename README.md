# Blockchain Camera Node Platform

## Overview
The Blockchain Camera Node Platform is a decentralized, Spring Boot-based Java application designed to capture, process, and store image data on a custom proof-of-work blockchain. The system utilizes a main node and multiple worker nodes to handle image similarity checks, cryptographic hashing, and block mining operations. High-performance communication between nodes is achieved using WebSockets.

## Key Features
- **Proof-of-Work Blockchain Implementation:** Mining operations coordinate across the network with a dynamically set difficulty level to securely append image records to the ledger.
- **Node-Worker Architecture:** Supports distributed compute capabilities where the main node dispatches computational tasks across a variable number of worker profiles.
- **WebSocket Communication:** Uses active profiles (node, worker1, worker2) establishing persistent real-time connections.
- **Image Processing and Similarity Validation:** Made by my precious CLAUDE. image processing doesnt work properly so needs revise
- **REST API Subsystem:** Exposes administrative endpoints for monitoring network status, querying recent blocks, and managing worker lifecycle.

## Technology Stack
- **Java** 17 (or higher)
- **Spring Boot** 3.5.x
- **WebSocket Protocol** for bidirectional node communication
- **Maven** for dependency management and build automation
- **Jackson** for high-performance JSON processing and serialization

## Getting Started

### Prerequisites
- JDK 17 or higher installed 
- Maven (or use the included Maven wrapper, `mvnw`)

### Running the Application

This application handles configuration using explicit Spring profiles. It relies on a primary coordinating node and multiple supplementary mining workers.

1. **Start the Main Node**
   Open a terminal and set the profile to `node`.
   
   **Windows PowerShell:**
   ```powershell
   $env:SPRING_PROFILES_ACTIVE="node"; .\mvnw.cmd spring-boot:run
   ```
   
   **Linux / macOS:**
   ```bash
   SPRING_PROFILES_ACTIVE=node ./mvnw spring-boot:run
   ```
   The main node will start on `http://localhost:8080`.

2. **Start Worker Nodes**
   Open additional terminals to start the worker nodes. They will automatically attempt to establish WebSocket connections to the main node.
   
   **Windows PowerShell:**
   ```powershell
   $env:SPRING_PROFILES_ACTIVE="worker1"; .\mvnw.cmd spring-boot:run
   $env:SPRING_PROFILES_ACTIVE="worker2"; .\mvnw.cmd spring-boot:run
   ```
   
   **Linux / macOS:**
   ```bash
   SPRING_PROFILES_ACTIVE=worker1 ./mvnw spring-boot:run
   SPRING_PROFILES_ACTIVE=worker2 ./mvnw spring-boot:run
   ```
   Worker 1 will start on `http://localhost:8081` and Worker 2 on `http://localhost:8082`.

### REST API Overview
You can interact with the primary node through the following REST endpoints:

- `GET /api/blockchain/info` - View main node configuration properties and active parameters.
- `GET /api/blockchain/status` - Access current state of the blockchain (ledger height, last block references).
- `GET /api/blockchain/workers` - Retrieve a list of actively connected worker instances.
- `GET /api/blockchain/worker/mining-status` - View aggregated mining progress across the network.
- `POST /api/blockchain/mining/start` - Initiate mining tasks onto the network and broadcast tasks.
- `POST /api/blockchain/worker/stop-mining` - Halt running proof-of-work subroutines globally.
- `GET /api/blockchain/health` - Internal system diagnostic probe.

### Network Protocol Details
Worker nodes and the primary node communicate over standard WebSocket endpoints (e.g., `ws://localhost:8080/ws/node`). Message payloads represent structured JSON objects containing request types, transaction data, and event synchronization parameters. Extended messaging definitions can be found within the `websocket-protocol.md` reference file.

## Build

To compile, test, and package a standalone executable JAR, run the following command:

```bash
./mvnw clean package
```

Upon completion, the built executable `.jar` file will be deposited in the `target/` output directory. Good Luck
