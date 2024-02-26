/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.awssdk.v2_2.LambdaAdviceBridge;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.none;

@AutoService(InstrumentationModule.class)
public class LambdaInstrumentationModule extends AbstractAwsSdkInstrumentationModule {

  public LambdaInstrumentationModule() {
    super("aws-sdk-2.2-lambda");
  }

  @Override
  public boolean isHelperClass(String className) {
    return super.isHelperClass(className)
        || className.startsWith("com.fasterxml.jackson.")
        || className.startsWith("org.w3c.dom."); // Jackson references it somewhere
  }

  @Override
  public void doTransform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        none(), LambdaInstrumentationModule.class.getName() + "$RegisterAdvice");
  }

  @SuppressWarnings("unused")
  public static class RegisterAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      // (indirectly) using LambdaImpl class here to make sure it is available from LambdaAccess
      // (injected into app classloader) and checked by Muzzle
      LambdaAdviceBridge.referenceForMuzzleOnly();
    }
  }
}
