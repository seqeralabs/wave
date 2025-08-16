# wave-api

API models and DTOs for the Wave container service.

## Installation

Add this dependency to your `build.gradle`:

```gradle
dependencies {
    implementation 'io.seqera:wave-api:0.16.0'
}
```

## Usage

The Wave API models provide type-safe data structures for REST API communication:

```groovy
@Inject 
WaveClient waveClient

def request = new SubmitContainerTokenRequest(
    containerImage: 'ubuntu:latest',
    containerConfig: new ContainerConfig(
        env: ['FOO=bar'],
        workingDir: '/workspace'
    )
)

def response = waveClient.submitContainerToken(request)
println "Container token: ${response.containerToken}"
```

## Testing

```bash
./gradlew :wave-api:test
```