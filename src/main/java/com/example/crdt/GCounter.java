package com.example.crdt;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * G-Counter：只增计数器（state-based CvRDT）。
 *
 * <p>每个副本只累加自己槽位 {@code counts[replicaId]}，合并时逐副本取最大值。
 * 由于 max 满足交换/结合/幂等，任意消息顺序与重复投递下均能收敛。</p>
 *
 * <p>状态压缩：每次 increment 同时把增量写入增量缓冲区，{@link #drainDelta()}
 * 把多次增量折叠为一个紧凑状态（每副本至多一个条目），其合并效果与逐条
 * 合并所有增量完全一致。</p>
 */
public final class GCounter implements CvRdt<GCounter> {

    private final String replicaId;
    private final Map<String, Long> counts;
    private long mergeCount;
    private GCounter deltaBuffer;

    public GCounter(String replicaId) {
        this(replicaId, new HashMap<>());
        counts.put(replicaId, 0L);
    }

    private GCounter(String replicaId, Map<String, Long> counts) {
        this.replicaId = Objects.requireNonNull(replicaId, "replicaId");
        this.counts = counts;
    }

    public String replicaId() {
        return replicaId;
    }

    public void increment() {
        increment(1L);
    }

    public void increment(long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("G-Counter 只增，delta 必须 >= 0: " + delta);
        }
        counts.merge(replicaId, delta, Long::sum);
        // 增量消息必须携带本槽位的累计值：合并语义是逐副本取 max，
        // 若只携带当次增量，乱序/压缩后会丢失先前的增量。
        deltaBuffer().counts.put(replicaId, counts.get(replicaId));
    }

    /** 全局计数值：所有副本槽位之和。 */
    public long value() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    /** 各副本槽位快照（只读）。 */
    public Map<String, Long> counts() {
        return Collections.unmodifiableMap(counts);
    }

    @Override
    public void merge(GCounter other) {
        other.counts.forEach((id, value) -> counts.merge(id, value, Math::max));
        mergeCount++;
    }

    /**
     * 取出并清空增量缓冲区：多次 increment 被压缩为一个紧凑状态。
     * 返回的增量可直接作为消息投递给其他副本，可重复、可乱序。
     */
    public GCounter drainDelta() {
        GCounter delta = deltaBuffer;
        deltaBuffer = null;
        return delta == null ? new GCounter(replicaId) : delta;
    }

    private GCounter deltaBuffer() {
        if (deltaBuffer == null) {
            deltaBuffer = new GCounter(replicaId, new HashMap<>());
        }
        return deltaBuffer;
    }

    @Override
    public GCounter copy() {
        return new GCounter(replicaId, new HashMap<>(counts));
    }

    @Override
    public int stateSize() {
        return counts.size();
    }

    @Override
    public long mergeCount() {
        return mergeCount;
    }

    public CrdtStats stats() {
        return new CrdtStats(replicaId, stateSize(), mergeCount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GCounter other)) {
            return false;
        }
        return counts.equals(other.counts);
    }

    @Override
    public int hashCode() {
        return counts.hashCode();
    }

    @Override
    public String toString() {
        return "GCounter" + counts;
    }
}
