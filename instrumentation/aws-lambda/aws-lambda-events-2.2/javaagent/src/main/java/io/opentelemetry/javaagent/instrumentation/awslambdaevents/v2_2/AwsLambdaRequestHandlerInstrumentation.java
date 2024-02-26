/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.amazonaws.services.lambda.runtime.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;

public class AwsLambdaRequestHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.amazonaws.services.lambda.runtime.RequestHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.amazonaws.services.lambda.runtime.RequestHandler"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("handleRequest"))
            .and(takesArgument(1, named("com.amazonaws.services.lambda.runtime.Context"))),
        AwsLambdaRequestHandlerInstrumentation.class.getName() + "$HandleRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleRequestAdvice {

    @SuppressWarnings("TooManyParameters")
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg,
        @Advice.Argument(1) Context context,
        @Advice.Local("otelInput") AwsLambdaRequest input,
        @Advice.Local("otelTriggerInstrumentation") Instrumenter<AwsLambdaRequest, Object> triggerInstrumentation,
        @Advice.Local("otelTriggerContext") io.opentelemetry.context.Context triggerContext,
        @Advice.Local("otelTriggerScope") Scope triggerScope,
        @Advice.Local("otelFunctionContext") io.opentelemetry.context.Context functionContext,
        @Advice.Local("otelFunctionScope") Scope functionScope) {
      input = AwsLambdaRequest.create(context, arg, Collections.emptyMap());
      io.opentelemetry.context.Context upstreamContext =
          AwsLambdaInstrumentationHelper.functionInstrumenter().extract(input);

      if (!AwsLambdaInstrumentationHelper.functionInstrumenter()
          .shouldStart(upstreamContext, input)) {
        return;
      }

      triggerInstrumentation = AwsLambdaInstrumentationHelper.getTriggers()
          .getInstrumenterForRequest(input);

      if (triggerInstrumentation != null) {
        triggerContext = triggerInstrumentation.start(upstreamContext, input);
      }

      io.opentelemetry.context.Context parentForFunctionContext =
          triggerContext != null ? triggerContext : upstreamContext;
      functionContext =
          AwsLambdaInstrumentationHelper.functionInstrumenter().start(parentForFunctionContext, input);

      OpenTelemetrySdkAccess.sendEarlySpans(upstreamContext, triggerContext, functionContext);

      if (triggerContext != null) {
        triggerScope = triggerContext.makeCurrent();
      }

      functionScope = functionContext.makeCurrent();
    }

    @SuppressWarnings("TooManyParameters")
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(value = 0, typing = Typing.DYNAMIC) Object arg,
        @Advice.Return(typing = Typing.DYNAMIC) Object response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelInput") AwsLambdaRequest input,
        @Advice.Local("otelTriggerInstrumentation") Instrumenter<AwsLambdaRequest, Object> triggerInstrumentation,
        @Advice.Local("otelTriggerContext") io.opentelemetry.context.Context triggerContext,
        @Advice.Local("otelTriggerScope") Scope triggerScope,
        @Advice.Local("otelFunctionContext") io.opentelemetry.context.Context functionContext,
        @Advice.Local("otelFunctionScope") Scope functionScope) {

      if (functionScope != null) {
        functionScope.close();
        AwsLambdaInstrumentationHelper.functionInstrumenter()
            .end(functionContext, input, null, throwable);
      }

      if (triggerScope != null) {
        triggerScope.close();
        triggerInstrumentation
            .end(triggerContext, input, response, throwable);
      }

      OpenTelemetrySdkAccess.forceFlush(1, TimeUnit.SECONDS);
    }
  }
}
