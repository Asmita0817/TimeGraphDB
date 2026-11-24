# TimeGraph DB: A Version-Controlled Relational Database

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.2](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**TimeGraph DB** is a full-stack, version-controlled relational database engine built in **Java 21** and **Spring Boot 3**. Inspired by the Git object model and content-addressable storage (CAS), TimeGraph DB treats database states as immutable snapshots organized in a Directed Acyclic Graph (DAG) of commits.

It supports relational SQL queries, instantaneous zero-copy branching, time-travel queries across historical snapshots, structured diff inspections, and robust 3-way branch merges with automatic conflict detection.

---

## 🌟 Key Architecture & Highlights

### 1. Content-Addressable Storage (CAS)
Every entity stored in TimeGraph DB is an immutable object whose identity is derived from its SHA-1 hash (`java.security.MessageDigest`):
* **`RowBlob`**: Serialized database record keyed by primary key. Identical row data produces identical SHA-1 hashes, providing **automatic row-level deduplication**.
* **`TableTree`**: Stores the table name, column definitions (`INT`, `VARCHAR`, `DOUBLE`, `BOOLEAN`), and a deterministic `TreeMap<String, String>` mapping `PrimaryKey -> RowBlob SHA-1`.
* **`DatabaseSnapshot` (Root Tree)**: Represents the entire database state at a commit by mapping `TableName -> TableTree SHA-1`.
* **`CommitNode`**: A DAG node storing `hash`, `parentHashes` (1 parent for linear commits, 2 parents for merge commits), `snapshotHash`, `timestamp`, `author`, and `message`.
* **`ObjectStore`**: Persists immutable objects under `.timegraph/objects/<2-char-prefix>/<remaining-hash>` using standard Java NIO (`java.nio.file`).

### 2. Version Control, Commit DAG & 3-Way Merge Engine
* **Repository State**: Managed under `.timegraph/` containing `HEAD` pointer file, `refs/heads/<branch>` files, and `objects/` directory.
* **Lowest Common Ancestor (LCA)**: BFS traversal through ancestor sets to find the exact branch point between two divergent branches.
* **3-Way Merge Algorithm**: Compares Base (LCA), Ours (Current branch HEAD), and Theirs (Target branch HEAD) row-by-row:
  - If a row is modified in only one branch, changes are auto-merged cleanly.
  - If both branches modify the same primary key differently, a structured `MergeConflictException` is raised with detailed Base vs Ours vs Theirs comparison data.
* **Fast Diff Engine**: Compares row hashes across any two commits or branches to identify `ADDED (+)` rows, `DELETED (-)` rows, and `CHANGED (~)` rows with old and new values.

### 3. SQL & Time-Travel Query Engine
* Implemented using the **Command Design Pattern** (`Command` interface with concrete classes).
* Full support for relational operations:
  - `CREATE TABLE <name> (col1 TYPE [PRIMARY KEY], ...)`
  - `UPSERT INTO <name> (col1, ...) VALUES (val1, ...)`
  - `DELETE FROM <name> WHERE <pkCol> = <val>`
  - `SELECT * FROM <name>`
* **Time-Travel Queries**:
  - `SELECT * FROM <table> AS_OF <commitHash>`: Queries any historical commit snapshot without mutating current HEAD or working directory state.

### 4. Interactive Developer Workbench
* Embedded single-page dashboard at `http://localhost:8080/`.
* Visual Commit DAG with clickable nodes that instantly trigger time-travel snapshot views.
* Interactive SQL & VCS console with pre-configured quick buttons.
* Real-time visual Diff Viewer (`+`, `-`, `~`) and 3-Way Conflict Resolver matrix.

---

## 🚀 Quickstart: Running Locally

### Prerequisites
* **Java 21** or higher
* **Maven 3.9+**

### 1. Clone & Build
```bash
git clone https://github.com/your-username/TimeGraphDB.git
cd TimeGraphDB
mvn clean package -DskipTests
```

### 2. Run the Spring Boot App
```bash
mvn spring-boot:run
```
Or execute the packaged JAR:
```bash
java -jar target/timegraph-db-1.0.0.jar
```

### 3. Open the Dashboard
Navigate to `http://localhost:8080` in your browser.

> **Note**: On startup, TimeGraph DB automatically seeds a realistic sample `employees` table across two branches (`main` and `salary-hike`) so you can explore commits, diffs, and 3-way merges immediately!

---

## 🐳 Docker Deployment

A multi-stage `Dockerfile` is included for containerized deployment:

### Build Docker Image
```bash
docker build -t timegraph-db:latest .
```

### Run Docker Container
```bash
docker run -p 8080:8080 -e PORT=8080 timegraph-db:latest
```

### Cloud Deployment (Render / Railway)
1. Push this repository to GitHub.
2. In **Render** or **Railway**, create a new **Web Service**.
3. Select **Docker** environment.
4. Render automatically supplies the `$PORT` environment variable, which the Dockerfile ENTRYPOINT passes to Spring Boot:
   ```dockerfile
   ENTRYPOINT ["sh", "-c", "java -XX:+UseG1GC -XX:MaxRAMPercentage=75 -Dserver.port=${PORT:-8080} -jar app.jar"]
   ```

---

## 🛠️ Supported Commands Reference

You can execute any of the following statements via the web console or REST API:

| Command | Example | Description |
|---|---|---|
| `SELECT` | `SELECT * FROM employees` | Queries current working snapshot |
| `SELECT AS_OF` | `SELECT * FROM employees AS_OF <commitHash>` | **Time-travel** query on historical snapshot |
| `UPSERT` | `UPSERT INTO employees (id, name, department, salary, active) VALUES (5, 'Evan', 'Design', 82000.0, true)` | Inserts or updates row by primary key |
| `DELETE` | `DELETE FROM employees WHERE id = 5` | Deletes row by primary key |
| `CREATE TABLE` | `CREATE TABLE projects (id INT PRIMARY KEY, name VARCHAR, budget DOUBLE)` | Creates new table schema |
| `COMMIT` | `COMMIT "Update engineering salaries"` | Commits staged changes to DAG |
| `BRANCH` | `BRANCH feature-bonus` | Creates a new branch at current HEAD |
| `CHECKOUT` | `CHECKOUT salary-hike` | Switches active branch and loads its snapshot |
| `DIFF` | `DIFF main salary-hike employees` | Computes row differences between two commits/branches |
| `MERGE` | `MERGE salary-hike` | Performs a 3-way merge into current branch |
| `LOG` | `LOG` | Displays commit DAG history backwards from HEAD |
| `SHOW TABLES` | `SHOW TABLES` | Lists tables in active snapshot |
| `STATUS` | `STATUS` | Reports active branch, HEAD hash, and staged status |

---

## 🔌 REST API Documentation

### `GET /api/state`
Returns the current database and VCS state:
```json
{
  "currentBranch": "main",
  "headCommitHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4",
  "isDirty": false,
  "branches": ["main", "salary-hike"],
  "commits": [ ... ],
  "tables": { ... }
}
```

### `POST /api/execute`
Executes SQL or VCS statements:
* **Request**:
  ```json
  {
    "command": "DIFF main salary-hike employees"
  }
  ```
* **Response**:
  ```json
  {
    "status": "SUCCESS",
    "message": "Found 4 diff entry/entries...",
    "diffEntries": [
      {
        "type": "CHANGED",
        "tableName": "employees",
        "primaryKey": "1",
        "oldValues": { "salary": 95000.0, "name": "Alice Smith" },
        "newValues": { "salary": 110000.0, "name": "Alice Smith" }
      }
    ]
  }
  ```

### `POST /api/reset`
Resets the repository and re-seeds the default demo branches and commits.

---

## 🧪 Testing the 3-Way Merge

1. Start on `main`: Notice employees `Frank Castle` exists, `Bob` has salary 76000.
2. Checkout `salary-hike`: Notice `Alice` and `Charlie` have higher salaries, and `Evan Wright` was hired.
3. Switch back to `main`: `CHECKOUT main`.
4. Run `DIFF main salary-hike employees` to see all additions and changes.
5. Run `MERGE salary-hike`: The engine computes the LCA, verifies no overlapping conflicting modifications on the same PK, and merges cleanly creating a 2-parent merge commit!
