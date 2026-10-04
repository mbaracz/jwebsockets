# Autobahn conformance tests

Checks the WebSocket protocol implementation using
the [Autobahn WebSocket Testsuite](https://github.com/crossbario/autobahn-testsuite).

The suite runs in Docker as a client (`fuzzingclient`) and connects to `AutobahnTestServer` (
`jwebsockets-core/src/test/java/pl/mbaracz/jwebsockets`), an echo server that sends every message back with the same payload
and frame type: text as text and binary as binary.

These tests are not run by `mvn test`. Run them manually when needed.

## Requirements

- Java 25 and Maven
- Docker with Docker Compose

## Running

1. Start the test server on port 9001 from the project root, or run `AutobahnTestServer` directly from the IDE:

   ```sh
   mvn -pl jwebsockets-core test-compile org.codehaus.mojo:exec-maven-plugin:3.6.4:java \
       -Dexec.mainClass=pl.mbaracz.jwebsockets.AutobahnTestServer \
       -Dexec.classpathScope=test
   ```

   It prints `jwebsockets listening on :9001` when ready.

2. In another terminal, run the test suite:

   ```sh
   cd autobahn
   docker compose up --abort-on-container-exit
   ```

3. Open `reports/servers/index.html` to inspect the results, then stop the test server with Ctrl+C.

## Configuration

`config/fuzzingclient.json` runs all cases except:

- `9.*`: limits and performance. These cases are slow and send messages of up to 16 MiB, which exceeds the default
  `maxMessageSize` of 1 MiB.
- `12.*` and `13.*`: compression (`permessage-deflate`), which jwebsockets does not currently implement.

The container reaches the host server through `host.docker.internal`.

Docker Desktop provides this hostname on macOS and Windows. `docker-compose.yml` maps it to the host gateway so the same
configuration also works on Linux.

## Reports

Generated reports are ignored by Git.

On Linux, the container may create them as root. To remove generated reports, run:

```sh
docker compose run --rm autobahn rm -rf /reports/servers
```