/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import io.opentelemetry.context.Context;
import java.util.concurrent.TimeUnit;

/**
 * A helper to facilitate accessing OpenTelemetry SDK methods from instrumentation. Because
 * instrumentation runs in the app class loader, they do not have access to our SDK in the agent
 * class loader. So we use this class in the bootstrap class loader to bridge between the two - the
 * agent class loader will register implementations of needed SDK functions that can be called from
 * instrumentation.
 */
public final class OpenTelemetrySdkAccess {

  /**
   * Interface matching {@code io.opentelemetry.sdk.trace.SdkTracerProvider#forceFlush()} to allow
   * holding a reference to it.
   */
  public interface ForceFlusher {
    /** Executes force flush. */
    void run(int timeout, TimeUnit unit);
  }

  private static volatile ForceFlusher forceFlush;

  /** Forces flushing of pending spans. */
  public static void forceFlush(int timeout, TimeUnit unit) {
    forceFlush.run(timeout, unit);
  }

  /**
   * Sets the {@link Runnable} to execute when instrumentation needs to force flush. This is called
   * from the agent class loader to execute the SDK's force flush mechanism. Instrumentation must
   * not call this.
   */
  public static void internalSetForceFlush(ForceFlusher forceFlush) {
    if (OpenTelemetrySdkAccess.forceFlush != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    OpenTelemetrySdkAccess.forceFlush = forceFlush;
  }

  /**
   * Sends copies of trigger and function spans early.
   */
  public interface EarlySpans {
    /** Sends copies of trigger and function spans early. */
    void sendEarlySpans(Context upstreamContext, Context triggerContext, Context functionContext);
  }

  private static volatile EarlySpans earlySpans;

  /** Sends copies of trigger and function spans early. */
  public static void sendEarlySpans(Context upstreamContext, Context triggerContext, Context functionContext) {
    earlySpans.sendEarlySpans(upstreamContext, triggerContext, functionContext);
  }

  /**
   * Sets the earlySpans. This is called
   * from the agent class loader to execute the SDK's mechanism. Instrumentation must not call this.
   */
  public static void internalSetEarlySpans(EarlySpans earlySpans) {
    if (OpenTelemetrySdkAccess.earlySpans != null) {
      // Only possible by misuse of this API, just ignore.
      return;
    }
    OpenTelemetrySdkAccess.earlySpans = earlySpans;
  }

  private OpenTelemetrySdkAccess() {}
}
