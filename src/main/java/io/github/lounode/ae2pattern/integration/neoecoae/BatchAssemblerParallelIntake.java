package io.github.lounode.ae2pattern.integration.neoecoae;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;

import io.github.lounode.ae2pattern.common.block.entity.BatchAssemblerBlockEntity;

/**
 * Presents the batch assembler's parallel intake to NEO ECO.
 *
 * <p>An adapter rather than the machine implementing the interface itself: NEO ECO is an optional dependency,
 * so the machine must not name a type from it. Everything here forwards to the machine, which is what the
 * mixin this replaced did as well - the difference is that nothing is added to the machine's class, so the
 * machine loads identically in a pack that ships no NEO ECO.</p>
 *
 * <p>A fresh instance per lookup is fine: NEO ECO asks for the contract while dispatching, and the registered
 * factory is what keeps the per-instance lookup from needing the class to implement anything.</p>
 * <p>The job id NEO ECO passes is deliberately not used: this machine books the batch against its own buffer and
 * answers for one hand-over at a time, so it has nothing to attribute the job to. The ECO side carries the id
 * for its own accounting.</p>
 *
 * <p>Each lookup builds a new instance. The registration is keyed by class and asked for while jobs are
 * running, so there is nowhere sensible to keep one, and an instance is a machine reference and nothing else.</p>
 */
final class BatchAssemblerParallelIntake implements ECOParallelCraftingProvider {

    private final BatchAssemblerBlockEntity machine;

    BatchAssemblerParallelIntake(BatchAssemblerBlockEntity machine) {
        this.machine = machine;
    }

    @Override
    public int eco$getAvailableParallelSlots() {
        return machine.availableParallelSlots();
    }

    @Override
    public boolean eco$pushPatternBatch(
            IPatternDetails pattern, KeyCounter[] inputTotal, long craftCount, @Nullable UUID craftingJobId) {
        return machine.acceptPatternBatch(pattern, inputTotal, craftCount);
    }
}
