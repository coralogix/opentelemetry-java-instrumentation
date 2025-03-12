val stableVersion = "1.33.6-cx-SNAPSHOT"
val alphaVersion = "1.33.6-cx-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
