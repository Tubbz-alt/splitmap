package com.openkappa.splitmap;

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static com.openkappa.splitmap.Reducers.PMCC;
import static org.testng.Assert.assertEquals;

public class Aggregation {


  @Test
  public void testProductMomentCorrelationCoefficientFactors() {
    double[] values1 = IntStream.range(0, 1000)
            .mapToDouble(i -> ThreadLocalRandom.current().nextDouble())
            .toArray();
    double[] values2 = IntStream.range(0, 1000)
            .mapToDouble(i -> -ThreadLocalRandom.current().nextDouble())
            .toArray();

    double[] statistics = new double[6];
    for (int i = 0; i < 1000; ++i) {
      double sp = values1[i];
      double sq = values2[i];
      double spp = sp * sp;
      double sqq = sq * sq;
      double spq = sp * sq;
      statistics[0] += sp;
      statistics[1] += sq;
      statistics[2] += spp;
      statistics[3] += sqq;
      statistics[4] += spq;
      statistics[5] += 1;
    }

    PrefixIndex<double[]> pi1 = new PrefixIndex<>();
    PrefixIndex<double[]> pi2 = new PrefixIndex<>();
    double[] page1 = new double[1 << 16];
    double[] page2 = new double[1 << 16];

    PageWriter filterWriter = new PageWriter(Hashing::scatter);

    int key = 0;
    int multiple = 0;
    for (int k = 0; k < 20; ++k) {
      for (int i = 0; i < 50; ++i) {
        filterWriter.add(key + i);
        page1[i] = values1[i + multiple * 50];
        page2[i] = values2[i + multiple * 50];
      }
      pi1.insert((short) Hashing.scatter(key >>> 16), Arrays.copyOf(page1, page1.length));
      pi2.insert((short) Hashing.scatter(key >>> 16), Arrays.copyOf(page2, page2.length));
      ++multiple;
      key += 1 << 16;
    }

    SplitMap filter = filterWriter.toSplitMap();
    double[] factors = filter.getIndex()
            .streamUniformPartitions()
            .parallel()
            .map(partition -> {
              double[] stats = new double[6];
              partition.forEach((k, c) -> {
                double[] x = pi1.get(k);
                double[] y = pi2.get(k);
                c.forEach((short) 0, i -> {
                  double sx = x[i];
                  double sy = y[i];
                  double sxx = sx * sx;
                  double syy = sy * sy;
                  double sxy = sx * sy;
                  stats[0] += sx;
                  stats[1] += sy;
                  stats[2] += sxx;
                  stats[3] += syy;
                  stats[4] += sxy;
                  stats[5] += 1;
                });
              });
              return stats;
            })
            .reduce(Reducers::sum)
            .orElseGet(() -> new double[6]);

    System.out.println(Arrays.toString(statistics) + " -> " + Arrays.toString(factors));
    for (int i = 0; i < factors.length; ++i) {
      assertEquals(factors[i], statistics[i], 1E-5);
    }
  }


  @Test
  public void testProductMomentCorrelationCoefficient() {
    double[] values1 = IntStream.range(0, 1000)
            .mapToDouble(i -> ThreadLocalRandom.current().nextDouble())
            .toArray();
    double[] values2 = IntStream.range(0, 1000)
            .mapToDouble(i -> -ThreadLocalRandom.current().nextDouble())
            .toArray();

    double[] statistics = new double[6];
    for (int i = 0; i < 1000; ++i) {
      double sp = values1[i];
      double sq = values2[i];
      double spp = sp * sp;
      double sqq = sq * sq;
      double spq = sp * sq;
      statistics[0] += sp;
      statistics[1] += sq;
      statistics[2] += spp;
      statistics[3] += sqq;
      statistics[4] += spq;
      statistics[5] += 1;
    }

    double sp = statistics[0];
    double sq = statistics[1];
    double spp = statistics[2];
    double sqq = statistics[3];
    double spq = statistics[4];
    double n = statistics[5];
    double pmccExpected =  (n * spq - sq * sp) / (Math.sqrt((n * spp - sp * sp) * (n * sqq - sq * sq)));

    PrefixIndex<double[]> pi1 = new PrefixIndex<>();
    PrefixIndex<double[]> pi2 = new PrefixIndex<>();
    double[] page1 = new double[1 << 16];
    double[] page2 = new double[1 << 16];

    PageWriter filterWriter = new PageWriter(Hashing::scatter);

    int key = 0;
    int multiple = 0;
    for (int k = 0; k < 20; ++k) {
      for (int i = 0; i < 50; ++i) {
        filterWriter.add(key + i);
        page1[i] = values1[i + multiple * 50];
        page2[i] = values2[i + multiple * 50];
      }
      pi1.insert((short) Hashing.scatter(key >>> 16), Arrays.copyOf(page1, page1.length));
      pi2.insert((short) Hashing.scatter(key >>> 16), Arrays.copyOf(page2, page2.length));
      ++multiple;
      key += 1 << 16;
    }

    SplitMap filter = filterWriter.toSplitMap();
    double pmcc = filter.getIndex()
            .streamUniformPartitions()
            .parallel()
            .map(partition -> {
              double[] stats = new double[6];
              partition.forEach((k, c) -> {
                double[] x = pi1.get(k);
                double[] y = pi2.get(k);
                c.forEach((short) 0, i -> {
                  double sx = x[i];
                  double sy = y[i];
                  double sxx = sx * sx;
                  double syy = sy * sy;
                  double sxy = sx * sy;
                  stats[0] += sx;
                  stats[1] += sy;
                  stats[2] += sxx;
                  stats[3] += syy;
                  stats[4] += sxy;
                  stats[5] += 1;
                });
              });
              return stats;
            })
            .collect(PMCC);
    assertEquals(pmcc, pmccExpected, 1E-5);
  }

}
