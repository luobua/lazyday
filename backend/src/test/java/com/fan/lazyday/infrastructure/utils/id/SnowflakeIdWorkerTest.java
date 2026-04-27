package com.fan.lazyday.infrastructure.utils.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SnowflakeIdWorker 单元测试
 * 验证雪花算法 ID 生成的唯一性、有序性和边界条件
 */
class SnowflakeIdWorkerTest {

    @Test
    @DisplayName("nextId: 生成的 ID 应为正数")
    void nextId_shouldReturnPositive() {
        SnowflakeIdWorker worker = new SnowflakeIdWorker(1, 1);
        long id = worker.nextId();

        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("nextId: 连续调用应生成递增的 ID")
    void nextId_shouldReturnIncreasingIds() {
        SnowflakeIdWorker worker = new SnowflakeIdWorker(1, 1);
        long id1 = worker.nextId();
        long id2 = worker.nextId();
        long id3 = worker.nextId();

        assertThat(id2).isGreaterThan(id1);
        assertThat(id3).isGreaterThan(id2);
    }

    @Test
    @DisplayName("nextId: 同一毫秒内通过序列号保证唯一性")
    void nextId_sameMillisecond_shouldBeUniqueBySequence() {
        SnowflakeIdWorker worker = new SnowflakeIdWorker(1, 1);
        Set<Long> ids = ConcurrentHashMap.newKeySet();

        int count = 1000;
        IntStream.range(0, count).parallel().forEach(i -> {
            long id = worker.nextId();
            ids.add(id);
        });

        assertThat(ids).hasSize(count);
    }

    @Test
    @DisplayName("构造函数: workerId 超出范围应抛出异常")
    void constructor_workerIdOutOfRange_shouldThrow() {
        assertThatThrownBy(() -> new SnowflakeIdWorker(256, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Worker ID");
    }

    @Test
    @DisplayName("构造函数: dataCenterId 超出范围应抛出异常")
    void constructor_dataCenterIdOutOfRange_shouldThrow() {
        assertThatThrownBy(() -> new SnowflakeIdWorker(1, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Data Center ID");
    }

    @Test
    @DisplayName("构造函数: workerId 为负数应抛出异常")
    void constructor_negativeWorkerId_shouldThrow() {
        assertThatThrownBy(() -> new SnowflakeIdWorker(-1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("nextId: 不同 worker 生成的 ID 应不同")
    void nextId_differentWorkers_shouldGenerateDifferentIds() {
        SnowflakeIdWorker worker1 = new SnowflakeIdWorker(1, 1);
        SnowflakeIdWorker worker2 = new SnowflakeIdWorker(2, 1);

        long id1 = worker1.nextId();
        long id2 = worker2.nextId();

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("无参构造: 默认构造不应抛出异常")
    void defaultConstructor_shouldNotThrow() {
        SnowflakeIdWorker worker = new SnowflakeIdWorker();
        long id = worker.nextId();

        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("nextId: 大批量生成应全部唯一（10000 个）")
    void nextId_largeBatch_shouldAllBeUnique() {
        SnowflakeIdWorker worker = new SnowflakeIdWorker(1, 1);
        Set<Long> ids = ConcurrentHashMap.newKeySet();

        int count = 10000;
        for (int i = 0; i < count; i++) {
            ids.add(worker.nextId());
        }

        assertThat(ids).hasSize(count);
    }
}
