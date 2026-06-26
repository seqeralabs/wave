package io.seqera.wave.tower

import spock.lang.Specification

class UserTest extends Specification {

    def 'waveBuildNotification is null when not set'() {
        given:
        def user = new User(id: 1L, userName: 'alice', email: 'alice@example.com')
        expect:
        user.waveBuildNotification == null
    }

    def 'waveBuildNotification stores enum value'() {
        given:
        def user = new User(id: 1L, userName: 'alice', email: 'alice@example.com',
                waveBuildNotification: WaveBuildNotification.ON_ERROR)
        expect:
        user.waveBuildNotification == WaveBuildNotification.ON_ERROR
    }
}
