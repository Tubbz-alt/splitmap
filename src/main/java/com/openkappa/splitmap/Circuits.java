package com.openkappa.splitmap;

import org.roaringbitmap.ArrayContainer;
import org.roaringbitmap.Container;

import java.util.*;
import java.util.function.*;
import java.util.stream.IntStream;

import static java.lang.Long.lowestOneBit;
import static java.lang.Long.numberOfTrailingZeros;
import static java.util.stream.Collectors.toList;

public class Circuits {

  private static final Container EMPTY = new ArrayContainer();

  public static SplitMap evaluateIfKeysIntersect(Function<List<Container>, Container> circuit, SplitMap... splitMaps) {
    PrefixIndex<Container>[] indices = Arrays.stream(splitMaps).map(SplitMap::getIndex).toArray(PrefixIndex[]::new);
    return new SplitMap(groupByIntersectingKeys(EMPTY, indices)
            .streamUniformPartitions()
            .collect(new IndexAggregator<>(circuit)));
  }


  public static SplitMap evaluate(Function<List<Container>, Container> circuit, SplitMap... splitMaps) {
    PrefixIndex<Container>[] indices = Arrays.stream(splitMaps).map(SplitMap::getIndex).toArray(PrefixIndex[]::new);
    return new SplitMap(groupByKey(EMPTY, indices)
            .streamUniformPartitions()
            .collect(new IndexAggregator<>(circuit)));
  }

  public static <T, U> PrefixIndex<List<U>> groupByKey(T defaultValue, Function<T, U> map, PrefixIndex<T>... indices) {
    return groupByKey((x, y) -> x | y, 0L, defaultValue, map, indices);
  }

  public static <T> PrefixIndex<List<T>> groupByKey(T defaultValue, PrefixIndex<T>... indices) {
    return groupByKey(defaultValue, Function.identity(), indices);
  }

  public static <T, U> PrefixIndex<List<U>> groupByIntersectingKeys(T defaultValue, Function<T, U> map, PrefixIndex<T>... indices) {
    return groupByKey((x, y) -> x & y, -1L, defaultValue, map, indices);
  }

  public static <T> PrefixIndex<List<T>> groupByIntersectingKeys(T defaultValue, PrefixIndex<T>... indices) {
    return groupByIntersectingKeys(defaultValue, Function.identity(), indices);
  }

  private static <T, U> PrefixIndex<List<U>> groupByKey(LongBinaryOperator op,
                                                        long identity,
                                                        T defaultValue,
                                                        Function<T, U> map,
                                                        PrefixIndex<T>... indices) {
    PrefixIndex<List<U>> grouped = new PrefixIndex<>();
    List<U> prototype = IntStream.range(0, indices.length).mapToObj(i -> defaultValue).map(map).collect(toList());
    T[] column = (T[])new Object[Long.SIZE];
    for (int i = 0; i < 1 << 10; ++i) {
      long word = identity;
      for (PrefixIndex<T> index : indices) {
        word = index.contributeToKey(i, word, op);
      }
      if (word != 0) {
        List<U>[] chunk = new List[Long.SIZE];
        int k = 0;
        for (PrefixIndex<T> index : indices) {
          index.readChunk(i, column);
          long mask = word;
          while (mask != 0) {
            int j = numberOfTrailingZeros(mask);
            if (null != column[j]) {
              if (null == chunk[j]) {
                chunk[j] = new ArrayList<>(prototype);
                grouped.insert((short)(i * Long.SIZE + j), chunk[j]);
              }
              chunk[j].set(k, map.apply(column[j]));
            }
            mask ^= lowestOneBit(mask);
          }
          ++k;
        }
      }
    }
    return grouped;
  }

}
