addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.0")  // standalone jar (windows, linux)

// addSbtPlugin("de.sciss" % "sbt-appbundle" % "1.0.2")      // os x application bundle (standalone)

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.5.0")  // provides version information to copy into main class

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.5")
