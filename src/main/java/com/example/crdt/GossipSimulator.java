package com.example.crdt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Gossip 收敛模拟器：在一组副本间按随机顺序交换状态，统计收敛轮次。
 *
 * <p>每一轮：将副本随机打乱，两两配对，配对双方<b>双向</b>合并对方状态
 * （消息在发送时快照，可乱序、可重复）。每轮结束检查所有副本状态是否一致。
 * 由于 merge 满足交换律、结合律、幂等性，有限轮后必然收敛。</p>
 */
public final class GossipSimulator {

    /** 模拟结果：收敛轮次与是否收敛。 */
    public record Result(int rounds, boolean converged) {
    }

    private GossipSimulator() {
    }

    public static <T extends Crdt<T>> Result simulate(List<T> replicas,
                                                      int maxRounds,
                                                      Random random) {
        if (replicas.size() < 2) {
            throw new IllegalArgumentException("need at least 2 replicas");
        }
        for (int round = 1; round <= maxRounds; round++) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < replicas.size(); i++) {
                order.add(i);
            }
            Collections.shuffle(order, random);

            for (int k = 0; k + 1 < order.size(); k += 2) {
                T left = replicas.get(order.get(k));
                T right = replicas.get(order.get(k + 1));
                T leftSnapshot = left.copy();
                T rightSnapshot = right.copy();
                // 双向推送，顺序随机，模拟乱序消息
                if (random.nextBoolean()) {
                    left.merge(rightSnapshot);
                    right.merge(leftSnapshot);
                } else {
                    right.merge(leftSnapshot);
                    left.merge(rightSnapshot);
                }
            }
            if (allEqual(replicas)) {
                return new Result(round, true);
            }
        }
        return new Result(maxRounds, allEqual(replicas));
    }

    private static boolean allEqual(List<?> replicas) {
        Object first = replicas.get(0);
        for (int i = 1; i < replicas.size(); i++) {
            if (!first.equals(replicas.get(i))) {
                return false;
            }
        }
        return true;
    }
}
