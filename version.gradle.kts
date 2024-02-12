val stableVersion = "1.31.0-cx-SNAPSHOT"
val alphaVersion = "1.31.0-cx-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
