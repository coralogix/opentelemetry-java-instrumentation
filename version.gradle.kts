val stableVersion = "2.30.0-cx-SNAPSHOT"
val alphaVersion = "2.30.0-cx-alpha-SNAPSHOT"

val apidiffBaselineVersion = "2.29.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
