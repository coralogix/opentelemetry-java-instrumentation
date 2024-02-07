package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.TimeUnit;

class SdkEarlySpans implements OpenTelemetrySdkAccess.EarlySpans {

  private final OpenTelemetrySdk sdk;
  private final Tracer tracer;

  SdkEarlySpans(OpenTelemetrySdk sdk) {
    this.sdk = sdk;
    this.tracer = sdk.getTracerProvider()
        .tracerBuilder("coralogix-autoinstrumentation")
        .build();
  }

  /**
   * We send copies of the trigger and function spans at the start of the invocation,
   * so that coralogix-aws-lambda-telemetry-exporter can use them
   * in case the function crashes/timeouts and doesn't deliver the trace.
   */
  @Override
  public void sendEarlySpans(Context upstreamContext, Context triggerContext,
      Context functionContext) {
    System.out.println("EarlySpans.doSth");

    createEarlySpan(upstreamContext, triggerContext, "trigger");
    Context parentForFunctionSpan = triggerContext != null ? triggerContext : upstreamContext;
    createEarlySpan(parentForFunctionSpan, functionContext, "function");

    sdk.getSdkTracerProvider().forceFlush().join(1, TimeUnit.SECONDS);
  }

  private void createEarlySpan(Context parentContext, Context context, String spanRole) {
    if (context != null) {
      ReadWriteSpan span = (ReadWriteSpan) Span.fromContextOrNull(context);
      if (span != null) {
        SpanData spanData = span.toSpanData();
        SpanBuilder builder = tracer.spanBuilder(span.getName());
        if (parentContext != null) {
          builder.setParent(parentContext);
        }
        builder.setSpanKind(spanData.getKind());
        for (LinkData link : spanData.getLinks()) {
          builder.addLink(link.getSpanContext());
        }
        builder.setAllAttributes(spanData.getAttributes());
        // These are all internal attributes meant to be interpreted by coralogix-aws-lambda-telemetry-exporter
        builder.setAttribute("cx.internal.span.id", spanData.getSpanId());
        builder.setAttribute("cx.internal.trace.id", spanData.getTraceId());
        builder.setAttribute("cx.internal.lambda.span.role", spanRole);
        Span earlySpan = builder.startSpan();
        earlySpan.end();
      }
    }
  }
}
