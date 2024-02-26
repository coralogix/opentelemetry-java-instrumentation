package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class TriggerMismatchException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public TriggerMismatchException(String message) {
    super(message);
  }
}
