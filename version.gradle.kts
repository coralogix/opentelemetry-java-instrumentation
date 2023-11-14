val stableVersion = "1.31.0-cx-1"
val alphaVersion = "1.31.0-cx-1"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
