package com.openkappa.splitmap;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public class ChunkedArray<T> {

  private T[][] chunks = (T[][]) new Object[1 << 10][];

  public T get(int index) {
    Objects.checkIndex(index >>> 6, chunks.length);
    T[] line = chunks[index >>> 6];
    return null == line ? null : line[index & 63];
  }

  public void put(int index, T value) {
    Objects.checkIndex(index >>> 6, chunks.length);
    T[] line = chunks[index >>> 6];
    if (null == line) {
      line = chunks[index >>> 6] = (T[]) new Object[Long.SIZE];
    }
    line[index & 63] = value;
  }

  public boolean readChunk(int chunkIndex, T[] output) {
    Objects.checkIndex(chunkIndex, chunks.length);
    if (null != chunks[chunkIndex]) {
      System.arraycopy(chunks[chunkIndex], 0, output, 0, Long.SIZE);
      return true;
    } else {
      Arrays.fill(output, null);
      return false;
    }
  }

  T[] getChunkNoCopy(int chunkIndex) {
    return chunks[chunkIndex];
  }

  public Stream<T> streamChunk(int chunkIndex) {
    Objects.checkIndex(chunkIndex, chunks.length);
    return null == chunks[chunkIndex]
            ? Stream.empty()
            : Stream.of(chunks[chunkIndex]).filter(Objects::nonNull);
  }

  public void writeChunk(int chunkIndex, T[] input) {
    Objects.checkIndex(chunkIndex, chunks.length);
    if (null != chunks[chunkIndex]) {
      System.arraycopy(input, 0, chunks[chunkIndex], 0, Long.SIZE);
    } else {
      chunks[chunkIndex] = Arrays.copyOf(input, Long.SIZE);
    }
  }

  void transferChunk(int chunkIndex, T[] input) {
    Objects.checkIndex(chunkIndex, chunks.length);
    chunks[chunkIndex] = input;
  }

}
