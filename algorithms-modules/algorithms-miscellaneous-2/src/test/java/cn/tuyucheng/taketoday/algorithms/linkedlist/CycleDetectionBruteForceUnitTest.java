package cn.tuyucheng.taketoday.algorithms.linkedlist;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CycleDetectionBruteForceUnitTest extends CycleDetectionTestBase {


   @ParameterizedTest
   @MethodSource("getLists")
   void givenList_detectLoop(Node<Integer> head, boolean cycleExists) {
      assertEquals(cycleExists, CycleDetectionBruteForce.detectCycle(head).cycleExists);
   }
}