package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class GCounterTest {

    @Test
    void incrementsOnlyOwnSlotAndValueIsSum() {
        GCounter a = new GCounter("A");
        GCounter b = new GCounter("B");

        a.increment();
        a.increment(4);
        b.increment(3);

        assertThat(a.value()).isEqualTo(5);
        assertThat(b.value()).isEqualTo(3);

        a.merge(b);
        b.merge(a);

        assertThat(a.value()).isEqualTo(8);
        assertThat(b.value()).isEqualTo(8);
        assertThat(a.countOf("A")).isEqualTo(5);
        assertThat(a.countOf("B")).isEqualTo(3);
    }

    @Test
    void mergeTakesComponentWiseMax() {
        GCounter a = new GCounter("A");
        GCounter b = new GCounter("B");
        a.increment(10);
        b.increment(7);

        GCounter stale = b.copy();
        b.increment(5);

        b.merge(stale);
        assertThat(b.value()).isEqualTo(12);

        a.merge(b);
        a.merge(stale);
        assertThat(a.countOf("B")).isEqualTo(12);
    }

    @Test
    void rejectsNegativeDelta() {
        GCounter a = new GCounter("A");
        assertThatThrownBy(() -> a.increment(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mergeIsIdempotentAndCommutative() {
        GCounter a = new GCounter("A");
        GCounter b = new GCounter("B");
        a.increment(3);
        b.increment(2);

        GCounter merged1 = a.copy();
        merged1.merge(b);
        merged1.merge(b);
        merged1.merge(b.copy());

        GCounter merged2 = b.copy();
        merged2.merge(a);

        assertThat(merged1).isEqualTo(merged2);
        assertThat(merged1.value()).isEqualTo(5);
    }

    @Test
    void compressDeltasDoesNotChangeResult() {
        GCounter base = new GCounter("base");
        base.increment(1);

        GCounter d1 = new GCounter("A");
        d1.increment(2);
        GCounter d2 = new GCounter("A");
        d2.increment(5);
        GCounter d3 = new GCounter("B");
        d3.increment(5);

        GCounter sequential = base.copy();
        sequential.merge(d1);
        sequential.merge(d2);
        sequential.merge(d3);

        GCounter viaCompact = base.copy();
        viaCompact.merge(GCounter.compress("compact", List.of(d1, d2, d3)));

        assertThat(viaCompact).isEqualTo(sequential);
        assertThat(viaCompact.value()).isEqualTo(11);
    }

    @Test
    void copyIsIndependent() {
        GCounter a = new GCounter("A");
        a.increment(2);
        GCounter copy = a.copy();
        copy.increment(10);
        assertThat(a.value()).isEqualTo(2);
        assertThat(copy.value()).isEqualTo(12);
    }
}
