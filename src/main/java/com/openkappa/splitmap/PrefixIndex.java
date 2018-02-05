package com.openkappa.splitmap;

import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongBinaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PrefixIndex<T> {

  private static final int PARTITIONS;
  static {
    PARTITIONS = Runtime.getRuntime().availableProcessors();
  }
  private static final int PARTITION_SIZE = (1 << 10) / PARTITIONS;

  private final long[] keys;
  private final ChunkedArray<T> values;
  private final int offset;
  private final int range;

  public PrefixIndex() {
    this(0, 1 << 10);
  }

  PrefixIndex(long[] keys, ChunkedArray<T> values, int offset, int range) {
    this.keys = keys;
    this.values = values;
    this.offset = offset;
    this.range = range;
  }

  PrefixIndex(int offset, int range) {
    this(new long[1 << 10], new ChunkedArray<>(), offset, range);
  }

  public int getMinChunkIndex() {
    return offset;
  }

  public int getMaxChunkIndex() {
    return offset + range;
  }

  public T get(short key) {
    int pos = key & 0xFFFF;
    if ((keys[pos >>> 6] & (1 << pos)) != 0) {
      return values.get(pos);
    }
    return null;
  }

  public void insert(short key, T value) {
    int pos = key & 0xFFFF;
    keys[pos >>> 6] |= (1L << pos);
    values.put(pos, value);
  }

  public Stream<PrefixIndex<T>> streamUniformPartitions() {
    return StreamSupport.stream(new UniformSpliterator<>(0, PARTITION_SIZE, this, PARTITIONS), true);
  }

  public Stream<T> stream() {
    return IntStream.range(offset, offset + range)
                    .filter(i -> keys[i] != 0)
                    .mapToObj(values::streamChunk)
                    .flatMap(Function.identity());
  }

  public void writeChunk(int wordIndex, long word, T[] chunk) {
    keys[wordIndex] = word;
    if (word != 0) {
      values.writeChunk(wordIndex, chunk);
    }
  }

  public long contributeToKey(int wordIndex, long value, LongBinaryOperator op) {
    return op.applyAsLong(keys[wordIndex], value);
  }

  public boolean readChunk(int chunkIndex, T[] ouptut) {
    return values.readChunk(chunkIndex, ouptut);
  }

  private static class UniformSpliterator<T> implements Spliterator<PrefixIndex<T>> {

    private final PrefixIndex<T> index;
    private int units;
    private Queue<PrefixIndex<T>> work;

    private UniformSpliterator(int offset, int range, PrefixIndex<T> index, int units) {
      this.index = index;
      this.units = units;
      this.work = new ArrayBlockingQueue<>(units);
      for (int i = 0; i < units; ++i) {
        work.offer(new PrefixIndex<>(index.keys, index.values, offset * i, range));
      }
    }

    private UniformSpliterator(PrefixIndex<T> index, Queue<PrefixIndex<T>> work) {
      this.index = index;
      this.units = 1;
      this.work = work;
    }

    @Override
    public boolean tryAdvance(Consumer<? super PrefixIndex<T>> action) {
      PrefixIndex<T> next = work.poll();
      if (null != next) {
        action.accept(next);
        return true;
      }
      return false;
    }

    @Override
    public Spliterator<PrefixIndex<T>> trySplit() {
      if (units == 1) {
        return null;
      }
      --units;
      return new UniformSpliterator<>(index, work);
    }

    @Override
    public long estimateSize() {
      return units;
    }

    @Override
    public int characteristics() {
      return Spliterator.SIZED
              | Spliterator.IMMUTABLE
              | Spliterator.ORDERED
              | Spliterator.SUBSIZED;
    }
  }


}
