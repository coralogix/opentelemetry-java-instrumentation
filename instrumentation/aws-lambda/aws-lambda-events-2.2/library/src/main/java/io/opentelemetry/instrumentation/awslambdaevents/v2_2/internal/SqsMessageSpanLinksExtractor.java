/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.MapGetter;
import java.util.Collections;

class SqsMessageSpanLinksExtractor implements SpanLinksExtractor<SQSMessage> {
  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";

  // lower-case map getter used for extraction
  static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, SQSMessage message) {
    String parentHeader = message.getAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);
    if (parentHeader != null) {
      Context xrayContext =
          AwsXrayPropagator.getInstance()
              .extract(
                  Context.root(), // We don't want the ambient context.
                  Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, parentHeader),
                  MapGetter.INSTANCE);
      SpanContext messageSpanCtx = Span.fromContext(xrayContext).getSpanContext();
      if (messageSpanCtx.isValid()) {
        spanLinks.addLink(messageSpanCtx);
      }
    }
  }
}
