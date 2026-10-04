package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GCounterTest {

    @Test
    void incrementsAccumulateLocally() {
        GCounter counter = new GCounter("A");
        assertThat(counter.value()).isZero();

        counter.increment();
        counter.increment();
        counter.increment(5);

        assertThat(counter.value()).isEqualTo(7);
        assertThat(counter.counts()).containsEntry("A", 7L);
    }

    @Test
    void rejectsNegativeDelta() {
        GCounter counter = new GCounter("A");
        assertThatThrownBy(() -> counter.increment(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mergeTakesPerReplicaMax() {
        GCounter a = new GCounter("A");
        GCounter b = new GCounter("B");
        a.increment(3);
        b.increment(10);

        a.merge(b);
        assertThat(a.value()).isEqualTo(13);
        assertThat(a.counts()).containsEntry("A", 3L).containsEntry("B", 10L);

        // B 继续增长后再次合并，逐副本取 max 而不是相加
        b.increment(4);
        a.merge(b);
        assertThat(a.value()).isEqualTo(17);
    }

    @Test
    void mergeIsIdempotent() {
        GCounter a = new GCounter("A");
        GCounter b = new GCounter("B");
        a.increment(2);
        b.increment(5);

        a.merge(b);
        GCounter once = a.copy();
        a.merge(b);
        a.merge(b.copy());

        assertThat(a).isEqualTo(once);
        assertThat(a.value()).isEqualTo(7);
    }

    @Test
    void mergeIsCommutative() {
        GCounter a1 = new GCounter("A");
        GCounter b1 = new GCounter("B");
        a1.increment(4);
        b1.increment(6);

        GCounter a2 = a1.copy();
        GCounter b2 = b1.copy();
        a1.merge(b1);
        b2.merge(a2);

        assertThat(a1).isEqualTo(b2);
    }

    @Test
    void copyIsIndependent() {
        GCounter a = new GCounter("A");
        a.increment(3);
        GCounter copy = a.copy();
        copy.increment(10);

        assertThat(a.value()).isEqualTo(3);
        assertThat(copy.value()).isEqualTo(13);
    }

    @Test
    void statsExposeSizeAndMergeCount() {
        GCounter a = new GCounter("A");
        GCounter b = new GCounter("B");
        b.increment(1);
        a.merge(b);
        a.merge(b);

        CrdtStats stats = a.stats();
        assertThat(stats.replicaId()).isEqualTo("A");
        assertThat(stats.stateSize()).isEqualTo(2);
        assertThat(stats.mergeCount()).isEqualTo(2);
    }
}
