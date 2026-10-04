package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PNCounterTest {

    @Test
    void supportsIncrementAndDecrement() {
        PNCounter a = new PNCounter("A");
        a.increment(5);
        a.decrement(2);
        assertThat(a.value()).isEqualTo(3);

        a.decrement(10);
        assertThat(a.value()).isEqualTo(-7);
    }

    @Test
    void mergeCombinesBothDirections() {
        PNCounter a = new PNCounter("A");
        PNCounter b = new PNCounter("B");

        a.increment(10);
        a.decrement(1);
        b.increment(4);
        b.decrement(6);

        a.merge(b);
        b.merge(a);

        assertThat(a.value()).isEqualTo(7);
        assertThat(b.value()).isEqualTo(7);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void mergeIsIdempotent() {
        PNCounter a = new PNCounter("A");
        PNCounter b = new PNCounter("B");
        a.increment(3);
        b.decrement(2);

        a.merge(b);
        PNCounter snapshot = a.copy();
        a.merge(b);
        a.merge(b.copy());

        assertThat(a).isEqualTo(snapshot);
        assertThat(a.value()).isEqualTo(1);
    }

    @Test
    void compressDeltasDoesNotChangeResult() {
        PNCounter d1 = new PNCounter("A");
        d1.increment(4);
        PNCounter d2 = new PNCounter("A");
        d2.decrement(1);
        PNCounter d3 = new PNCounter("B");
        d3.increment(2);
        d3.decrement(3);

        PNCounter sequential = new PNCounter("base");
        sequential.merge(d1);
        sequential.merge(d2);
        sequential.merge(d3);

        PNCounter viaCompact = new PNCounter("base");
        viaCompact.merge(PNCounter.compress("compact", List.of(d1, d2, d3)));

        assertThat(viaCompact).isEqualTo(sequential);
        assertThat(viaCompact.value()).isEqualTo(2);
    }

    @Test
    void statsExposeSizeAndMergeCount() {
        PNCounter a = new PNCounter("A");
        PNCounter b = new PNCounter("B");
        a.increment();
        a.decrement();
        b.increment();

        a.merge(b);
        a.merge(b);

        CrdtStats stats = a.stats();
        assertThat(stats.replicaId()).isEqualTo("A");
        assertThat(stats.stateSize()).isEqualTo(3);
        assertThat(stats.mergeCount()).isEqualTo(2);
    }
}
