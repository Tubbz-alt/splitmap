package com.openkappa.splitmap;

import org.openjdk.jmh.annotations.*;
import org.roaringbitmap.ArrayContainer;
import org.roaringbitmap.Container;

import java.util.stream.IntStream;

import static com.openkappa.splitmap.DataGenerator.randomSplitmap;

@State(Scope.Benchmark)
public class CircuitBenchmarks {

  @Param("0.33")
  double runniness;
  @Param("0.33")
  double dirtiness;
  @Param({"64", "256", "512"})
  int keys;
  @Param({"100", "1000"})
  int count;

  SplitMap[] splitMaps;

  @Setup(Level.Trial)
  public void setup() {
    splitMaps = IntStream.range(0, count)
            .mapToObj(i -> randomSplitmap(keys, runniness, dirtiness))
            .toArray(SplitMap[]::new);
  }


  @Benchmark
  public SplitMap circuit1() {
    return Circuits.evaluate(slice -> {
      Container difference = new ArrayContainer();
      for (Container container : slice) {
        if (container.getCardinality() != 0) {
          difference = difference.ixor(container);
        }
      }
      Container union = new ArrayContainer();
      for (Container container : slice) {
        if (container.getCardinality() != 0) {
          union = union.lazyIOR(container);
        }
      }
      return difference.iand(union);
    }, splitMaps);
  }
}
