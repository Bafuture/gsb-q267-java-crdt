package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 乱序与重复消息测试：merge 的幂等性、交换律、结合律，以及增量消息
 * 被乱序、重复投递后最终状态与按序投递完全一致。
 */
class MergePropertiesTest {

    private static final Random RANDOM = new Random(42L);

    @Test
    @DisplayName("G-Counter：乱序 + 重复投递与按序投递结果一致")
    void gCounterDeliveriesAreOrderAndDuplicateInsensitive() {
        List<GCounter> messages = new ArrayList<>();
        GCounter a = new GCounter("A");
        GCounter b = new GCounter("B");
        GCounter c = new GCounter("C");
        a.increment(3);
        messages.add(a.drainDelta());
        b.increment(2);
        messages.add(b.drainDelta());
        a.increment(4);
        messages.add(a.drainDelta());
        c.increment(1);
        messages.add(c.drainDelta());

        GCounter inOrder = new GCounter("subscriber");
        for (GCounter message : messages) {
            inOrder.merge(message);
        }

        List<GCounter> shuffled = shuffleWithDuplicates(messages, RANDOM);
        GCounter disordered = new GCounter("subscriber");
        for (GCounter message : shuffled) {
            disordered.merge(message);
        }

        assertThat(disordered).isEqualTo(inOrder);
        assertThat(disordered.value()).isEqualTo(10);
    }

    @Test
    @DisplayName("PN-Counter：乱序 + 重复投递与按序投递结果一致")
    void pnCounterDeliveriesAreOrderAndDuplicateInsensitive() {
        List<PNCounter> messages = new ArrayList<>();
        PNCounter a = new PNCounter("A");
        PNCounter b = new PNCounter("B");
        a.increment(5);
        messages.add(a.drainDelta());
        b.decrement(7);
        messages.add(b.drainDelta());
        a.decrement(2);
        messages.add(a.drainDelta());
        b.increment(3);
        messages.add(b.drainDelta());

        PNCounter inOrder = new PNCounter("subscriber");
        messages.forEach(inOrder::merge);

        PNCounter disordered = new PNCounter("subscriber");
        for (PNCounter message : shuffleWithDuplicates(messages, RANDOM)) {
            disordered.merge(message);
        }

        assertThat(disordered).isEqualTo(inOrder);
        assertThat(disordered.value()).isEqualTo(-1);
    }

    @Test
    @DisplayName("OR-Set：乱序 + 重复投递（含墓碑）与按序投递结果一致")
    void orSetDeliveriesAreOrderAndDuplicateInsensitive() {
        List<ORSet<String>> messages = new ArrayList<>();
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");
        a.add("x");
        messages.add(a.drainDelta());
        b.add("y");
        messages.add(b.drainDelta());
        b.remove("y");
        messages.add(b.drainDelta());
        a.add("z");
        a.add("x");
        messages.add(a.drainDelta());
        b.merge(messages.get(0));
        b.remove("x"); // 删除已观测的 A:1，但不影响后续的 A:2（乱序关键场景）
        messages.add(b.drainDelta());
        a.add("w");
        messages.add(a.drainDelta());

        ORSet<String> inOrder = new ORSet<>("subscriber");
        messages.forEach(inOrder::merge);

        ORSet<String> disordered = new ORSet<>("subscriber");
        for (ORSet<String> message : shuffleWithDuplicates(messages, RANDOM)) {
            disordered.merge(message);
        }

        assertThat(disordered).isEqualTo(inOrder);
        assertThat(disordered.elements()).containsExactlyInAnyOrder("w", "z", "x");
        assertThat(disordered.contains("y")).isFalse();
    }

    @Test
    @DisplayName("随机化代数性质：幂等、交换、结合（三种 CRDT）")
    void algebraicLawsHoldForRandomStates() {
        for (int trial = 0; trial < 20; trial++) {
            checkCvRdt(randomGCounter(), randomGCounter(), randomGCounter());
            checkCvRdt(randomPNCounter(), randomPNCounter(), randomPNCounter());
            checkCvRdt(randomORSet(), randomORSet(), randomORSet());
        }
    }

    private <T extends CvRdt<T>> void checkCvRdt(T x, T y, T z) {
        // 幂等：x.merge(x) == x
        T idempotent = x.copy();
        idempotent.merge(x.copy());
        assertThat(idempotent).isEqualTo(x);

        // 交换：x.merge(y) == y.merge(x)
        T xy = x.copy();
        xy.merge(y.copy());
        T yx = y.copy();
        yx.merge(x.copy());
        assertThat(xy).isEqualTo(yx);

        // 结合：(x.merge(y)).merge(z) == x.merge(y.merge(z))
        T left = x.copy();
        left.merge(y.copy());
        left.merge(z.copy());
        T right = x.copy();
        T yz = y.copy();
        yz.merge(z.copy());
        right.merge(yz);
        assertThat(left).isEqualTo(right);
    }

    private static <T> List<T> shuffleWithDuplicates(List<T> messages, Random random) {
        List<T> result = new ArrayList<>(messages);
        // 插入若干重复消息后整体打乱
        int duplicates = messages.size() / 2;
        for (int i = 0; i < duplicates; i++) {
            result.add(messages.get(random.nextInt(messages.size())));
        }
        Collections.shuffle(result, random);
        return result;
    }

    private static GCounter randomGCounter() {
        GCounter counter = new GCounter("r" + RANDOM.nextInt(4));
        for (int i = 0; i < RANDOM.nextInt(6); i++) {
            counter.increment(RANDOM.nextInt(5));
        }
        return counter;
    }

    private static PNCounter randomPNCounter() {
        PNCounter counter = new PNCounter("r" + RANDOM.nextInt(4));
        for (int i = 0; i < RANDOM.nextInt(6); i++) {
            if (RANDOM.nextBoolean()) {
                counter.increment(RANDOM.nextInt(5));
            } else {
                counter.decrement(RANDOM.nextInt(5));
            }
        }
        return counter;
    }

    private static ORSet<String> randomORSet() {
        ORSet<String> set = new ORSet<>("r" + RANDOM.nextInt(4));
        for (int i = 0; i < RANDOM.nextInt(6); i++) {
            String element = "e" + RANDOM.nextInt(5);
            if (RANDOM.nextBoolean()) {
                set.add(element);
            } else {
                set.remove(element);
            }
        }
        return set;
    }
}
