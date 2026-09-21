val stableVersion = "2.31.0-cx-SNAPSHOT"
val alphaVersion = "2.31.0-cx-alpha-SNAPSHOT"

val apidiffBaselineVersion = "2.30.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
