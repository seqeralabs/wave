package io.seqera.wave.service.stream


import groovy.transform.CompileStatic
import io.micronaut.core.io.buffer.ByteBuffer
import reactor.core.publisher.Flux

@CompileStatic
class FluxToInputStream extends InputStream {

    private final Flux<ByteBuffer> flux;
    private ByteBuffer currentBuffer;

    FluxToInputStream(Flux<ByteBuffer> flux) {
        this.flux = flux;
    }

    @Override
    int read() throws IOException {
        if (currentBuffer == null || currentBuffer.readableBytes()==0 ) {
            currentBuffer = flux.blockFirst(); // Blocking call to get the next ByteBuffer
            if (currentBuffer == null) {
                return -1; // End of stream
            }
        }
        return currentBuffer.read() & 0xFF;
    }

    @Override
    void close() throws IOException {
        // Implement any necessary cleanup, if required
    }
}
