package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PNCounterTest {

    @Test
    void supportsIncrementAndDecrement() {
        PNCounter counter = new PNCounter("A");
        counter.increment(10);
        counter.decrement(4);
        counter.decrement();

        assertThat(counter.value()).isEqualTo(5);
    }

    @Test
    void valueMayGoNegative() {
        PNCounter counter = new PNCounter("A");
        counter.decrement(7);
        counter.increment(2);

        assertThat(counter.value()).isEqualTo(-5);
    }

    @Test
    void rejectsNegativeArguments() {
        PNCounter counter = new PNCounter("A");
        assertThatThrownBy(() -> counter.increment(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> counter.decrement(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mergeCombinesBothDirectionsAcrossReplicas() {
        PNCounter a = new PNCounter("A");
        PNCounter b = new PNCounter("B");
        a.increment(8);
        a.decrement(3);
        b.increment(2);
        b.decrement(9);

        a.merge(b);
        b.merge(a);

        // A: +8-3, B: +2-9 => 总值 -2，两个副本收敛到同一状态
        assertThat(a.value()).isEqualTo(-2);
        assertThat(b.value()).isEqualTo(-2);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void mergeIsIdempotentAndCommutative() {
        PNCounter a = new PNCounter("A");
        PNCounter b = new PNCounter("B");
        a.increment(5);
        b.decrement(2);

        PNCounter ab = a.copy();
        ab.merge(b);
        PNCounter abTwice = ab.copy();
        abTwice.merge(b);
        assertThat(abTwice).isEqualTo(ab);

        PNCounter ba = b.copy();
        ba.merge(a);
        assertThat(ab).isEqualTo(ba);
    }

    @Test
    void stateSizeCoversBothSubCounters() {
        PNCounter a = new PNCounter("A");
        PNCounter b = new PNCounter("B");
        b.increment(1);
        a.merge(b);

        // P、N 两个 G-Counter 各自知道 A、B 两个槽位
        assertThat(a.stateSize()).isEqualTo(4);
        assertThat(a.stats().mergeCount()).isEqualTo(1);
    }
}
