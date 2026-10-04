package com.example.crdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 状态压缩测试：多次增量被折叠为紧凑状态，压缩后合并结果与逐条合并完全一致。
 */
class DeltaCompressionTest {

    @Test
    @DisplayName("G-Counter：N 次增量压缩为单条状态，合并结果不变")
    void gCounterDeltaCompressionPreservesMergeResult() {
        GCounter source = new GCounter("A");
        List<GCounter> individualDeltas = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            source.increment(2);
            individualDeltas.add(source.drainDelta());
        }
        assertThat(individualDeltas).hasSize(50);

        // 逐条合并 50 个增量
        GCounter byIndividualDeltas = new GCounter("sub");
        individualDeltas.forEach(byIndividualDeltas::merge);

        // 压缩：50 次增量折叠为 1 个紧凑状态
        GCounter compressedSource = new GCounter("A");
        for (int i = 0; i < 50; i++) {
            compressedSource.increment(2);
        }
        GCounter compressed = compressedSource.drainDelta();
        assertThat(compressed.stateSize()).isEqualTo(1);

        GCounter byCompressed = new GCounter("sub");
        byCompressed.merge(compressed);

        assertThat(byCompressed).isEqualTo(byIndividualDeltas);
        assertThat(byCompressed.value()).isEqualTo(100);
    }

    @Test
    @DisplayName("PN-Counter：增减混合增量压缩后合并结果不变")
    void pnCounterDeltaCompressionPreservesMergeResult() {
        PNCounter source = new PNCounter("A");
        List<PNCounter> individualDeltas = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            source.increment(3);
            source.decrement(1);
            individualDeltas.add(source.drainDelta());
        }

        PNCounter byIndividualDeltas = new PNCounter("sub");
        individualDeltas.forEach(byIndividualDeltas::merge);

        PNCounter compressedSource = new PNCounter("A");
        for (int i = 0; i < 30; i++) {
            compressedSource.increment(3);
            compressedSource.decrement(1);
        }
        PNCounter compressed = compressedSource.drainDelta();

        PNCounter byCompressed = new PNCounter("sub");
        byCompressed.merge(compressed);

        assertThat(byCompressed).isEqualTo(byIndividualDeltas);
        assertThat(byCompressed.value()).isEqualTo(60);
    }

    @Test
    @DisplayName("OR-Set：增删增量压缩后合并结果不变")
    void orSetDeltaCompressionPreservesMergeResult() {
        ORSet<String> source = new ORSet<>("A");
        List<ORSet<String>> individualDeltas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            source.add("e" + (i % 5));
            if (i % 3 == 0) {
                source.remove("e" + (i % 5));
            }
            individualDeltas.add(source.drainDelta());
        }

        ORSet<String> byIndividualDeltas = new ORSet<>("sub");
        individualDeltas.forEach(byIndividualDeltas::merge);

        ORSet<String> compressedSource = new ORSet<>("A");
        for (int i = 0; i < 20; i++) {
            compressedSource.add("e" + (i % 5));
            if (i % 3 == 0) {
                compressedSource.remove("e" + (i % 5));
            }
        }
        ORSet<String> compressed = compressedSource.drainDelta();

        ORSet<String> byCompressed = new ORSet<>("sub");
        byCompressed.merge(compressed);

        assertThat(byCompressed).isEqualTo(byIndividualDeltas);
        assertThat(byCompressed.elements()).isEqualTo(byIndividualDeltas.elements());
    }

    @Test
    @DisplayName("OR-Set compact()：状态变小，与远端合并后的查询结果不变")
    void compactDoesNotChangeMergeOutcome() {
        ORSet<String> local = new ORSet<>("A");
        for (int i = 0; i < 10; i++) {
            local.add("e" + i);
        }
        for (int i = 0; i < 7; i++) {
            local.remove("e" + i);
        }
        int sizeBefore = local.stateSize();

        ORSet<String> remote = new ORSet<>("B");
        remote.add("remote-only");
        remote.merge(local.copy()); // remote 观测到本地状态

        ORSet<String> mergedBeforeCompact = remote.copy();
        mergedBeforeCompact.merge(local.copy());

        local.compact();
        assertThat(local.stateSize()).isLessThan(sizeBefore);

        ORSet<String> mergedAfterCompact = remote.copy();
        mergedAfterCompact.merge(local.copy());

        // 压缩后合并：元素集合一致，且墓碑仍能让 remote 上的旧 tag 失效
        assertThat(mergedAfterCompact.elements()).isEqualTo(mergedBeforeCompact.elements());
        assertThat(mergedAfterCompact.elements()).containsExactlyInAnyOrder("e7", "e8", "e9", "remote-only");
    }

    @Test
    @DisplayName("压缩增量可重复、可乱序投递")
    void compressedDeltasAreIdempotentAndReorderable() {
        GCounter source = new GCounter("A");
        source.increment(5);
        GCounter delta1 = source.drainDelta();
        source.increment(7);
        GCounter delta2 = source.drainDelta();

        GCounter inOrder = new GCounter("sub");
        inOrder.merge(delta1);
        inOrder.merge(delta2);

        GCounter disordered = new GCounter("sub");
        disordered.merge(delta2);
        disordered.merge(delta1);
        disordered.merge(delta2); // 重复投递
        disordered.merge(delta1);

        assertThat(disordered).isEqualTo(inOrder);
        assertThat(disordered.value()).isEqualTo(12);
    }
}
