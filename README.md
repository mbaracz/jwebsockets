# jwebsockets

This project implements a WebSocket server using Netty, a high-performance, event-driven network application framework.
The server supports WebSocket connections and allows for easy configuration and customization.

## Features

- **High Performance**: Built on Netty for efficient handling of WebSocket connections.
- **Configurable**: Easily customizable to suit various use cases.
- **Asynchronous**: Leverages Netty's non-blocking I/O for scalability.
- **Protocol Support**: Full support for WebSocket protocol (RFC 6455).
- **Publish/Subscribe (Pub/Sub)**: Supports Pub/Sub messaging pattern for efficient message broadcasting.

## Getting Started

### Prerequisites

- Java 8 or higher
- Maven for dependency management


1. **Clone the repository**:

```sh
git clone https://github.com/mbaracz/jwebsockets.git
```

```sh
cd netty-websocket-server
```

2. **Build the project**:

```sh
mvn clean install
```

## Usage

### Generics

**WebSocketServer<T, D>** uses generic types, where **T** is the message object. It can be a string or your custom
Message class. **D** is the type of additional data associated with the WebSocket client. It can be passed, for example,
during upgrade, used with fetching some data from a database by token provided in the cookies.

The `WebSocketServer` constructor optionally takes a path where the endpoint should be available. It is **/** by
default.

### Configuration

Next, configure the server. You need to set a **MessageEncoder** and **MessageDecoder**. There are
**JsonMessageEncoder/JsonMessageDecoder** and **PlainTextMessageEncoder/PlainTextMessageEncoder** available by default.
If needed, you can implement your own encoder and decoder. The plain encoder/decoder can be accessed via the **INSTANCE** field from its class.

Now, the WebSocket server is ready to run, but you probably want to implement some event handlers.

### Events

You can bind events directly to the `WebSocketServer` by setting them via `onUpgrade`, `onOpen`, `onMessage`,
and `onClose`.

- **Upgrade handler**: Called before performing the handshake. You can implement your own logic and decide if the
  upgrade request should be handled.
- **Open handler**: Called after the handshake is done and the server is ready to exchange data with the client.

### Pub/sub:
The Publish/Subscribe pattern allows clients to subscribe to specific topics and receive messages broadcast to those topics. This is useful for applications where multiple clients need to receive the same messages, such as chat applications, live updates, and notifications.

To use the pub/sub functionality, you can subscribe, unsubscribe, and publish messages to topics as follows:

```java
WebSocketSession<T, D> session = ...; // obtain a WebSocketSession instance

// Subscribe to a topic
server.subscribe(session, "example-topic");

// Check if subscribed
boolean isSubscribed = server.isSubscribed(session, "example-topic");
System.out.println("Is subscribed: " + isSubscribed);

// Publish a message to the topic
server.publish("example-topic", "Hello, subscribers!");

// Unsubscribe from a topic
server.unsubscribe(session, "example-topic");

// Unsubscribe all sessions from all topics
server.unsubscribeAllTopics();
```

### SSL/TLS Support
To secure your WebSocket connections with SSL/TLS, configure the server to use SSL. This ensures that the data exchanged between the server and clients is encrypted.

```java
WebSocketServer<T, D> server = ...
        .configure(confiurer -> configurer.setSslContext(context))
        .listen(port);
```
### Example:
For a complete example demonstrating how to configure and start a WebSocket server with various event handlers, please refer to the [example directory](src/main/java/example).

## Contributing

We welcome contributions from the community to help make this project even better! Whether you're fixing bugs, adding
new features, or improving documentation, your efforts are greatly appreciated. Here’s how you can get started:

1. Fork the repository.
2. Create your feature branch (`git checkout -b feature/fooBar`).
3. Commit your changes (`git commit -am 'Add some fooBar'`).
4. Push to the branch (`git push origin feature/fooBar`).
5. Create a new Pull Request.

## License

This project is licensed under the GPL-2.0 License - see the [LICENSE](LICENSE) file for details.