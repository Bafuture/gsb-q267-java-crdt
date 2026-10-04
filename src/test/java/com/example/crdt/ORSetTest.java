package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ORSetTest {

    @Test
    void addContainsRemove() {
        ORSet<String> set = new ORSet<>("A");
        set.add("x");
        set.add("y");

        assertThat(set.elements()).containsExactlyInAnyOrder("x", "y");

        set.remove("x");
        assertThat(set.contains("x")).isFalse();
        assertThat(set.elements()).containsExactly("y");
    }

    @Test
    void removeOfAbsentElementIsNoOp() {
        ORSet<String> set = new ORSet<>("A");
        assertThat(set.remove("ghost")).isZero();
        assertThat(set.elements()).isEmpty();
    }

    @Test
    void reAddAfterRemoveRevivesElement() {
        ORSet<String> set = new ORSet<>("A");
        set.add("x");
        set.remove("x");
        set.add("x");

        assertThat(set.contains("x")).isTrue();
    }

    @Test
    void mergeUnionsAddsAndRemoves() {
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");
        a.add("shared");
        b.add("shared");
        b.add("only-b");

        a.merge(b);
        assertThat(a.elements()).containsExactlyInAnyOrder("shared", "only-b");

        // A 观测到双方 tag 后删除，合并回 B 后 B 也看不到 shared
        a.remove("shared");
        b.merge(a);
        assertThat(b.contains("shared")).isFalse();
        assertThat(b.elements()).containsExactly("only-b");
    }

    @Test
    void mergeDoesNotLoseConcurrentAdditions() {
        // 经典场景：A、B 各自并发新增 x；B 删除 x 只能抹掉自己观测到的 tag
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");
        a.add("x");
        b.add("x");
        b.remove("x"); // 只删除 B 自己观测到的 tag

        a.merge(b);
        b.merge(a);

        assertThat(a.contains("x")).isTrue();
        assertThat(b.contains("x")).isTrue();
    }

    @Test
    void mergeIsIdempotent() {
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");
        a.add("x");
        b.add("y");
        b.remove("y");

        a.merge(b);
        ORSet<String> once = a.copy();
        a.merge(b);
        a.merge(b.copy());

        assertThat(a).isEqualTo(once);
    }

    @Test
    void compactShrinksStateWithoutChangingContents() {
        ORSet<String> set = new ORSet<>("A");
        set.add("x");
        set.add("y");
        set.remove("x");
        int before = set.stateSize();

        set.compact();

        assertThat(set.stateSize()).isLessThan(before);
        assertThat(set.elements()).containsExactly("y");
        assertThat(set.contains("x")).isFalse();
    }

    @Test
    void statsExposeSizeAndMergeCount() {
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");
        a.add("x");
        b.add("y");
        a.merge(b);

        CrdtStats stats = a.stats();
        assertThat(stats.replicaId()).isEqualTo("A");
        assertThat(stats.stateSize()).isEqualTo(2);
        assertThat(stats.mergeCount()).isEqualTo(1);
    }
}
