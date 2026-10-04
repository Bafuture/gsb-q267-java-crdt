package com.example.crdt;

/**
 * 单个副本的统计快照：副本标识、状态规模、累计合并次数。
 *
 * @param replicaId  副本标识
 * @param stateSize  状态规模（计数器为副本条目数；OR-Set 为标签总数）
 * @param mergeCount 累计合并次数
 */
public record CrdtStats(String replicaId, int stateSize, long mergeCount) {

    @Override
    public String toString() {
        return replicaId + "{stateSize=" + stateSize + ", mergeCount=" + mergeCount + '}';
    }
}
