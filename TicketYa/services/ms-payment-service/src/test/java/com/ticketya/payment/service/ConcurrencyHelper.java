package com.ticketya.payment.service;

import com.ticketya.payment.exception.PaymentException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;

/** Lanza N hilos a la vez y cuenta resultados ("OK" o el code del error de negocio). */
final class ConcurrencyHelper {

    private ConcurrencyHelper() {}

    static Map<String, Integer> race(int threads, IntConsumer action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        action.accept(index);
                        return "OK";
                    } catch (PaymentException e) {
                        return e.getCode();
                    }
                }));
            }
            ready.await();
            go.countDown();
            Map<String, Integer> result = new TreeMap<>();
            for (Future<String> f : futures) {
                result.merge(f.get(), 1, Integer::sum);
            }
            return result;
        } finally {
            pool.shutdownNow();
        }
    }
}
