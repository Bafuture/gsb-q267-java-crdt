package com.example.crdt;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * G-Counter（Grow-only Counter，只增计数器）。
 *
 * <p>每个副本只递增属于自己的计数槽位：状态为 {@code 副本ID -> 该副本计数}。
 * 合并语义为逐副本取最大值（join = component-wise max），满足
 * 交换律、结合律、幂等性；总值为各槽位之和。</p>
 */
public final class GCounter implements Crdt<GCounter> {

    private final String replicaId;
    private final Map<String, Long> counts;
    private long mergeCount;

    public GCounter(String replicaId) {
        this(replicaId, new HashMap<>());
    }

    private GCounter(String replicaId, Map<String, Long> counts) {
        this.replicaId = Objects.requireNonNull(replicaId, "replicaId");
        this.counts = counts;
    }

    public String replicaId() {
        return replicaId;
    }

    /** 将本副本的计数加 1。 */
    public void increment() {
        increment(1L);
    }

    /** 将本副本的计数加 delta（delta 必须非负）。 */
    public void increment(long delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("G-Counter delta must be non-negative: " + delta);
        }
        counts.merge(replicaId, delta, Long::sum);
    }

    /** 当前总值：所有副本槽位之和。 */
    public long value() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    /** 某副本槽位的当前计数（未知副本返回 0）。 */
    public long countOf(String replica) {
        return counts.getOrDefault(replica, 0L);
    }

    @Override
    public void merge(GCounter other) {
        for (Map.Entry<String, Long> entry : other.counts.entrySet()) {
            counts.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        mergeCount++;
    }

    /**
 * 状态压缩：把多个增量状态合并成一个紧凑状态。
 * 压缩状态与逐个合并原始增量对最终结果完全等价。
 */
    public static GCounter compress(String compressedId, Collection<GCounter> deltas) {
        GCounter compact = new GCounter(compressedId);
        for (GCounter delta : deltas) {
            compact.merge(delta);
        }
        return compact;
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
