package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public abstract class Trigger implements SpanNameExtractor<AwsLambdaRequest>,
    SpanStatusExtractor<AwsLambdaRequest, Object>, AttributesExtractor<AwsLambdaRequest, Object>,
    SpanLinksExtractor<AwsLambdaRequest> {

  public abstract boolean matches(AwsLambdaRequest request);

  public abstract SpanKindExtractor<AwsLambdaRequest> spanKindExtractor(); // Can't implement SpanKindExtractor directly because it has a method that clashes with SpanNameExtractor#extract

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, AwsLambdaRequest request) {
    // by default extract no links
  }
}
