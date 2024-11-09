package io.seqera.wave.util

import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SimplePoolTest extends Specification {

    static class MyObject {

    }

    def 'should borrow the same object' () {
        given:
        def pool = new SimplePool(()-> new MyObject())

        when:
        def o1 = pool.borrow()
        pool.release(o1)
        and:
        def o2 = pool.borrow()
        pool.release(o2)
        then:
        // is the same object
        o1.is(o2)
    }

    def 'should borrow a new object' () {
        given:
        def pool = new SimplePool(()-> new MyObject())

        when:
        def o1 = pool.borrow()
        and:
        def o2 = pool.borrow()
        then:
        // is not the same object
        !o1.is(o2)
    }

    def 'should apply a function and use the same object' () {
        given:
        def objects = new HashSet()
        and:
        def pool = new SimplePool(()-> new MyObject())

        expect:
        // the first time is added to the `objects` set
        pool.apply( (it) -> { assert objects.add(it); return it } ) instanceof MyObject
        and:
        // the following invocations use the same instance, the object is not added anymore
        // to the set because it already contains it
        pool.apply( (it) -> { assert !objects.add(it); return it } ) instanceof MyObject
        pool.apply( (it) -> { assert !objects.add(it); return it } ) instanceof MyObject
        and:
        objects.size()==1
    }


    def 'should apply a function and use a new object instance' () {
        given:
        def objects = new HashSet()
        def started = new CompletableFuture()
        and:
        def pool = new SimplePool(()-> new MyObject())

        when:
        // the first time is added to the `objects` set
        def thread = Thread.start { pool.apply( (obj) -> { started.complete('yes'); sleep 300; assert objects.add(obj); return obj } )  }
        and:
        started.join()
        then:
        // the following invocations use a same instance, because the long running thread keep using the other instance
        pool.apply( (it) -> { assert objects.add(it); return it } ) instanceof MyObject

        when:
        thread.join()
        then:
        objects.size()==2
    }

}
