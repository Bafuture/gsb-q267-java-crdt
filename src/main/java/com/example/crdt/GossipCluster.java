package com.example.crdt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 反熵（anti-entropy）收敛模拟器：一组副本通过两两交换状态收敛。
 *
 * <p>用于验证 CvRDT 的收敛性并统计轮次与合并次数。提供两种交换策略：</p>
 * <ul>
 *   <li>{@link #round()}：全互联一轮——每个副本接收所有其它副本的状态快照，
 *       更新停止后至多 1 轮即收敛；</li>
 *   <li>{@link #randomRound(Random)}：随机选一对副本单向投递一次状态，
 *       模拟任意顺序、可重复的消息交换，有限轮内必然收敛。</li>
 * </ul>
 */
public final class GossipCluster<T extends CvRdt<T>> {

    private final List<T> replicas = new ArrayList<>();
    private long mergeCount;
    private int rounds;

    public void addReplica(T replica) {
        replicas.add(Objects.requireNonNull(replica));
    }

    public List<T> replicas() {
        return Collections.unmodifiableList(replicas);
    }

    /** 全互联一轮：基于轮首快照，每个副本合并所有其它副本的状态。 */
    public void round() {
        List<T> snapshot = new ArrayList<>(replicas.size());
        for (T replica : replicas) {
            snapshot.add(replica.copy());
        }
        for (int i = 0; i < replicas.size(); i++) {
            for (int j = 0; j < replicas.size(); j++) {
                if (i != j) {
                    replicas.get(i).merge(snapshot.get(j));
                    mergeCount++;
                }
            }
        }
        rounds++;
    }

    /** 随机一轮：执行 {@code replicas.size()} 次随机配对单向投递（允许重复对）。 */
    public void randomRound(Random random) {
        int n = replicas.size();
        if (n < 2) {
            return;
        }
        for (int k = 0; k < n; k++) {
            int from = random.nextInt(n);
            int to = random.nextInt(n);
            if (from != to) {
                replicas.get(to).merge(replicas.get(from).copy());
                mergeCount++;
            }
        }
        rounds++;
    }

    /** 所有副本状态是否两两相等（已收敛）。 */
    public boolean converged() {
        for (int i = 1; i < replicas.size(); i++) {
            if (!replicas.get(0).equals(replicas.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** 反复执行全互联轮直到收敛，返回所用轮次；超过 maxRounds 仍未收敛则抛异常。 */
    public int roundsToConverge(int maxRounds) {
        int used = 0;
        while (!converged()) {
            if (used >= maxRounds) {
                throw new IllegalStateException("超过 " + maxRounds + " 轮仍未收敛");
            }
            round();
            used++;
        }
        return used;
    }

    /** 已执行的合并总次数。 */
    public long mergeCount() {
        return mergeCount;
    }

    /** 已执行的交换轮次。 */
    public int rounds() {
        return rounds;
    }

    /** 汇总各副本统计（状态大小、合并次数）。 */
    public List<CrdtStats> stats() {
        List<CrdtStats> result = new ArrayList<>();
        for (int i = 0; i < replicas.size(); i++) {
            T replica = replicas.get(i);
            result.add(new CrdtStats("replica-" + i, replica.stateSize(), replica.mergeCount()));
        }
        return result;
    }
}
