# lib-pairing

Credential federation library for secure RSA key exchange between services.

## Overview

lib-pairing provides a secure mechanism for establishing cryptographic key pairs between Wave and external services (like Tower) to enable encrypted credential sharing. It implements:

- RSA key pair generation and caching with configurable expiration
- WebSocket-based bidirectional communication for real-time messaging
- HTTP request/response proxying over WebSocket connections
- Distributed state management supporting both Redis and local (in-memory) backends

## Installation

Add this dependency to your `build.gradle`:

```gradle
dependencies {
    implementation project(':lib-pairing')
}
```

Or when published to Maven:

```gradle
dependencies {
    implementation 'io.seqera:lib-pairing:1.0.0'
}
```

## Configuration

The library uses a `PairingConfig` interface that must be implemented by the consuming application.
This allows the application to provide configuration values via its own mechanism (e.g., Micronaut's `@Value` or `@ConfigurationProperties`).

### PairingConfig Interface

```groovy
interface PairingConfig {
    Duration getKeyLease()                      // Key validity before renewal (default: 1d)
    Duration getKeyDuration()                   // Key cache TTL (default: 30d)
    Duration getChannelTimeout()                // Inbound message timeout (default: 5s)
    Duration getChannelAwaitTimeout()           // Queue poll interval (default: 100ms)
    boolean getCloseSessionOnInvalidLicenseToken() // Close session on invalid license (default: false)
    List<String> getDenyHosts()                 // Hosts to deny pairing (default: [])
}
```

### Example Implementation (Micronaut)

```groovy
@Singleton
class PairingConfigImpl implements PairingConfig {
    @Value('${wave.pairing-key.lease:`1d`}')
    Duration keyLease

    @Value('${wave.pairing-key.duration:`30d`}')
    Duration keyDuration

    @Value('${wave.pairing.channel.timeout:5s}')
    Duration channelTimeout

    @Value('${wave.pairing.channel.awaitTimeout:100ms}')
    Duration channelAwaitTimeout

    @Value('${wave.closeSessionOnInvalidLicenseToken:false}')
    boolean closeSessionOnInvalidLicenseToken

    @Nullable
    @Value('${wave.denyHosts}')
    List<String> denyHosts

    @Override List<String> getDenyHosts() { denyHosts ?: [] }
}
```

### YAML Configuration

```yaml
wave:
  pairing-key:
    lease: '1d'        # How long before a key is considered expired and needs renewal
    duration: '30d'    # How long keys stay in the cache store before eviction
  pairing:
    channel:
      timeout: '5s'        # Timeout for inbound message futures
      awaitTimeout: '100ms' # Poll interval for queue consumption
  denyHosts: []            # Optional list of hosts to deny pairing
  closeSessionOnInvalidLicenseToken: false
```

## Usage

### Core Service

```groovy
@Inject PairingService pairingService

// Get or create a pairing record for a service endpoint
def response = pairingService.acquirePairingKey("tower", "https://tower.example.com")
// Returns: PairingResponse with pairingId and base64-encoded publicKey

// Retrieve an existing pairing record
def record = pairingService.getPairingRecord("tower", "https://tower.example.com")
// Returns: PairingRecord with service, endpoint, pairingId, privateKey, publicKey, expiration
```

### WebSocket Channel

The library exposes a WebSocket endpoint at `/pairing/{service}/token/{token}{?endpoint}` that handles:

- Client registration and key exchange
- Heartbeat messages for connection liveness
- HTTP request/response proxying between Wave and connected clients

### License Validation (Optional)

Implement the `LicenseValidator` interface to integrate with your license management system:

```groovy
@Singleton
class MyLicenseValidator implements LicenseValidator {
    @Override
    LicenseCheckResult checkToken(String token, String product) {
        // Your license validation logic
        return new LicenseCheckResult(id: licenseId, expiration: expirationTime)
    }
}
```

## Architecture

### Key Components

- **PairingConfig** - Configuration interface (must be implemented by consuming application)
- **PairingService** - Interface for acquiring and retrieving pairing keys
- **PairingServiceImpl** - Implementation using RSA key generation and caching
- **PairingStore** - Distributed cache store for PairingRecord objects
- **PairingWebSocket** - WebSocket server endpoint for client connections
- **PairingChannel** - Message routing between clients and Wave instances
- **PairingOutboundQueue** - Distributed queue for outbound messages
- **PairingInboundStore** - Distributed store for inbound message futures
- **LicenseValidator** - Optional interface for license validation (e.g., LicenseMan integration)

### Message Types

- **PairingResponse** - Initial response with pairingId and publicKey
- **PairingHeartbeat** - Keep-alive heartbeat messages
- **ProxyHttpRequest** - HTTP requests forwarded through WebSocket
- **ProxyHttpResponse** - HTTP responses returned through WebSocket

## Dependencies

- `io.seqera:lib-crypto` - RSA key generation
- `io.seqera:lib-random` - Random ID generation
- `io.seqera:lib-data-store-state-redis` - Redis-backed state storage
- `io.seqera:lib-data-store-future-redis` - Redis-backed future storage
- `io.seqera:lib-data-queue-redis` - Redis-backed message queue
- `io.seqera:lib-serde-moshi` - JSON serialization
- Micronaut WebSocket for WebSocket support

## Testing

```bash
./gradlew :lib-pairing:test
```

## License

Apache License 2.0
