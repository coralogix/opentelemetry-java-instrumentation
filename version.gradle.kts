val stableVersion = "1.32.1-cx-SNAPSHOT"
val alphaVersion = "1.32.1-cx-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
