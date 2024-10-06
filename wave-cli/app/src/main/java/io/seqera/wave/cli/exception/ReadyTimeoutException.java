/*
 * Copyright 2023, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.cli.exception;

/**
 * Exception thrown when a container do not reach a ready status with the max expected time
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ReadyTimeoutException extends RuntimeException {

    public ReadyTimeoutException(String message) {
        super(message);
    }

    public ReadyTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

}
