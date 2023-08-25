val stableVersion = "1.30.0-cx-SNAPSHOT"
val alphaVersion = "1.30.0-cx-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
