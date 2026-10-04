package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 乱序与重复消息：对同一批副本状态，按序合并、乱序合并、
 * 乱序且重复合并的结果必须完全一致（交换律 + 幂等性）。
 */
class MergePropertiesTest {

    private static final long SEED = 20261004L;

    @Test
    void gCounterOutOfOrderAndDuplicateMergesMatchInOrder() {
        List<GCounter> replicas = List.of(
                counter("A", 5), counter("B", 3), counter("C", 8), counter("D", 1));

        GCounter inOrder = new GCounter("sink");
        for (GCounter replica : replicas) {
            inOrder.merge(replica);
        }

        List<GCounter> shuffled = new ArrayList<>(replicas);
        Collections.shuffle(shuffled, new Random(SEED));
        GCounter outOfOrder = new GCounter("sink");
        for (GCounter replica : shuffled) {
            outOfOrder.merge(replica);
        }

        GCounter duplicated = new GCounter("sink");
        Random random = new Random(SEED);
        for (int i = 0; i < 40; i++) {
            duplicated.merge(replicas.get(random.nextInt(replicas.size())));
        }

        assertThat(outOfOrder).isEqualTo(inOrder);
        assertThat(duplicated).isEqualTo(inOrder);
        assertThat(inOrder.value()).isEqualTo(17);
    }

    private static GCounter counter(String id, long n) {
        GCounter counter = new GCounter(id);
        counter.increment(n);
        return counter;
    }

    @Test
    void pnCounterOutOfOrderAndDuplicateMergesMatchInOrder() {
        PNCounter a = new PNCounter("A");
        a.increment(10);
        a.decrement(2);
        PNCounter b = new PNCounter("B");
        b.decrement(5);
        PNCounter c = new PNCounter("C");
        c.increment(1);
        List<PNCounter> replicas = List.of(a, b, c);

        PNCounter inOrder = new PNCounter("sink");
        replicas.forEach(inOrder::merge);

        List<PNCounter> shuffled = new ArrayList<>(replicas);
        Collections.shuffle(shuffled, new Random(SEED));
        PNCounter outOfOrder = new PNCounter("sink");
        shuffled.forEach(outOfOrder::merge);

        PNCounter duplicated = new PNCounter("sink");
        Random random = new Random(SEED);
        for (int i = 0; i < 40; i++) {
            duplicated.merge(replicas.get(random.nextInt(replicas.size())));
        }

        assertThat(outOfOrder).isEqualTo(inOrder);
        assertThat(duplicated).isEqualTo(inOrder);
        assertThat(inOrder.value()).isEqualTo(4);
    }

    @Test
    void orSetOutOfOrderAndDuplicateMergesMatchInOrder() {
        ORSet<String> a = new ORSet<>("A");
        a.add("x");
        a.add("y");
        ORSet<String> b = new ORSet<>("B");
        b.merge(a);
        b.remove("x");
        b.add("z");
        ORSet<String> c = new ORSet<>("C");
        c.add("x");
        c.add("w");
        List<ORSet<String>> replicas = List.of(a, b, c);

        ORSet<String> inOrder = new ORSet<>("sink");
        replicas.forEach(inOrder::merge);

        List<ORSet<String>> shuffled = new ArrayList<>(replicas);
        Collections.shuffle(shuffled, new Random(SEED));
        ORSet<String> outOfOrder = new ORSet<>("sink");
        shuffled.forEach(outOfOrder::merge);

        ORSet<String> duplicated = new ORSet<>("sink");
        Random random = new Random(SEED);
        for (int i = 0; i < 40; i++) {
            duplicated.merge(replicas.get(random.nextInt(replicas.size())));
        }

        assertThat(outOfOrder).isEqualTo(inOrder);
        assertThat(duplicated).isEqualTo(inOrder);
        assertThat(inOrder.elements()).containsExactlyInAnyOrder("y", "z", "x", "w");
    }
}
