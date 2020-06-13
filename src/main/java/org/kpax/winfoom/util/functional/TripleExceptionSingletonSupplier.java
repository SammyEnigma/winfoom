package org.kpax.winfoom.util.functional;

import org.springframework.util.Assert;


/**
 * A {@link TripleExceptionSupplier} decorator that caches a singleton value and
 * makes it available from {@link #get()}.
 * <p>The provided {@link TripleExceptionSupplier} must not return {@code null}.
 * <p>It is also possible to refresh the stored value (see {@link #reset()} method).
 * <p>Note: Refreshing might break the null safety of {@link #get()} method in a multi-threaded environment.
 *
 * @param <T>  the type of value supplied by this supplier
 * @param <E1> the first {@link Exception} type
 * @param <E2> the second {@link Exception} type
 * @param <E3> the third {@link Exception} type
 */
public class TripleExceptionSingletonSupplier<T, E1 extends Exception, E2 extends Exception, E3 extends Exception> implements TripleExceptionSupplier<T, E1, E2, E3> {

    /**
     * For thread safety purposes.
     */
    private final Object LOCK = new Object();

    /**
     * The {@link java.util.function.Supplier} used to initialize the value.
     */
    private final TripleExceptionSupplier<T, E1, E2, E3> supplier;

    /**
     * The cached value.
     */
    private volatile T t;

    /**
     * Constructor.
     *
     * @param supplier the not null supplier
     */
    public TripleExceptionSingletonSupplier(TripleExceptionSupplier<T, E1, E2, E3> supplier) {
        Assert.notNull(supplier, "supplier cannot be null");
        this.supplier = supplier;
    }

    /**
     * Get the cached value (if any) otherwise a new value is created in a thread safe manner.
     *
     * @return the value
     */
    public T get() throws E1, E2, E3 {
        if (t == null) {
            synchronized (LOCK) {
                if (t == null) {
                    t = supplier.get();
                    Assert.state(t != null, "No instance from supplier");
                }
            }
        }
        return t;
    }

    /**
     * Nullify the value in a thread safe manner.
     */
    public void reset() {
        synchronized (LOCK) {
            t = null;
        }
    }

    /**
     * Check whether the value is not null.
     *
     * @return {@code true} iff the value is not null
     */
    public boolean hasValue() {
        return t != null;
    }

}
