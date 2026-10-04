package com.example.crdt;

/**
 * 状态式 CRDT（CvRDT）公共契约。
 *
 * <p>实现必须保证 merge 满足交换律、结合律与幂等性，
 * 因此任意副本以任意顺序、任意重复次数交换状态后都会收敛到同一状态。</p>
 *
 * @param <T> 自身类型（CRDT 只能与同类型合并）
 */
public interface Crdt<T extends Crdt<T>> {

    /** 将 other 的状态合并进本副本。 */
    void merge(T other);

    /** 返回本状态的深拷贝，拷贝与原对象互不影响。 */
    T copy();

    /** 状态规模（条目数），用于统计各副本状态大小。 */
    int stateSize();

    /** 本副本累计执行的合并次数。 */
    long mergeCount();
}
