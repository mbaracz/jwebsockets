# jwebsockets

Tiny production-ready WebSocket server for Java, powered by Netty.

## Quick Start

Add the core dependency to your project:

```xml

<dependency>
    <groupId>pl.mbaracz</groupId>
    <artifactId>jwebsockets-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Start an echo server:

```java
var server = new WebSocketServer<String, Void>("/chat")
    .configure(config -> config
        .setMessageDecoder(PlainTextMessageDecoder.INSTANCE)
        .setMessageEncoder(PlainTextMessageEncoder.INSTANCE)
    )
    .onMessage((session, message) ->
        session.sendMessage("echo: " + message)
    )
    .listen(8080);
```

Connect to:

```text
ws://localhost:8080/chat
```

That's it. No framework, container or application server required.

## Why jwebsockets?

- Netty-based
- Typed messages and session context
- Built-in pub/sub
- Heartbeat and idle timeout
- Backpressure handling
- TLS and permessage-deflate
- Graceful shutdown
- RFC 6455, Autobahn-tested

## Conformance

jwebsockets is tested with the [Autobahn WebSocket Testsuite](https://github.com/crossbario/autobahn-testsuite)
in the [conformance workflow](.github/workflows/conformance.yml).

| Result        | Cases |
|---------------|------:|
| OK            |   386 |
| NON-STRICT    |     2 |
| INFORMATIONAL |     3 |
| FAILED        |     0 |

The supported test set excludes group 9 cases requiring messages above the default 1 MiB message limit, and cases
13.3-13.6 requiring `server_max_window_bits`, which the current compression implementation does not support.

## JSON, Authentication & Pub/Sub

For JSON support, add the Jackson extension:

```xml

<dependency>
    <groupId>pl.mbaracz</groupId>
    <artifactId>jwebsockets-jackson</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Configure the Jackson codec, add authentication, and use the pub/sub features:

```java
var server = new WebSocketServer<ChatMessage, User>("/chat")
    .configure(config -> config
        .setMessageDecoder(new JsonMessageDecoder<>(ChatMessage.class))
        .setMessageEncoder(new JsonMessageEncoder<>())
    )
    .onUpgrade((request, response) ->
        UpgradeResult.accept(authenticate(request))
    )
    .onMessage((session, message) ->
        server.publish("chat", message)
    );
```
