package com.fan.lazyday.infrastructure.helper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class ReactiveHelper {
    private ReactiveHelper() throws IllegalAccessException {
        throw new IllegalAccessException();
    }

    @SuppressWarnings("unchecked")
    public static <T, R> Flux<R> batchExec(Collection<T> collection, int batchSize, Function<Collection<T>, Mono<R>> executor) {
        if (collection == null || collection.isEmpty()) {
            return Flux.empty();
        }

        List<Mono<R>> monoList = new ArrayList<>();
        final List<T> list = new ArrayList<>(collection);
        int size = list.size();
        int fromIndex = 0;
        int toIndex = 0;
        while (toIndex < size) {
            toIndex = Math.min(fromIndex + batchSize, size);

            List<T> subList = list.subList(fromIndex, toIndex);

            Mono<R> mono = executor.apply(subList);
            monoList.add(mono);

            fromIndex = toIndex;
        }

        return (Flux<R>) Mono.zip(monoList, Arrays::asList)
                .flatMapMany(Flux::fromIterable);
    }

    public static <T, R> Flux<R> queuedBatchExec(Collection<T> collection, int batchSize, Function<Collection<T>, Mono<R>> executor) {
        if (collection == null || collection.isEmpty()) {
            return Flux.empty();
        }
        final List<T> list = new ArrayList<>(collection);

        int size = list.size();
        int fromIndex = 0;
        int toIndex = 0;

        List<R> result = new ArrayList<>();

        Mono<Void> mono = Mono.empty();
        while (toIndex < size) {
            toIndex = Math.min(fromIndex + batchSize, size);
            List<T> subList = list.subList(fromIndex, toIndex);

            mono = mono.then(Mono.defer(() ->
                    executor.apply(subList)
                            .doOnNext(result::add)
                            .then()
            ));

            fromIndex = toIndex;
        }

        return Mono.when(mono)
                .thenMany(Flux.fromIterable(result));
    }
}
