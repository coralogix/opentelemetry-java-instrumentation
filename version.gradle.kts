val stableVersion = "2.28.0-cx-SNAPSHOT"
val alphaVersion = "2.28.0-cx-alpha-SNAPSHOT"

val apidiffBaselineVersion = "2.27.0"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
