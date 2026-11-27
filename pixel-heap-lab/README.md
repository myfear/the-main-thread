# How Many Pixels Fit in 64 MB of Java Heap?

Make a prediction for a 4K frame, then measure how many packed ARGB pixels fit in `int[]`, an ordinary record array, and a value record array. A fourth run puts the same value pixels in `new Object[count]`. Every trial fills its array and reads the colors back in a fresh JVM with a 64 MiB heap.

Companion to [How Many Pixels Fit in 64 MB of Java Heap?](https://www.the-main-thread.com/p/java-28-valhalla-pixel-heap-lab).

## Run the lab

Use an OpenJDK 28 early-access build with JEP 401 and select it for both `java` and `javac`. This lab was verified on OpenJDK HotSpot **28-ea+25-2095** for macOS AArch64. It uses only the JDK. [Download JDK 28 EA](https://jdk.java.net/28/)

From the directory containing this README, compile the sources and check a 1024 × 1024 image.

```sh
java --version
javac --version
javac --enable-preview --release 28 -d out src/PixelProbe.java src/HeapLab.java src/PixelImage.java
java --enable-preview -cp out HeapLab check
```

All four modes should print `pixels=1048576 checksum=9add7cc805989d47`. To see the pixels, run the renderer in a separate JVM and open `pixels.png`.

```sh
java --enable-preview -cp out PixelImage pixels.png
```

A 3840 × 2160 frame contains 8,294,400 pixels. Predict which arrays can hold that many, then run each command.

```sh
java --enable-preview -cp out HeapLab try int 8294400
java --enable-preview -cp out HeapLab try record 8294400
java --enable-preview -cp out HeapLab try value 8294400
java --enable-preview -cp out HeapLab try object 8294400
```

`FIT` includes the count and checksum. `NO_FIT` with exit status 42 means that the probe ran out of heap for that count. If your shell uses `set -e`, an expected `NO_FIT` ends the sequence. Use a regular interactive shell for this step.

## Find the boundary

```sh
java --enable-preview -cp out HeapLab measure
```

To measure one representation, append `int`, `record`, `value`, or `object`. Each line gives the largest successful count found and the next grid point that failed. The grid spacing is 65,536 pixels. The search starts a fresh HotSpot JVM for each probe, fixes the heap with `-Xms64m -Xmx64m`, and selects G1 with two active processors. Build, architecture and heap layout can affect your boundary.

## Change one keyword

Start with six tiles:

```sh
java --enable-preview -cp out HeapLab try value 6291456
```

In [PixelProbe.java](src/PixelProbe.java), remove `value` from `value record ValuePixel(int argb) {}`. Recompile using the command above, then run:

```sh
java --enable-preview -cp out HeapLab check
java --enable-preview -cp out HeapLab try value 6291456
java --enable-preview -cp out HeapLab measure value
```

Compare the six-tile result before and after the change. Restore the keyword and recompile to return to the original experiment.

With the original declaration, this HotSpot diagnostic prints how the VM lays out the typed array. On the verified build it reports `NULLABLE_ATOMIC_FLAT` with an eight-byte element.

```sh
java --enable-preview -Xms64m -Xmx64m -XX:+UseG1GC -XX:ActiveProcessorCount=2 -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlatArrayLayout -cp out PixelProbe value 1048576
```

The preview language feature removes object identity. HotSpot chooses which layouts to flatten. [JEP 401](https://openjdk.org/jeps/401) · [OpenJDK guide to value objects](https://github.com/openjdk/valhalla-docs/blob/main/site/value-objects.md)
