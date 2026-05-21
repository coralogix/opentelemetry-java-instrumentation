val stableVersion = "2.4.0-cx-SNAPSHOT"
val alphaVersion = "2.4.0-cx-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
