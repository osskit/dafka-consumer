package utils;

import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Operators;

public final class OperatorUtils {

  private OperatorUtils() {}

  public static long safeAddAndGet(AtomicLong atomicLong, long toAdd) {
    long r, u;
    for (;;) {
      r = atomicLong.get();

      if (r == Long.MAX_VALUE) {
        return Long.MAX_VALUE;
      }

      if (r < 0) {
        if (toAdd == Long.MAX_VALUE) {
          u = toAdd;
        } else {
          u = r + toAdd;
        }
      } else {
        u = Operators.addCap(r, toAdd);
      }

      if (atomicLong.compareAndSet(r, u)) {
        return u;
      }
    }
  }
}
