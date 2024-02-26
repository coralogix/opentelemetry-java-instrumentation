/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class AwsLambdaFunctionInstrumenter {

  private final OpenTelemetry openTelemetry;
  final Instrumenter<AwsLambdaRequest, Object> instrumenter;

  public AwsLambdaFunctionInstrumenter(
      OpenTelemetry openTelemetry, Instrumenter<AwsLambdaRequest, Object> instrumenter) {
    this.openTelemetry = openTelemetry;
    this.instrumenter = instrumenter;
  }

  public boolean shouldStart(Context parentContext, AwsLambdaRequest input) {
    return instrumenter.shouldStart(parentContext, input);
  }

  public Context start(Context parentContext, AwsLambdaRequest input) {
    return instrumenter.start(parentContext, input);
  }

  public void end(
      Context context,
      AwsLambdaRequest input,
      @Nullable Object response,
      @Nullable Throwable error) {
    instrumenter.end(context, input, response, error);
  }

  public Context extract(AwsLambdaRequest input) {
    ContextPropagationDebug.debugContextLeakIfEnabled();

    TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
    Context root = Context.root();

    Context contextFromHeaders = propagator.extract(root, input.getHeaders(), MapGetter.INSTANCE);
    if (contextFromHeaders != root){
      return contextFromHeaders;
    }

    if (input.getAwsContext() != null &&
        input.getAwsContext().getClientContext() != null &&
        input.getAwsContext().getClientContext().getCustom() != null) {
      Map<String, String> custom = input.getAwsContext().getClientContext().getCustom();
      return propagator.extract(root, custom, MapGetter.INSTANCE);
    }
    return root;
  }
}
