package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 并发增删同一元素的语义验证（OR-Set 为 add-wins 语义）：
 * 删除只作用于删除方已观测到的唯一标记，并发产生的新增标记不受墓碑影响。
 */
class ConcurrentSemanticsTest {

    @Test
    @DisplayName("并发删除不覆盖并发新增（add-wins）")
    void concurrentRemoveDoesNotOverrideConcurrentAdd() {
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");

        // 双方先同步到都含有 x
        a.add("x");
        b.merge(a);
        a.merge(b);
        assertThat(b.contains("x")).isTrue();

        // 网络分区期间：B 删除 x，A 并发重新新增 x（产生 B 未观测的新 tag）
        b.remove("x");
        a.add("x");

        // 网络恢复后合并：A 的并发新增存活
        a.merge(b);
        b.merge(a);
        assertThat(a.contains("x")).isTrue();
        assertThat(b.contains("x")).isTrue();
    }

    @Test
    @DisplayName("先见后删：观测到新增后的删除在合并后生效")
    void observedRemoveWinsOverEarlierAdd() {
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");

        a.add("x");
        b.merge(a);   // B 观测到 A 的新增
        b.remove("x"); // 删除已观测的 tag

        a.merge(b);
        assertThat(a.contains("x")).isFalse();
        assertThat(b.contains("x")).isFalse();
    }

    @Test
    @DisplayName("并发双向新增同一元素，单方删除后另一方新增仍存活")
    void concurrentAddsFromTwoReplicasSurviveSingleRemove() {
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");

        a.add("x"); // tag A:1
        b.add("x"); // tag B:1，B 未观测 A:1
        b.remove("x"); // 只抹掉 B:1

        a.merge(b);
        b.merge(a);
        assertThat(a.contains("x")).isTrue();
        assertThat(b.contains("x")).isTrue();
    }

    @Test
    @DisplayName("删除后再次新增（新 tag）使元素复活")
    void addAfterRemoveCreatesFreshTag() {
        ORSet<String> a = new ORSet<>("A");
        ORSet<String> b = new ORSet<>("B");

        a.add("x");
        b.merge(a);
        b.remove("x");
        a.merge(b);
        assertThat(a.contains("x")).isFalse();

        b.add("x"); // 删除之后的新增产生全新 tag
        a.merge(b);
        assertThat(a.contains("x")).isTrue();
    }

    @Test
    @DisplayName("并发语义在计数器上：并发增减按副本分别累计，互不覆盖")
    void counterConcurrentUpdatesAccumulate() {
        PNCounter a = new PNCounter("A");
        PNCounter b = new PNCounter("B");

        a.increment(5);
        b.decrement(2);

        a.merge(b);
        b.merge(a);
        assertThat(a.value()).isEqualTo(3);
        assertThat(b.value()).isEqualTo(3);
    }
}
