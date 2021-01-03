addSbtPlugin("com.eed3si9n"     % "sbt-assembly"        % "0.15.0")   // standalone jar (windows, linux)
addSbtPlugin("com.eed3si9n"     % "sbt-buildinfo"       % "0.10.0")   // provides version information to copy into main class
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.0")
addSbtPlugin("com.typesafe"     % "sbt-mima-plugin"     % "0.8.1")
addSbtPlugin("ch.epfl.lamp"     % "sbt-dotty"           % "0.5.1")    // cross-compile for dotty

