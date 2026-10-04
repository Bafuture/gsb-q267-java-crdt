package com.example.crdt;

/**
 * 状态型 CRDT（CvRDT，收敛复制数据类型）公共契约。
 *
 * <p>实现必须保证 merge 满足交换律、结合律与幂等性，因此任意副本以任意顺序、
 * 任意次数交换状态后，所有副本在有限轮内收敛到相同状态，无需中心协调。</p>
 *
 * @param <T> 自身类型（CRDT 只能与同类型合并）
 */
public interface CvRdt<T extends CvRdt<T>> {

    /** 将 other 的状态并入本副本。必须满足幂等、交换、结合。 */
    void merge(T other);

    /** 返回当前状态的深拷贝，拷贝的修改不影响本副本。 */
    T copy();

    /** 状态大小的逻辑度量（条目数），用于统计与压缩效果对比。 */
    int stateSize();

    /** 本副本已执行的 merge 次数。 */
    long mergeCount();
}
