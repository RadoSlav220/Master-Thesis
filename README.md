# Master-Thesis — Digital Twin Orchestration Platform

A Spring Boot service that orchestrates external Digital Twin / ML components. It
exposes REST endpoints to register **components** and **datasets**, and to trigger
**executions** that bind a dataset to a component.

## Prerequisites

- **Java 25** (JDK)
- **Maven 3.9+**
- **PostgreSQL 16** running on `localhost:5432`

## Database setup

The app expects a database named `orchestrator`. Create it once:

```sql
CREATE DATABASE orchestrator;
```

Tables are created automatically on first boot (Hibernate `ddl-auto: update`).

## Configuration

Datasource settings resolve from environment variables, with local-dev defaults
in `application.yml`:

| Variable      | Default                                              |
|---------------|------------------------------------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/orchestrator`      |
| `DB_USERNAME` | `postgres`                                           |
| `DB_PASSWORD` | *(empty)*                                            |

Credentials are **not** committed. Provide the password in one of two ways:

**Option A — local profile file** (`src/main/resources/application-local.yml`, git-ignored):

```yaml
spring:
  datasource:
    username: postgres
    password: your-password
```

**Option B — environment variables:**

```bash
export DB_PASSWORD=your-password
```

## Running

With the local profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Or with environment variables set:

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. Startup is confirmed by the
`Started OrchestratorApplication` log line.

## API

Each resource supports create (`POST`), list (`GET`), and fetch-by-id (`GET /{id}`).

| Resource     | Base path      |
|--------------|----------------|
| Components   | `/components`  |
| Datasets     | `/datasets`    |
| Executions   | `/executions`  |

### Example

```bash
# Create a component
curl -X POST http://localhost:8080/components \
  -H "Content-Type: application/json" \
  -d '{"name":"anomaly-detector","endpointUrl":"http://localhost:9000/run","description":"demo"}'

# Create a dataset
curl -X POST http://localhost:8080/datasets \
  -H "Content-Type: application/json" \
  -d '{"name":"sensor-readings","type":"timeseries","description":"demo"}'

# Trigger an execution (use the ids returned above)
curl -X POST http://localhost:8080/executions \
  -H "Content-Type: application/json" \
  -d '{"datasetId":"<dataset-id>","componentId":"<component-id>"}'

# List executions
curl http://localhost:8080/executions
```

## Executing components

The platform is an **orchestration layer**: it does not run analytical models
itself. A registered component points (via `endpointUrl`) at an external REST
service. Triggering an execution invokes that service **synchronously**, stores
the returned JSON in the execution's `result`, and sets the status.

Execution lifecycle: `RUNNING` → `COMPLETED` (result stored) or `FAILED`
(`errorMessage` stored). A failed invocation is still persisted and returned with
HTTP `201` — the failure is a recorded outcome, not a request error.

### Mock components

Two in-app endpoints simulate future external services, so the flow is demoable
without deploying a real model. Each accepts `POST { "datasetId": "<uuid>" }`.

| Component    | Endpoint                                       | Metric       |
|--------------|------------------------------------------------|--------------|
| Air Quality  | `POST /mock-components/air-quality`            | `pm25`       |
| Traffic      | `POST /mock-components/traffic`                | `congestion` |

### End-to-end example

```bash
# 1. Create a dataset -> note its id
curl -X POST http://localhost:8080/datasets \
  -H "Content-Type: application/json" \
  -d '{"name":"sofia-sensors","type":"timeseries","description":"demo"}'

# 2. Register a component pointing at a mock endpoint -> note its id
curl -X POST http://localhost:8080/components \
  -H "Content-Type: application/json" \
  -d '{"name":"Air Quality","endpointUrl":"http://localhost:8080/mock-components/air-quality","description":"mock"}'

# 3. Execute -> returns a COMPLETED execution with the component result
curl -X POST http://localhost:8080/executions \
  -H "Content-Type: application/json" \
  -d '{"datasetId":"<dataset-id>","componentId":"<component-id>"}'

# 4. Retrieve the stored result
curl http://localhost:8080/executions/<execution-id>
```

Swap the `endpointUrl` for `.../mock-components/traffic` to run the Traffic component.
