package io.seqera.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a copy-on-read input stream that copies the content
 * of a source input stream to a target outputstream while reading it
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class TapInputStreamFilter extends FilterInputStream {

    private static final Logger log = LoggerFactory.getLogger(TapInputStreamFilter.class);

    final private OutputStream target;

    final private Runnable finalizer;

    public TapInputStreamFilter(InputStream source, OutputStream target) {
        super(source);
        this.target = target;
        this.finalizer = null;
    }

    public TapInputStreamFilter(InputStream source, OutputStream target, Runnable finalizer) {
        super(source);
        this.target = target;
        this.finalizer = finalizer;
    }

    @Override
    public int read() throws IOException {
        final int result = super.read();
        target.write(result);
        return result;
    }

    @Override
    public int read(byte[] buffer, int off, int len) throws IOException {
        final int ret = super.read(buffer, off, len);
        if( ret>0 )
            target.write(buffer, off, ret);
        return ret;
    }

    @Override
    public long skip(long n) throws IOException {
        throw new IOException("Skip not supported by TapInputStreamFilter");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void close() throws IOException {
        target.close();
        super.close();
        if( finalizer != null){
            finalizer.run();
        }
    }
}
