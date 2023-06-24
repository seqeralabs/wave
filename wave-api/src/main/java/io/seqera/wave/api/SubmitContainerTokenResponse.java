package io.seqera.wave.api;

import java.time.Instant;


/**
 * Model a response for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class SubmitContainerTokenResponse {

    /**
     * A unique authorization token assigned to this request
     */
    public String containerToken;

    /**
     * The fully qualified wave container name to be used
     */
    public String targetImage;

    /**
     * The time instant when the container token is going to expire
     */
    public Instant expiration;

    /**
     * The source container image that originated this request
     */
    public String containerImage;

    /**
     * The ID of the build associated with this request or null of the image already exists
     */
    public String buildId;

    public SubmitContainerTokenResponse() { }

    public SubmitContainerTokenResponse(String token, String target, Instant expiration, String containerImage, String buildId) {
        this.containerToken = token;
        this.targetImage = target;
        this.expiration = expiration;
        this.containerImage = containerImage;
        this.buildId = buildId;
    }
}
