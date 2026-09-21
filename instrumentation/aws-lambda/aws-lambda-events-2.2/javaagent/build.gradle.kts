plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.amazonaws")
    module.set("aws-lambda-java-core")
    versions.set("[1.0.0,)")
    extraDependency("com.amazonaws:aws-lambda-java-events:3.3.1")
    extraDependency("com.amazonaws.serverless:aws-serverless-java-container-core:1.5.2")
  }
  pass {
    group.set("com.amazonaws")
    module.set("aws-lambda-java-events")
    versions.set("[3.3.1,)")
    extraDependency("com.amazonaws.serverless:aws-serverless-java-container-core:1.5.2")
  }
}

dependencies {
  implementation(project(":instrumentation:aws-lambda:aws-lambda-core-1.0:library"))
  implementation(project(":instrumentation:aws-lambda:aws-lambda-events-2.2:library"))

  implementation(project(":instrumentation:aws-lambda:aws-lambda-events-common-2.2:library")) {
    // Only needed by wrappers, not the javaagent. Muzzle will catch if we accidentally change this.
    exclude("com.fasterxml.jackson.core", "jackson-databind")
  }

  library("com.amazonaws:aws-lambda-java-core:1.0.0")
  library("com.amazonaws:aws-lambda-java-events:3.3.1")
  // Present in supported Lambda runtimes; required by Coralogix wrapper serialization helpers.
  library("com.amazonaws:aws-lambda-java-serialization:1.1.5")

  testImplementation(project(":instrumentation:aws-lambda:aws-lambda-events-2.2:testing"))
  testInstrumentation(project(":instrumentation:aws-lambda:aws-lambda-core-1.0:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testMessagingPreview, testBothSemconv, testV3Preview)
  }
}
