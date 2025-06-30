# Heimdall

A demo project for Spring Boot that provides real-time monitoring and service management capabilities.

## Features

*   **Real-Time Communication:** Utilizes Spring WebSockets for real-time communication between the server and clients.
*   **Service Discovery:** Integrates with Nacos for dynamic service discovery and configuration management.
*   **High-Performance RPC:** Uses gRPC for efficient and high-performance remote procedure calls.
*   **Web Interface:** Provides a web interface for monitoring and interacting with the services.

## Prerequisites

*   Java 17 or higher
*   Maven 3.6 or higher
*   A running Nacos server

## Getting Started

### Installation

1.  Clone the repository:
    ```bash
    git clone https://github.com/your-username/heimdall.git
    ```
2.  Navigate to the project directory:
    ```bash
    cd heimdall
    ```

### Configuration

Before running the application, you need to configure the Nacos server address. Create a file named `application.properties` in the `src/main/resources` directory and add the following line:

```properties
spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848
```

Replace `127.0.0.1:8848` with the actual address of your Nacos server.

### Build and Run

To build and run the application, use the following Maven command:

```bash
mvn spring-boot:run
```

The application will start on the default port `8080`.

## Available Scripts

*   `mvn clean install`: Builds the project and creates a JAR file in the `target` directory.
*   `mvn spring-boot:run`: Runs the application in development mode.
*   `mvn test`: Runs the unit tests.

## Dependencies

*   **Spring Boot:** A framework for creating stand-alone, production-grade Spring-based applications.
*   **Spring Web:** A module for building web applications, including RESTful applications.
*   **Spring WebSockets:** A module for building WebSocket-based applications.
*   **Nacos Client:** A client for Nacos, a platform for dynamic service discovery, configuration, and service management.
*   **gRPC:** A high-performance, open-source universal RPC framework.

## Contributing

Contributions are welcome! Please feel free to open an issue or submit a pull request.

## License

This project is licensed under the MIT License.
