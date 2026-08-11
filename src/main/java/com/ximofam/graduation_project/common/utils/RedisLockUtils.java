package com.ximofam.graduation_project.common.utils;

import com.ximofam.graduation_project.common.exceptions.http.InternalException;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

public class RedisLockUtils {

    private RedisLockUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static void tryLockOrThrow(RLock lock) {
        boolean acquired = lock.tryLock();
        if (!acquired) {
            throw new InternalException("Could not acquire lock.");
        }
    }

    public static void tryLockOrThrow(RLock lock, long waitTime, TimeUnit unit) {
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalException("Lock interrupted.");
        }
        if (!acquired) {
            throw new InternalException("Could not acquire lock.");
        }
    }

    public static void tryLockOrThrow(RLock lock, long waitTime, long leaseTime, TimeUnit unit) {
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalException("Lock interrupted.");
        }
        if (!acquired) {
            throw new InternalException("Could not acquire lock.");
        }
    }
}
