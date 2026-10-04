package com.example.crdt;

import java.util.Collection;
import java.util.Objects;

/**
 * PN-Counter（可增可减计数器）。
 *
 * <p>由两个 G-Counter 构成：P 记录所有增量，N 记录所有减量，
 * 当前值 = P - N。合并分别对 P、N 做逐副本取最大值，
 * 因而同样满足交换律、结合律与幂等性。</p>
 */
public final class PNCounter implements Crdt<PNCounter> {

    private final String replicaId;
    private final GCounter increments;
    private final GCounter decrements;

    public PNCounter(String replicaId) {
        this(replicaId, new GCounter(replicaId), new GCounter(replicaId));
    }

    private PNCounter(String replicaId, GCounter increments, GCounter decrements) {
        this.replicaId = Objects.requireNonNull(replicaId, "replicaId");
        this.increments = increments;
        this.decrements = decrements;
    }

    public String replicaId() {
        return replicaId;
    }

    public void increment() {
        increment(1L);
    }

    public void increment(long delta) {
        increments.increment(delta);
    }

    public void decrement() {
        decrement(1L);
    }

    public void decrement(long delta) {
        decrements.increment(delta);
    }

    /** 当前值：总增量 - 总减量。 */
    public long value() {
        return increments.value() - decrements.value();
    }

    @Override
    public void merge(PNCounter other) {
        increments.merge(other.increments);
        decrements.merge(other.decrements);
    }

    /**
     * 状态压缩：把多个增量状态合并成一个紧凑状态，
     * 压缩后合并与逐个合并原始增量结果一致。
     */
    public static PNCounter compress(String compressedId, Collection<PNCounter> deltas) {
        PNCounter compact = new PNCounter(compressedId);
        for (PNCounter delta : deltas) {
            compact.merge(delta);
        }
        return compact;
    }

    @Override
    public PNCounter copy() {
        return new PNCounter(replicaId, increments.copy(), decrements.copy());
    }

    @Override
    public int stateSize() {
        return increments.stateSize() + decrements.stateSize();
    }

    @Override
    public long mergeCount() {
        return increments.mergeCount();
    }

    public CrdtStats stats() {
        return new CrdtStats(replicaId, stateSize(), mergeCount());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PNCounter other)) {
            return false;
        }
        return increments.equals(other.increments) && decrements.equals(other.decrements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(increments, decrements);
    }

    @Override
    public String toString() {
        return "PNCounter{p=" + increments + ", n=" + decrements + '}';
    }
}
