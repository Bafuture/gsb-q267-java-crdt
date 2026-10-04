package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 收敛性验证：更新停止后，任意副本以任意顺序交换状态，有限轮内全部一致。
 * 全互联交换恰好 1 轮收敛；随机单消息投递在少量轮内收敛。
 */
class ConvergenceTest {

    private static final int REPLICAS = 5;

    @Test
    @DisplayName("G-Counter：全互联 1 轮收敛")
    void gCounterConvergesInOneFullRound() {
        GossipCluster<GCounter> cluster = new GossipCluster<>();
        for (int i = 0; i < REPLICAS; i++) {
            GCounter counter = new GCounter("node-" + i);
            for (int k = 0; k <= i; k++) {
                counter.increment(i + 1);
            }
            cluster.addReplica(counter);
        }

        int rounds = cluster.roundsToConverge(10);
        System.out.println("G-Counter 收敛轮次（全互联）=" + rounds);

        assertThat(rounds).isEqualTo(1);
        long expected = cluster.replicas().get(0).value();
        assertThat(cluster.replicas()).allSatisfy(c -> {
            assertThat(c.value()).isEqualTo(expected);
            assertThat(c).isEqualTo(cluster.replicas().get(0));
        });
    }

    @Test
    @DisplayName("PN-Counter：全互联 1 轮收敛，允许负值")
    void pnCounterConvergesInOneFullRound() {
        GossipCluster<PNCounter> cluster = new GossipCluster<>();
        for (int i = 0; i < REPLICAS; i++) {
            PNCounter counter = new PNCounter("node-" + i);
            counter.increment(10L * (i + 1));
            counter.decrement(4L * i);
            cluster.addReplica(counter);
        }

        int rounds = cluster.roundsToConverge(10);
        System.out.println("PN-Counter 收敛轮次（全互联）=" + rounds);

        assertThat(rounds).isEqualTo(1);
        long expected = cluster.replicas().get(0).value();
        assertThat(cluster.replicas()).allSatisfy(c -> {
            assertThat(c.value()).isEqualTo(expected);
            assertThat(c).isEqualTo(cluster.replicas().get(0));
        });
    }

    @Test
    @DisplayName("OR-Set：全互联 1 轮收敛，含增删")
    void orSetConvergesInOneFullRound() {
        GossipCluster<ORSet<String>> cluster = new GossipCluster<>();
        for (int i = 0; i < REPLICAS; i++) {
            ORSet<String> set = new ORSet<>("node-" + i);
            set.add("elem-" + i);
            if (i % 2 == 0) {
                set.add("shared");
            } else {
                set.add("shared");
                set.remove("shared");
            }
            cluster.addReplica(set);
        }

        int rounds = cluster.roundsToConverge(10);
        System.out.println("OR-Set 收敛轮次（全互联）=" + rounds);

        assertThat(rounds).isEqualTo(1);
        ORSet<String> reference = cluster.replicas().get(0);
        assertThat(cluster.replicas())
                .allSatisfy(s -> assertThat(s.elements()).isEqualTo(reference.elements()));
        // 偶数节点的并发新增 tag 未被奇数节点观测到，因此 shared 存活
        assertThat(reference.contains("shared")).isTrue();
    }

    @Test
    @DisplayName("随机顺序投递：三种 CRDT 均在有限轮内收敛")
    void randomGossipOrderConvergesForAllTypes() {
        Random seedRandom = new Random(20261004L);
        for (int trial = 0; trial < 5; trial++) {
            long seed = seedRandom.nextLong();
            int counterRounds = randomConvergeForCounters(seed);
            int setRounds = randomConvergeForOrSet(seed);
            System.out.println("随机投递 trial " + trial + " seed=" + seed
                    + "：计数器 " + counterRounds + " 轮，OR-Set " + setRounds + " 轮");

            assertThat(counterRounds).isLessThan(200);
            assertThat(setRounds).isLessThan(200);
        }
    }

    private int randomConvergeForCounters(long seed) {
        Random random = new Random(seed);
        GossipCluster<PNCounter> cluster = new GossipCluster<>();
        for (int i = 0; i < REPLICAS; i++) {
            cluster.addReplica(new PNCounter("node-" + i));
        }
        // 先在各副本本地做一轮增减，期间不通信
        for (int step = 0; step < 30; step++) {
            PNCounter target = cluster.replicas().get(random.nextInt(REPLICAS));
            if (random.nextBoolean()) {
                target.increment(random.nextInt(5) + 1);
            } else {
                target.decrement(random.nextInt(5) + 1);
            }
        }
        return runRandomUntilConverged(cluster, random);
    }

    private int randomConvergeForOrSet(long seed) {
        Random random = new Random(seed ^ 0x5DEECE66DL);
        GossipCluster<ORSet<String>> cluster = new GossipCluster<>();
        for (int i = 0; i < REPLICAS; i++) {
            cluster.addReplica(new ORSet<>("node-" + i));
        }
        for (int step = 0; step < 40; step++) {
            ORSet<String> target = cluster.replicas().get(random.nextInt(REPLICAS));
            String element = "e" + random.nextInt(8);
            if (random.nextBoolean()) {
                target.add(element);
            } else {
                target.remove(element);
            }
        }
        return runRandomUntilConverged(cluster, random);
    }

    private <T extends CvRdt<T>> int runRandomUntilConverged(GossipCluster<T> cluster, Random random) {
        for (int round = 0; round < 200; round++) {
            if (cluster.converged()) {
                return round;
            }
            cluster.randomRound(random);
        }
        throw new AssertionError("随机投递 200 轮后仍未收敛");
    }

    @Test
    @DisplayName("统计：收敛后各副本状态大小一致，合并次数与轮次可查询")
    void statsAreReportedPerReplicaAndCluster() {
        GossipCluster<GCounter> cluster = new GossipCluster<>();
        List<Integer> localMerges = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            GCounter counter = new GCounter("node-" + i);
            counter.increment(i + 1);
            cluster.addReplica(counter);
        }

        cluster.roundsToConverge(10);
        int expectedSize = cluster.replicas().get(0).stateSize();
        for (CrdtStats stats : cluster.stats()) {
            assertThat(stats.stateSize()).isEqualTo(expectedSize);
            // 全互联一轮中每个副本合并另外 2 个副本
            assertThat(stats.mergeCount()).isEqualTo(2);
            localMerges.add((int) stats.mergeCount());
        }
        assertThat(cluster.rounds()).isEqualTo(1);
        assertThat(cluster.mergeCount()).isEqualTo(6);
    }
}
