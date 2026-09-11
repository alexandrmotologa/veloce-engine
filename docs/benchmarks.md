# Benchmarking Methodology and Performance Targets

This document outlines the benchmarking setup, measurement techniques, and operating system / JVM tuning for VeloceEngine.

## Measurement Goals

The primary performance targets on modern x86-64 hardware (such as AMD Zen 4 or Intel Raptor Lake) are:

- Sustained matching throughput: Greater than 5,000,000 orders/sec.
- Hot-path allocation: Exactly 0 bytes per order processed.
- 50th percentile latency (p50): Under 1.0 microsecond.
- 99th percentile latency (p99): Under 3.5 microseconds.
- 99.9th percentile latency (p99.9): Under 6.0 microseconds.

## JMH Harness Structure

The benchmark suite lives under `src/test/java/com/engine/veloce/benchmark/MatchingBenchmark.java`.

Key practices followed:

1. **Pre-generated inputs**: Random order streams are pre-allocated in memory arrays prior to benchmark iterations. This guarantees that random number generation, Gaussian distribution math, and data structure creation do not pollute the measurement loop.
2. **Blackhole consumption**: Results from matching operations are consumed via `org.openjdk.jmh.infra.Blackhole` to prevent the Just-In-Time (JIT) compiler from optimizing away side-effect-free code (dead code elimination).
3. **GC Profiler**: The benchmark runs with `-prof gc` to verify normalized allocation rates down to 0 bytes/operation.

## JVM Tuning Flags

When running benchmarks or production deployments, use the following JVM options:

```bash
java \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+UseEpsilonGC \
  -XX:-RestrictContended \
  -XX:+AlwaysPreTouch \
  -Xms4g -Xmx4g \
  -XX:+TieredCompilation \
  -XX:CICompilerCount=4 \
  -jar target/benchmarks.jar
```

Explanation of flags:
- `-XX:+UseEpsilonGC`: A no-op garbage collector. If the engine performs zero allocations on the hot path, memory usage remains flat. If an unintended leak occurs, the JVM will eventually throw an OutOfMemoryError, immediately catching regressions.
- Alternatively, `-XX:+UseZGC -XX:+ZGenerational`: Low-latency concurrent garbage collector for environments where occasional background setup allocations occur.
- `-XX:-RestrictContended`: Enables `@jdk.internal.vm.annotation.Contended` on user classes to enforce 64-byte CPU cache line padding.
- `-XX:+AlwaysPreTouch`: Pre-faults all allocated pages in physical RAM at startup, eliminating page fault overhead during order execution.
- `-Xms4g -Xmx4g`: Sets fixed initial and maximum heap size to avoid runtime heap expansions.
