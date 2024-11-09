package io.seqera.wave.util

import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Function
import java.util.function.Supplier

import groovy.transform.CompileStatic
/**
 * Implement a simple object pool retaining objects using a {@link SoftReference}.
 *
 * The pool is meant to be created specifying a {@link Supplier} function as argument to
 * the {@link SimplePool} constructor to instantiate pooled objects.
 *
 * Pool object need to be accessed by {@link SimplePool#borrow()} and release
 * once used via {@link SimplePool#release(Object)}.
 *
 * Alternatively a function can be applied to a pooled object by using {@link SimplePool#apply(Function)}
 * method which takes care of borrowing an object and releasing it automatically when done. If the pool
 * is empty a new object is created implicitly and returned to the pool on completion.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class SimplePool<T> {

    // A thread-safe queue to hold SoftReferences to pooled objects
    private final Queue<SoftReference<T>> pool = new ConcurrentLinkedQueue<>()

    // Factory for creating new objects
    private final Supplier<T> factory

    /**
     * Create a pool with the given supplier function. The supplier behave as factory
     * to instantiate new object instances when the pool is empty.
     *
     * @param factory A {@link Supplier} function that creates instances of the pooled objects.
     */
    SimplePool(Supplier<T> factory) {
        this.factory = factory
    }

    /**
     * Borrow an object from the pool. The object is expected to be returned by using the {@link #release(Object)}
     * method.
     *
     * @return The object instance taken from the pool or a new instance when the pool is empty.
     */
    T borrow() {
        // Attempt to retrieve a non-collected object from the pool
        T result
        while (!pool.isEmpty()) {
            SoftReference<T> ref = pool.poll()
            if( ref!=null && (result=ref.get())!=null ) {
                return result
            }
        }

        // If no valid object was found, create a new one
        return factory.get()
    }

    /**
     * Return an object to the pool.
     *
     * @param obj The object to be returned
     */
    void release(T obj) {
        if (obj != null) {
            pool.offer(new SoftReference<>(obj))
        }
    }

    /**
     * Apply the specified function to an object in the pool or create a new object if needed. Once
     * the function has been carried out the object is automatically returned to the pool.
     *
* This method is a shortcut to the {@link #borrow()}, operation and {@link #release} pattern
     *
     * @param function A {@link Function} to be applied to the pooled object.
     * @return The value returned by the {@link Function} execution.
     */
    <R> R apply(Function<T,R> function) {
        final object = borrow()
        try {
            return function.apply(object)
        }
        finally {
            release(object)
        }
    }

}
