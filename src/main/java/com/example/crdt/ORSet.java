package com.example.crdt;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * OR-Set（Observed-Remove Set，可增删集合），add-wins 语义。
 *
 * <h2>数据结构</h2>
 * <ul>
 *   <li>{@code adds[e]}：元素 e 所有新增操作生成的唯一标签（tag）集合；</li>
 *   <li>{@code removes[e]}：删除操作已“观察到”的标签集合（墓碑）。</li>
 * </ul>
 * 元素 e 在场，当且仅当 {@code adds[e]} 中存在一个不在 {@code removes[e]} 里的标签。
 *
 * <h2>合并</h2>
 * 对 adds 与 removes 分别做集合并集。并集天然满足交换律、结合律、幂等性，
 * 因此乱序、重复合并结果一致，且任何未被删除观察到的新增标签都不会丢失。
 *
 * <h2>并发增删同一元素的语义</h2>
 * 删除只会清除该副本<b>已经观察到</b>的新增标签。若另一副本并发新增同一元素，
 * 该新增携带一个删除方从未见过的全新标签；合并后墓碑集合无法覆盖它，
 * 所以<b>并发删除不会覆盖并发新增</b>（add-wins）。删除之后的再新增同样使用
 * 新标签，不会被旧墓碑误伤。
 *
 * @param <E> 元素类型
 */
public final class ORSet<E> implements Crdt<ORSet<E>> {

    private final String replicaId;
    private final Map<E, Set<String>> adds;
    private final Map<E, Set<String>> removes;
    private long mergeCount;
    private long tagSequence;

    public ORSet(String replicaId) {
        this(replicaId, new HashMap<>(), new HashMap<>());
    }

    private ORSet(String replicaId,
                  Map<E, Set<String>> adds,
                  Map<E, Set<String>> removes) {
        this.replicaId = Objects.requireNonNull(replicaId, "replicaId");
        this.adds = adds;
        this.removes = removes;
    }

    public String replicaId() {
        return replicaId;
    }

    /**
     * 新增元素：生成一个全局唯一标签 {@code replicaId:seq} 并加入 adds。
     */
    public void add(E element) {
        adds.computeIfAbsent(element, k -> new LinkedHashSet<>()).add(nextTag());
    }

    /**
     * 删除元素：把本副本已观察到的该元素全部新增标签加入 removes。
     * 没有观察到的（例如并发新增产生的）标签不受影响。
     */
    public void remove(E element) {
        Set<String> observed = adds.get(element);
        if (observed == null || observed.isEmpty()) {
            return;
        }
        removes.computeIfAbsent(element, k -> new LinkedHashSet<>()).addAll(observed);
    }

    /** 元素是否在场：存在一个未被墓碑覆盖的新增标签。 */
    public boolean contains(E element) {
        Set<String> live = adds.get(element);
        if (live == null || live.isEmpty()) {
            return false;
        }
        Set<String> tombstoned = removes.get(element);
        if (tombstoned == null) {
            return true;
        }
        for (String tag : live) {
            if (!tombstoned.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /** 当前在场的全部元素。 */
    public Set<E> elements() {
        Set<E> result = new LinkedHashSet<>();
        for (E element : adds.keySet()) {
            if (contains(element)) {
                result.add(element);
            }
        }
        return result;
    }

    private String nextTag() {
        return replicaId + ':' + (++tagSequence) + ':' + UUID.randomUUID();
    }

    @Override
    public void merge(ORSet<E> other) {
        mergeTagMap(adds, other.adds);
        mergeTagMap(removes, other.removes);
        mergeCount++;
    }

    private static <E> void mergeTagMap(Map<E, Set<String>> target, Map<E, Set<String>> source) {
        for (Map.Entry<E, Set<String>> entry : source.entrySet()) {
            target.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>())
                    .addAll(entry.getValue());
        }
    }

    /**
     * 状态压缩：把多个增量状态合并成一个紧凑状态，
     * 压缩后合并与逐个合并原始增量结果一致。
     */
    public static <E> ORSet<E> compress(String compressedId, Collection<ORSet<E>> deltas) {
        ORSet<E> compact = new ORSet<>(compressedId);
        for (ORSet<E> delta : deltas) {
            compact.merge(delta);
        }
        return compact;
    }

    @Override
    public ORSet<E> copy() {
        return new ORSet<>(replicaId, deepCopy(adds), deepCopy(removes));
    }

    private static <E> Map<E, Set<String>> deepCopy(Map<E, Set<String>> source) {
        Map<E, Set<String>> copy = new HashMap<>();
        for (Map.Entry<E, Set<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    @Override
    public int stateSize() {
        return countTags(adds) + countTags(removes);
    }

    private static <E> int countTags(Map<E, Set<String>> map) {
        return map.values().stream().mapToInt(Set::size).sum();
    }

    @Override
    public long mergeCount() {
        return mergeCount;
    }

    public CrdtStats stats() {
        return new CrdtStats(replicaId, stateSize(), mergeCount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ORSet<?> other)) {
            return false;
        }
        return new HashSet<>(adds.entrySet()).equals(new HashSet<>(other.adds.entrySet()))
                && new HashSet<>(removes.entrySet()).equals(new HashSet<>(other.removes.entrySet()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(new HashSet<>(adds.entrySet()), new HashSet<>(removes.entrySet()));
    }

    @Override
    public String toString() {
        return "ORSet" + elements();
    }
}
