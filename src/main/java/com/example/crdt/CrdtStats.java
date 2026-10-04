package com.example.crdt;

/**
 * 单个副本的运行统计快照。
 *
 * @param replicaId  副本标识
 * @param stateSize  状态大小（逻辑条目数）
 * @param mergeCount 该副本已执行的合并次数
 */
public record CrdtStats(String replicaId, int stateSize, long mergeCount) {

    @Override
    public String toString() {
        return "CrdtStats{replica=" + replicaId
                + ", stateSize=" + stateSize
                + ", mergeCount=" + mergeCount + "}";
    }
}
