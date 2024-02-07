package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class Triggers {

  private final Trigger[] triggers;
  private final Instrumenter<AwsLambdaRequest, Object>[] instrumenters;

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Triggers(Trigger[] triggers, OpenTelemetry openTelemetry) {
    this.triggers = triggers;
    this.instrumenters = new Instrumenter[triggers.length];
    for (int i = 0; i < triggers.length; i++) {
      instrumenters[i] = Instrumenter.builder(
              openTelemetry,
              "io.opentelemetry.aws-lambda-core-1.0",
              triggers[i])
          .addAttributesExtractor(triggers[i])
          .addAttributesExtractor(AttributesExtractor.constant(AttributeKey.stringKey("cx.internal.span.role"), "trigger"))
          .setSpanStatusExtractor(triggers[i])
          .addSpanLinksExtractor(triggers[i])
          .buildInstrumenter(triggers[i].spanKindExtractor());
    }
  }

  public Instrumenter<AwsLambdaRequest, Object> getInstrumenterForRequest(
      AwsLambdaRequest request) {
    for (int i = 0; i < triggers.length; i++) {
      if (triggers[i].matches(request)) {
        return instrumenters[i];
      }
    }
    return null;
  }
}
