package cn.tuyucheng.taketoday.branchprediction;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.LongStream;

public class CombiningUnitTest {
   public static final int TOP = 10000000;
   public static final double FRACTION = 0.1;
   private static final Logger LOG = LoggerFactory.getLogger(CombiningUnitTest.class);

   @Test
   public void combined() {
      long[] first = LongStream.range(0, TOP)
            .map(n -> Math.random() < FRACTION ? 0 : n)
            .toArray();
      long[] second = LongStream.range(0, TOP)
            .map(n -> Math.random() < FRACTION ? 0 : n)
            .toArray();

      long count = 0;
      long start = System.currentTimeMillis();
      for (int i = 0; i < TOP; i++) {
         if (first[i] * second[i] != 0) {
            ++count;
         }
      }
      long end = System.currentTimeMillis();

      LOG.info("Counted {}/{} numbers using combined mode in {}ms", count, TOP, end - start);

   }

   @Test
   public void separate() {
      long[] first = LongStream.range(0, TOP)
            .map(n -> Math.random() < FRACTION ? 0 : n)
            .toArray();
      long[] second = LongStream.range(0, TOP)
            .map(n -> Math.random() < FRACTION ? 0 : n)
            .toArray();

      long count = 0;
      long start = System.currentTimeMillis();
      for (int i = 0; i < TOP; i++) {
         if (first[i] != 0 && second[i] != 0) {
            ++count;
         }
      }
      long end = System.currentTimeMillis();

      LOG.info("Counted {}/{} numbers using separate mode in {}ms", count, TOP, end - start);
   }
}