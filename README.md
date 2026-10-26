# Task Management REST API

A small Java 17 REST API using Javalin, JDBC, and a relational database. The default local configuration uses an embedded H2 file database; Docker Compose runs the same API against PostgreSQL. The shared SQL schema is in `src/main/resources/schema.sql` and is applied automatically at startup.

## Requirements

- Java 17 or newer
- Maven 3.9 or newer
- Docker Desktop (optional, for PostgreSQL)

## Run locally with H2

```sh
mvn test
mvn package
java -jar target/task-management-api.jar
```

Open [http://localhost:7070](http://localhost:7070) for the task interface. The database is stored in `tasks-db` in the current directory. Override `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `PORT` to configure the connection. For an ephemeral H2 database, use `DB_URL=jdbc:h2:mem:tasks;DB_CLOSE_DELAY=-1`.

## Run with PostgreSQL

```sh
docker compose up --build
```

The API is available at [http://localhost:7070](http://localhost:7070); PostgreSQL data persists in the `postgres-data` volume. The default credentials are for local development only. Set different `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_USERNAME`, and `DB_PASSWORD` values before using this outside a local environment.

## Endpoints

All request and response bodies use JSON. Status values are `TODO`, `IN_PROGRESS`, and `DONE`; priority values are `LOW`, `MEDIUM`, and `HIGH`. New tasks default to `TODO` and `MEDIUM`. `due_date` is nullable and uses `YYYY-MM-DD`. A successful delete returns `204 No Content`. Errors use `{ "error": "...", "message": "..." }`.

Create a task:

```sh
curl -i http://localhost:7070/tasks -H "Content-Type: application/json" -d '{"title":"Prepare demo","description":"Show the task flow","status":"IN_PROGRESS","priority":"HIGH","due_date":"2026-11-01"}'
```

List tasks, with optional filters and pagination:

```sh
curl "http://localhost:7070/tasks?status=TODO&priority=HIGH&due_date_from=2026-11-01&due_date_to=2026-11-30&page=1&size=10"
```

The list response contains `items`, `page`, `size`, and `total`. Filters are optional; `page` defaults to 1 and `size` defaults to 20 (maximum 100).

Get one task:

```sh
curl http://localhost:7070/tasks/1
```

Replace a task (title is required; omitted status and priority reset to their defaults):

```sh
curl -X PUT http://localhost:7070/tasks/1 -H "Content-Type: application/json" -d '{"title":"Prepare final demo","description":"Add the API walkthrough","status":"DONE","priority":"MEDIUM","due_date":"2026-11-01"}'
```

Delete a task:

```sh
curl -i -X DELETE http://localhost:7070/tasks/1
```

## Database and tests

The JDBC DAO uses prepared statements and closes connections, statements, and result sets with try-with-resources. `schema.sql` uses identity columns, checks, and indexes supported by both H2 and PostgreSQL. DAO tests create isolated in-memory H2 databases and run with `mvn test`.