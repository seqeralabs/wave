package io.seqera.wave.api;

import java.time.Instant;


/**
 * Model a response for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class SubmitContainerTokenResponse {
    public String containerToken;

    public String targetImage;

    public Instant expiration;

    public SubmitContainerTokenResponse() { }

    public SubmitContainerTokenResponse(String token, String target, Instant expiration) {
        this.containerToken = token;
        this.targetImage = target;
        this.expiration = expiration;
    }
}
