package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ORSetTest {

    @Test
    void addContainsRemove() {
        ORSet<String> set = new ORSet<>("A");
        set.add("x");
        set.add("y");

        assertThat(set.contains("x")).isTrue();
        assertThat(set.elements()).containsExactlyInAnyOrder("x", "y");

        set.remove("x");
        assertThat(set.contains("x")).isFalse();
        assertThat(set.elements()).containsExactly("y");
    }

    @Test
    void removeOfUnknownElementIsNoOp() {
        ORSet<String> set = new ORSet<>("A");
        set.remove("ghost");
        assertThat(set.elements()).isEmpty();
        assertThat(set.stateSize()).isZero();
    }

    @Test
    void mergePropagatesAddAndRemove() {
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");

        a.add("x");
        b.merge(a);
        b.remove("x");
        b.add("y");

        a.merge(b);

        assertThat(a.contains("x")).isFalse();
        assertThat(a.contains("y")).isTrue();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void mergeNeverLosesUnremovedAdds() {
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");

        a.add("shared");
        b.add("shared");

        a.merge(b);
        b.merge(a);

        assertThat(a.contains("shared")).isTrue();
        assertThat(b.contains("shared")).isTrue();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void concurrentRemoveDoesNotOverrideConcurrentAdd() {
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");

        a.add("e");
        b.merge(a);

        // 并发：b 删除 e，同时 a 再次新增 e（新标签 b 从未观察到）
        b.remove("e");
        a.add("e");

        a.merge(b);
        b.merge(a);

        assertThat(a.contains("e")).as("add-wins: 并发删除不覆盖并发新增").isTrue();
        assertThat(b.contains("e")).isTrue();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void reAddAfterRemoveSurvivesOldTombstones() {
        ORSet<String> a = new ORSet<>("A");
        a.add("e");
        a.remove("e");
        assertThat(a.contains("e")).isFalse();

        a.add("e");
        assertThat(a.contains("e")).isTrue();
    }

    @Test
    void mergeIsIdempotent() {
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");
        a.add("x");
        b.add("y");
        b.remove("y");
        b.add("z");

        a.merge(b);
        ORSet<String> snapshot = a.copy();
        a.merge(b);
        a.merge(b.copy());

        assertThat(a).isEqualTo(snapshot);
        assertThat(a.elements()).containsExactlyInAnyOrder("x", "z");
    }

    @Test
    void compressDeltasDoesNotChangeResult() {
        ORSet<String> d1 = new ORSet<>("A");
        d1.add("x");
        ORSet<String> d2 = new ORSet<>("B");
        d2.add("y");
        ORSet<String> d3 = new ORSet<>("A");
        d3.merge(d1);
        d3.remove("x");
        d3.add("z");

        ORSet<String> sequential = new ORSet<>("base");
        sequential.merge(d1);
        sequential.merge(d2);
        sequential.merge(d3);

        ORSet<String> viaCompact = new ORSet<>("base");
        viaCompact.merge(ORSet.compress("compact", List.of(d1, d2, d3)));

        assertThat(viaCompact).isEqualTo(sequential);
        assertThat(viaCompact.elements()).containsExactlyInAnyOrder("y", "z");
    }

    @Test
    void copyIsIndependent() {
        ORSet<String> a = new ORSet<>("A");
        a.add("x");
        ORSet<String> copy = a.copy();
        copy.remove("x");
        copy.add("y");

        assertThat(a.contains("x")).isTrue();
        assertThat(a.contains("y")).isFalse();
    }
}
