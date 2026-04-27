package com.fan.lazyday.infrastructure.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReactiveHelper 单元测试
 * 验证批处理分批逻辑
 */
class ReactiveHelperTest {

    @Test
    @DisplayName("batchExec: 空集合应返回空 Flux")
    void batchExec_emptyCollection_shouldReturnEmpty() {
        Flux<Integer> result = ReactiveHelper.batchExec(List.of(), 5, items ->
                Mono.just(items.size()));

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("batchExec: null 集合应返回空 Flux")
    void batchExec_nullCollection_shouldReturnEmpty() {
        Flux<Integer> result = ReactiveHelper.batchExec(null, 5, items ->
                Mono.just(items.size()));

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("batchExec: 集合大小等于 batchSize 应一批处理")
    void batchExec_exactBatchSize_shouldProcessInOneBatch() {
        AtomicInteger batchCount = new AtomicInteger(0);

        Flux<Integer> result = ReactiveHelper.batchExec(List.of(1, 2, 3), 3, items -> {
            batchCount.incrementAndGet();
            return Mono.just(items.size());
        });

        StepVerifier.create(result.collectList())
                .assertNext(results -> {
                    assertThat(results).containsExactly(3);
                    assertThat(batchCount.get()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("batchExec: 集合超过 batchSize 应分批处理")
    void batchExec_largerThanBatchSize_shouldSplit() {
        AtomicInteger batchCount = new AtomicInteger(0);
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        Flux<Integer> result = ReactiveHelper.batchExec(items, 2, batch -> {
            batchCount.incrementAndGet();
            return Mono.just(batch.size());
        });

        StepVerifier.create(result.collectList())
                .assertNext(results -> {
                    // 5 items, batch=2 → [2, 2, 1] 共 3 批
                    assertThat(results).hasSize(3);
                    assertThat(results).containsExactly(2, 2, 1);
                    assertThat(batchCount.get()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("batchExec: 每个 batch 应包含正确的元素")
    void batchExec_shouldContainCorrectElementsPerBatch() {
        List<List<Integer>> batches = new ArrayList<>();
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7);

        Flux<List<Integer>> result = ReactiveHelper.batchExec(items, 3, batch -> {
            batches.add(new ArrayList<>(batch));
            return Mono.just(new ArrayList<>(batch));
        });

        StepVerifier.create(result.collectList())
                .assertNext(allBatches -> {
                    assertThat(batches).hasSize(3);
                    assertThat(batches.get(0)).containsExactly(1, 2, 3);
                    assertThat(batches.get(1)).containsExactly(4, 5, 6);
                    assertThat(batches.get(2)).containsExactly(7);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("queuedBatchExec: 空集合应返回空 Flux")
    void queuedBatchExec_emptyCollection_shouldReturnEmpty() {
        Flux<Integer> result = ReactiveHelper.queuedBatchExec(List.of(), 5, items ->
                Mono.just(items.size()));

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("queuedBatchExec: null 集合应返回空 Flux")
    void queuedBatchExec_nullCollection_shouldReturnEmpty() {
        Flux<Integer> result = ReactiveHelper.queuedBatchExec(null, 5, items ->
                Mono.just(items.size()));

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("queuedBatchExec: 应按顺序处理（非并行）")
    void queuedBatchExec_shouldProcessSequentially() {
        List<String> executionOrder = new ArrayList<>();
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        Flux<Integer> result = ReactiveHelper.queuedBatchExec(items, 2, batch -> {
            executionOrder.add("batch-" + batch);
            return Mono.just(batch.size());
        });

        StepVerifier.create(result.collectList())
                .assertNext(results -> {
                    assertThat(executionOrder).hasSize(3);
                    // queuedBatchExec 顺序执行
                    assertThat(executionOrder.get(0)).isEqualTo("batch-[1, 2]");
                    assertThat(executionOrder.get(1)).isEqualTo("batch-[3, 4]");
                    assertThat(executionOrder.get(2)).isEqualTo("batch-[5]");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("queuedBatchExec: 返回结果应包含所有批次的累加值")
    void queuedBatchExec_shouldReturnAllResults() {
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        Flux<Integer> result = ReactiveHelper.queuedBatchExec(items, 2, batch ->
                Mono.just(batch.stream().mapToInt(i -> i).sum())
        );

        StepVerifier.create(result.collectList())
                .assertNext(sums -> {
                    assertThat(sums).containsExactly(3, 7, 5); // 1+2, 3+4, 5
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("batchExec vs queuedBatchExec: 两者结果应相同")
    void batchExecVsQueued_shouldHaveSameResults() {
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6);

        Flux<Integer> batchResult = ReactiveHelper.batchExec(items, 2, batch ->
                Mono.just(batch.size()));
        Flux<Integer> queuedResult = ReactiveHelper.queuedBatchExec(items, 2, batch ->
                Mono.just(batch.size()));

        StepVerifier.create(Mono.zip(
                batchResult.collectList(),
                queuedResult.collectList()
        ))
                .assertNext(tuple -> {
                    assertThat(tuple.getT1()).isEqualTo(tuple.getT2());
                })
                .verifyComplete();
    }
}
