package io.seqera.wave.tower

import spock.lang.Specification

class WaveBuildNotificationTest extends Specification {

    def 'should define three constants'() {
        expect:
        WaveBuildNotification.values().length == 3
        WaveBuildNotification.valueOf('ALWAYS_ON')  == WaveBuildNotification.ALWAYS_ON
        WaveBuildNotification.valueOf('ON_ERROR')   == WaveBuildNotification.ON_ERROR
        WaveBuildNotification.valueOf('ALWAYS_OFF') == WaveBuildNotification.ALWAYS_OFF
    }

    def 'defaultValue returns ALWAYS_ON'() {
        expect:
        WaveBuildNotification.defaultValue() == WaveBuildNotification.ALWAYS_ON
    }
}
