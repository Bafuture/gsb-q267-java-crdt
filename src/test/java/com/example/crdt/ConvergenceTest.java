package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 收敛性验证：多个副本各自并发更新，随后以随机顺序 gossip 交换状态，
 * 有限轮后所有副本状态必须一致；测试输出收敛轮次与各副本统计。
 */
class ConvergenceTest {

    private static final int REPLICAS = 5;
    private static final int MAX_ROUNDS = 1_000;
    private static final long SEED = 42L;

    @Test
    void gCounterClusterConverges() {
        Random random = new Random(SEED);
        List<GCounter> cluster = new ArrayList<>();
        for (int i = 0; i < REPLICAS; i++) {
            GCounter replica = new GCounter("R" + i);
            for (int op = 0; op < 20; op++) {
                replica.increment(random.nextInt(5));
            }
            cluster.add(replica);
        }
        long expectedTotal = cluster.stream().mapToLong(GCounter::value).sum();

        GossipSimulator.Result result = GossipSimulator.simulate(cluster, MAX_ROUNDS, random);

        assertThat(result.converged()).as("应在有限轮内收敛").isTrue();
        assertThat(result.rounds()).isPositive();
        for (GCounter replica : cluster) {
            assertThat(replica).isEqualTo(cluster.get(0));
            assertThat(replica.value()).isEqualTo(expectedTotal);
        }
        System.out.printf("[G-Counter] 收敛轮次=%d, 统计=%s%n",
                result.rounds(), cluster.stream().map(GCounter::stats).toList());
    }

    @Test
    void pnCounterClusterConverges() {
        Random random = new Random(SEED);
        List<PNCounter> cluster = new ArrayList<>();
        for (int i = 0; i < REPLICAS; i++) {
            PNCounter replica = new PNCounter("R" + i);
            for (int op = 0; op < 20; op++) {
                if (random.nextBoolean()) {
                    replica.increment(random.nextInt(5));
                } else {
                    replica.decrement(random.nextInt(5));
                }
            }
            cluster.add(replica);
        }
        long expectedTotal = cluster.stream().mapToLong(PNCounter::value).sum();

        GossipSimulator.Result result = GossipSimulator.simulate(cluster, MAX_ROUNDS, random);

        assertThat(result.converged()).isTrue();
        for (PNCounter replica : cluster) {
            assertThat(replica).isEqualTo(cluster.get(0));
            assertThat(replica.value()).isEqualTo(expectedTotal);
        }
        System.out.printf("[PN-Counter] 收敛轮次=%d, 统计=%s%n",
                result.rounds(), cluster.stream().map(PNCounter::stats).toList());
    }

    @Test
    void orSetClusterConvergesWithConcurrentAddRemove() {
        Random random = new Random(SEED);
        String[] elements = {"a", "b", "c", "d", "e"};
        List<ORSet<String>> cluster = new ArrayList<>();
        for (int i = 0; i < REPLICAS; i++) {
            ORSet<String> replica = new ORSet<>("R" + i);
            for (int op = 0; op < 30; op++) {
                String element = elements[random.nextInt(elements.length)];
                if (random.nextBoolean()) {
                    replica.add(element);
                } else {
                    replica.remove(element);
                }
            }
            cluster.add(replica);
        }

        GossipSimulator.Result result = GossipSimulator.simulate(cluster, MAX_ROUNDS, random);

        assertThat(result.converged()).isTrue();
        for (ORSet<String> replica : cluster) {
            assertThat(replica).isEqualTo(cluster.get(0));
        }
        // 收敛后再交换状态不改变结果（幂等）
        ORSet<String> snapshot = cluster.get(0).copy();
        for (ORSet<String> replica : cluster) {
            replica.merge(snapshot.copy());
            assertThat(replica).isEqualTo(snapshot);
        }
        System.out.printf("[OR-Set] 收敛轮次=%d, 最终元素=%s, 统计=%s%n",
                result.rounds(), cluster.get(0).elements(),
                cluster.stream().map(ORSet::stats).toList());
    }

    @Test
    void convergenceIsIndependentOfGossipOrder() {
        List<ORSet<String>> base = new ArrayList<>();
        for (int i = 0; i < REPLICAS; i++) {
            ORSet<String> replica = new ORSet<>("R" + i);
            replica.add("item-" + i);
            if (i % 2 == 0) {
                replica.remove("item-" + i);
                replica.add("item-" + i);
            }
            base.add(replica);
        }

        ORSet<String> reference = null;
        for (long seed = 0; seed < 10; seed++) {
            List<ORSet<String>> cluster = new ArrayList<>();
            for (ORSet<String> replica : base) {
                cluster.add(replica.copy());
            }
            GossipSimulator.Result result =
                    GossipSimulator.simulate(cluster, MAX_ROUNDS, new Random(seed));
            assertThat(result.converged()).isTrue();
            if (reference == null) {
                reference = cluster.get(0);
            } else {
                assertThat(cluster.get(0))
                        .as("不同 gossip 顺序必须收敛到同一状态")
                        .isEqualTo(reference);
            }
        }
    }
}
