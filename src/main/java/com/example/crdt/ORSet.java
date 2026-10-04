package com.example.crdt;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * OR-Set（Observed-Remove Set，可增删集合），state-based CvRDT。
 *
 * <h3>数据模型</h3>
 * <ul>
 *   <li>{@code adds[element]}：为该元素产生的全部“新增唯一标记（tag）”，
 *       tag 形如 {@code replicaId:序号}，全局唯一；</li>
 *   <li>{@code removes[element]}：删除墓碑——删除操作执行时“已观测到”的 tag 集合；</li>
 *   <li>元素存在当且仅当存在至少一个新增 tag 不在墓碑集合中。</li>
 * </ul>
 *
 * <h3>合并</h3>
 * adds 与 removes 各自做集合并。并集天然满足交换/结合/幂等，因此乱序消息、
 * 重复消息、部分投递都不会影响最终收敛。
 *
 * <h3>并发增删同一元素的语义（add-wins）</h3>
 * 删除只移除<b>删除方已经观测到的</b> tag。因此：
 * <ul>
 *   <li>先见后删：B 已同步 A 对 x 的新增（tag tA），B 删除 x 会把 tA 放入墓碑，
 *       合并后 x 被删除（happen-before 顺序生效）；</li>
 *   <li>并发增删：A 新增 x（tag tA，B 尚未观测到），与此同时 B 删除 x，
 *       B 的墓碑不含 tA，合并后 x 仍然存在——并发删除不覆盖并发新增；</li>
 *   <li>删除后新增：删除之后再次 add 会产生全新 tag，新 tag 不在墓碑中，
 *       元素重新出现；</li>
 *   <li>两个副本并发新增同一元素：产生两个不同 tag，任一方删除只抹掉自己
 *       观测到的 tag，合并后另一方的新增仍然存活。</li>
 * </ul>
 *
 * <h3>压缩</h3>
 * {@link #compact()} 删除 adds 中已被墓碑完全覆盖的冗余 tag/条目
 * （墓碑保留），不改变任何查询结果，也不改变与任何远端状态合并后的查询结果。
 * {@link #drainDelta()} 把一段时间内的增删增量折叠为一个紧凑状态。
 */
public final class ORSet<E> implements CvRdt<ORSet<E>> {

    private final String replicaId;
    private long tagSequence;
    private final Map<E, Set<String>> adds;
    private final Map<E, Set<String>> removes;
    private long mergeCount;
    private ORSet<E> deltaBuffer;

    public ORSet(String replicaId) {
        this(replicaId, new HashMap<>(), new HashMap<>());
    }

    private ORSet(String replicaId, Map<E, Set<String>> adds, Map<E, Set<String>> removes) {
        this.replicaId = Objects.requireNonNull(replicaId, "replicaId");
        this.adds = adds;
        this.removes = removes;
    }

    public String replicaId() {
        return replicaId;
    }

    /** 新增元素：生成一个全局唯一 tag。对已存在的元素重复 add 也是合法的（产生新 tag）。 */
    public void add(E element) {
        Objects.requireNonNull(element, "element");
        String tag = replicaId + ':' + (++tagSequence);
        addTagTo(adds, element, tag);
        addTagTo(deltaBuffer().adds, element, tag);
    }

    /**
     * 删除元素：把本副本<b>当前已观测到</b>的全部新增 tag 记入墓碑。
     * 若元素当前不存在（没有可观测 tag），删除为空操作，不会误伤未来的并发新增。
     *
     * @return 实际加入墓碑的 tag 数量
     */
    public int remove(E element) {
        Objects.requireNonNull(element, "element");
        Set<String> observed = adds.get(element);
        if (observed == null || observed.isEmpty()) {
            return 0;
        }
        Set<String> tombstones = removes.computeIfAbsent(element, k -> new HashSet<>());
        ORSet<E> delta = deltaBuffer();
        int moved = 0;
        for (String tag : observed) {
            if (tombstones.add(tag)) {
                addTagTo(delta.removes, element, tag);
                moved++;
            }
        }
        return moved;
    }

    /** 元素当前是否在集合中：存在存活（未被墓碑覆盖）的新增 tag。 */
    public boolean contains(E element) {
        Set<String> alive = adds.get(element);
        if (alive == null || alive.isEmpty()) {
            return false;
        }
        Set<String> tombstones = removes.get(element);
        if (tombstones == null || tombstones.isEmpty()) {
            return true;
        }
        for (String tag : alive) {
            if (!tombstones.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /** 当前集合内容快照。 */
    public Set<E> elements() {
        Set<E> result = new HashSet<>();
        for (E element : adds.keySet()) {
            if (contains(element)) {
                result.add(element);
            }
        }
        return result;
    }

    @Override
    public void merge(ORSet<E> other) {
        other.adds.forEach((element, tags) ->
                adds.computeIfAbsent(element, k -> new HashSet<>()).addAll(tags));
        other.removes.forEach((element, tags) ->
                removes.computeIfAbsent(element, k -> new HashSet<>()).addAll(tags));
        mergeCount++;
    }

    /**
     * 状态压缩：从 adds 中剔除已被墓碑覆盖的 tag，删除空条目。
     * removes（墓碑）必须保留，否则无法让其它副本上的旧 tag 失效。
     */
    public void compact() {
        adds.forEach((element, tags) -> {
            Set<String> tombstones = removes.get(element);
            if (tombstones != null) {
                tags.removeAll(tombstones);
            }
        });
        adds.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /** 取出并清空增删增量缓冲区：多次 add/remove 被折叠为一个紧凑状态。 */
    public ORSet<E> drainDelta() {
        ORSet<E> delta = deltaBuffer;
        deltaBuffer = null;
        return delta == null ? new ORSet<>(replicaId) : delta;
    }

    private ORSet<E> deltaBuffer() {
        if (deltaBuffer == null) {
            deltaBuffer = new ORSet<>(replicaId);
        }
        return deltaBuffer;
    }

    private static <E> void addTagTo(Map<E, Set<String>> target, E element, String tag) {
        target.computeIfAbsent(element, k -> new HashSet<>()).add(tag);
    }

    @Override
    public ORSet<E> copy() {
        Map<E, Set<String>> addsCopy = new HashMap<>();
        adds.forEach((e, tags) -> addsCopy.put(e, new HashSet<>(tags)));
        Map<E, Set<String>> removesCopy = new HashMap<>();
        removes.forEach((e, tags) -> removesCopy.put(e, new HashSet<>(tags)));
        return new ORSet<>(replicaId, addsCopy, removesCopy);
    }

    @Override
    public int stateSize() {
        return tagCount(adds) + tagCount(removes);
    }

    private static <E> int tagCount(Map<E, Set<String>> map) {
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
        return adds.equals(other.adds) && removes.equals(other.removes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adds, removes);
    }

    @Override
    public String toString() {
        return "ORSet" + elements();
    }

    /** 内部只读视图，便于测试与诊断墓碑结构。 */
    Map<E, Set<String>> rawAdds() {
        return Collections.unmodifiableMap(adds);
    }

    Map<E, Set<String>> rawRemoves() {
        return Collections.unmodifiableMap(removes);
    }
}
