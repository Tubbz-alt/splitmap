package uk.co.openkappa.splitmap;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static java.lang.Long.numberOfTrailingZeros;
import static java.util.stream.Collector.Characteristics.*;

class IndexAggregator<Filter, T> implements Collector<PrefixIndex<Slice<Filter, T>>, PrefixIndex<T>, PrefixIndex<T>> {

  private final Function<Slice<Filter, T>, T> circuit;

  // no two threads will ever write to the same partition because mixin the spliterator on the PrefixIndex
  private final PrefixIndex<T> target = new PrefixIndex<>();

  public IndexAggregator(Function<Slice<Filter, T>, T> circuit) {
    this.circuit = circuit;
  }

  @Override
  public Supplier<PrefixIndex<T>> supplier() {
    return () -> target;
  }

  @Override
  public BiConsumer<PrefixIndex<T>, PrefixIndex<Slice<Filter, T>>> accumulator() {
    return (l, r) -> {
      Object[] chunkIn;
      T[] chunkOut = (T[]) new Object[Long.SIZE];
      for (int i = r.getMinChunkIndex(); i < r.getMaxChunkIndex(); ++i) {
        long keyMask = r.readKeyWord(i);
        if (keyMask != 0) {
          chunkIn = r.getChunkNoCopy(i);
          long temp = keyMask;
          while (temp != 0) {
            int j = numberOfTrailingZeros(temp);
            T reduced = circuit.apply((Slice<Filter, T>) chunkIn[j]);
            if (null != reduced) {
              chunkOut[j] = reduced;
            } else {
              keyMask &= (temp - 1);
            }
            temp &= (temp - 1);
          }
          l.writeChunk(i, keyMask, chunkOut);
        }
      }
    };
  }

  @Override
  public BinaryOperator<PrefixIndex<T>> combiner() {
    return (l, r) -> l;
  }

  @Override
  public Function<PrefixIndex<T>, PrefixIndex<T>> finisher() {
    return Function.identity();
  }

  @Override
  public Set<Characteristics> characteristics() {
    return EnumSet.of(UNORDERED, IDENTITY_FINISH, CONCURRENT);
  }
}
