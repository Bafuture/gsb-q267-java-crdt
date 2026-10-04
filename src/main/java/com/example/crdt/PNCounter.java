package com.example.crdt;

import java.util.Objects;

/**
 * PN-Counter：可增减计数器，由两个 G-Counter 组合而成。
 *
 * <p>P 记录所有增量，N 记录所有减量，值为 {@code P - N}。
 * 合并委托给两个 G-Counter 的逐副本取最大值，因此同样满足
 * 交换/结合/幂等，支持乱序与重复消息。</p>
 */
public final class PNCounter implements CvRdt<PNCounter> {

    private final String replicaId;
    private final GCounter increments;
    private final GCounter decrements;
    private long mergeCount;

    public PNCounter(String replicaId) {
        this.replicaId = Objects.requireNonNull(replicaId, "replicaId");
        this.increments = new GCounter(replicaId);
        this.decrements = new GCounter(replicaId);
    }

    private PNCounter(String replicaId, GCounter increments, GCounter decrements) {
        this.replicaId = replicaId;
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
        if (delta < 0) {
            throw new IllegalArgumentException("decrement 参数必须 >= 0: " + delta);
        }
        decrements.increment(delta);
    }

    /** 当前值：总增量 - 总减量，可以为负。 */
    public long value() {
        return increments.value() - decrements.value();
    }

    @Override
    public void merge(PNCounter other) {
        increments.merge(other.increments);
        decrements.merge(other.decrements);
        mergeCount++;
    }

    /** 取出并清空增量缓冲区（P、N 各自的压缩增量）。 */
    public PNCounter drainDelta() {
        return new PNCounter(replicaId, increments.drainDelta(), decrements.drainDelta());
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
        return "PNCounter{p=" + increments.counts() + ", n=" + decrements.counts() + "}";
    }
}
