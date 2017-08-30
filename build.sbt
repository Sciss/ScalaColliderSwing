import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val appName                = "ScalaCollider"
lazy val appNameL               = appName.toLowerCase
lazy val baseName               = s"${appName}Swing"
lazy val baseNameL              = baseName.toLowerCase

lazy val projectVersion         = "1.34.1"
lazy val mimaVersion            = "1.34.0"

lazy val authorName             = "Hanns Holger Rutz"
lazy val authorEMail            = "contact@sciss.de"

lazy val appDescription         = "Standalone application for ScalaCollider"

// ---- core dependencies ----

lazy val scalaColliderVersion   = "1.22.3"
lazy val prefuseVersion         = "1.0.1"
lazy val audioWidgetsVersion    = "1.11.0"
lazy val ugensVersion           = "1.16.4"
lazy val dotVersion             = "0.4.1"
lazy val batikVersion           = "1.9.1"
lazy val xmlGraphicsVersion     = "2.2"

// ---- interpreter dependencies ----

lazy val interpreterPaneVersion = "1.8.1"

// ---- plotting dependencies ----

lazy val pdflitzVersion         = "1.2.2"
lazy val chartVersion           = "0.5.1"

// ---- app dependencies ----

lazy val desktopVersion         = "0.8.0"
lazy val fileUtilVersion        = "1.1.3"
lazy val kollFlitzVersion       = "0.2.1"
lazy val subminVersion          = "0.2.1"
lazy val webLaFVersion          = "2.1.3"
lazy val dockingVersion         = "2.0.0"
lazy val pegDownVersion         = "1.6.0"
lazy val dspVersion             = "1.2.3"

lazy val commonSettings = Seq(
  version            := projectVersion,
  organization       := "de.sciss",
  scalaVersion       := "2.12.3",
  crossScalaVersions := Seq("2.12.3", "2.11.11", "2.10.6"),
  homepage           := Some(url(s"https://github.com/Sciss/$baseName")),
  licenses           := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt")),
  scalacOptions ++= {
    val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture")
    val ys = if (scalaVersion.value.startsWith("2.10")) xs else xs :+ "-Xlint:-stars-align,-missing-interpolator,_"  // syntax not supported in Scala 2.10
    if (isSnapshot.value) ys else ys ++ Seq("-Xelide-below", "INFO")  // elide logging in stable versions
  },
  aggregate in assembly := false   // https://github.com/sbt/sbt-assembly/issues/147
) ++ publishSettings

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo :=
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    ),
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := { val n = baseName
    <scm>
      <url>git@github.com:Sciss/{n}.git</url>
      <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
    </scm>
      <developers>
        <developer>
          <id>sciss</id>
          <name>Hanns Holger Rutz</name>
          <url>http://www.sciss.de</url>
        </developer>
      </developers>
  }
)

def appMainClass = Some("de.sciss.synth.swing.Main")

// ---- packaging ----

//////////////// universal (directory) installer
lazy val pkgUniversalSettings = Seq(
  executableScriptName /* in Universal */ := appNameL,
  // NOTE: doesn't work on Windows, where we have to
  // provide manual file `SCALACOLLIDER_config.txt` instead!
  javaOptions in Universal ++= Seq(
    // -J params will be added as jvm parameters
    "-J-Xmx1024m"
    // others will be added as app parameters
    // "-Dproperty=true",
  ),
  // Since our class path is very very long,
  // we use instead the wild-card, supported
  // by Java 6+. In the packaged script this
  // results in something like `java -cp "../lib/*" ...`.
  // NOTE: `in Universal` does not work. It therefore
  // also affects debian package building :-/
  // We need this settings for Windows.
  scriptClasspath /* in Universal */ := Seq("*")
)

//////////////// debian installer
lazy val pkgDebianSettings = Seq(
  name                      in Debian := appName,
  packageName               in Debian := appNameL,
  name                      in Linux  := appName,
  packageName               in Linux  := appNameL,
  packageSummary            in Debian := appDescription,
  mainClass                 in Debian := appMainClass,
  maintainer                in Debian := s"$authorName <$authorEMail>",
  debianPackageDependencies in Debian += "java7-runtime",
  packageDescription        in Debian :=
    """A simple development environment for ScalaCollider,
      | a client for the sound-synthesis server SuperCollider.
      | It is based on a multiple-document-interface code editor
      | and some useful included packages.
      | A few widgets are added, such as server status, node tree
      | display, bus meters, and signal plotting.
      |""".stripMargin,
  // include all files in src/debian in the installed base directory
  linuxPackageMappings      in Debian ++= {
    val n     = (name            in Debian).value.toLowerCase
    val dir   = (sourceDirectory in Debian).value / "debian"
    val f1    = (dir * "*").filter(_.isFile).get  // direct child files inside `debian` folder
    val f2    = ((dir / "doc") * "*").get
    //
    def readOnly(in: LinuxPackageMapping) =
      in.withUser ("root")
        .withGroup("root")
        .withPerms("0644")  // http://help.unc.edu/help/how-to-use-unix-and-linux-file-permissions/
    //
    val aux   = f1.map { fIn => packageMapping(fIn -> s"/usr/share/$n/${fIn.name}") }
    val doc   = f2.map { fIn => packageMapping(fIn -> s"/usr/share/doc/$n/${fIn.name}") }
    (aux ++ doc).map(readOnly)
  }
)

lazy val assemblySettings = Seq(
  // ---- assembly ----
  test            in assembly := (),
  mainClass       in assembly := appMainClass,
  target          in assembly := baseDirectory.value,
  assemblyJarName in assembly := "ScalaCollider.jar",
  assemblyMergeStrategy in assembly := {
    case "logback.xml" => MergeStrategy.last
    case PathList("org", "xmlpull", xs @ _*)              => MergeStrategy.first
    case PathList("org", "w3c", "dom", "events", xs @ _*) => MergeStrategy.first // bloody Apache Batik
    case x =>
      val old = (assemblyMergeStrategy in assembly).value
      old(x)
  }
)

// ---- projects ----

lazy val root = Project(id = baseNameL, base = file("."))
  .aggregate(core, interpreter, plotting, app)
  .dependsOn(core, interpreter, plotting, app)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .settings(commonSettings)
  .settings(assemblySettings)
  .settings(
    publishArtifact in (Compile, packageBin) := false, // there are no binaries
    publishArtifact in (Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in (Compile, packageSrc) := false, // there are no sources
    mainClass in Compile := appMainClass  // cf. https://stackoverflow.com/questions/23664963
  )
  .settings(pkgUniversalSettings)
  .settings(useNativeZip) // cf. https://github.com/sbt/sbt-native-packager/issues/334
  .settings(pkgDebianSettings)

lazy val core = Project(id = s"$baseNameL-core", base = file("core")).
  enablePlugins(BuildInfoPlugin).
  settings(commonSettings).
  settings(
    name           := s"$baseName-core",
    description    := "Swing components for ScalaCollider",
    libraryDependencies ++= Seq(
      "de.sciss"               %% "scalacollider"              % scalaColliderVersion,
      "de.sciss"               %% "scalacolliderugens-core"    % ugensVersion,
      "de.sciss"               %% "scalacolliderugens-plugins" % ugensVersion,  // NB: sc3-plugins
      "de.sciss"               %  "prefuse-core"               % prefuseVersion,
      "de.sciss"               %% "audiowidgets-swing"         % audioWidgetsVersion,
      "at.iem"                 %% "scalacollider-dot"          % dotVersion,
      // "org.apache.xmlgraphics" %  "batik-swing"                % batikVersion  exclude("org.apache.xmlgraphics", "batik-script")
      "org.apache.xmlgraphics" %  "batik-swing"                % batikVersion exclude("org.mozilla", "rhino") exclude("org.python", "jython") // mother***
      // "org.apache.xmlgraphics" %  "xmlgraphics-commons"        % xmlGraphicsVersion // bloody Apache Batik does not declare its dependencies
    ),
    // ---- build info ----
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.synth.swing",
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-core" % mimaVersion)
  )

lazy val interpreter = Project(id = s"$baseNameL-interpreter", base = file("interpreter")).
  dependsOn(core).
  settings(commonSettings).
  settings(
    description    := "REPL for ScalaCollider",
    libraryDependencies ++= Seq(
      "de.sciss" %% "scalainterpreterpane" % interpreterPaneVersion,
      "org.scala-lang" %  "scala-compiler" % scalaVersion.value  // make sure we have the newest
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-interpreter" % mimaVersion)
  )

lazy val plotting = Project(id = s"$baseNameL-plotting", base = file("plotting")).
  dependsOn(core).
  settings(commonSettings).
  settings(
    description := "Plotting functions for ScalaCollider",
    libraryDependencies ++= Seq(
      "de.sciss"                 %% "pdflitz"     % pdflitzVersion,
      "com.github.wookietreiber" %% "scala-chart" % chartVersion
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-plotting" % mimaVersion)
  )

lazy val app = Project(id = s"$baseNameL-app", base = file("app")).
  dependsOn(core, interpreter, plotting).
  settings(commonSettings).
  settings(
    description    := appDescription,
    libraryDependencies ++= Seq(
      // experiment with making sources and docs available.
      // cf. http://stackoverflow.com/questions/22160701
      //     "de.sciss" %% "scalacollider" % scalaColliderVersion,
      //     "de.sciss" %% "scalacollider" % scalaColliderVersion classifier "javadoc",
      //     "de.sciss" %% "scalacollider" % scalaColliderVersion classifier "sources",
      "de.sciss"    %  "scalacolliderugens-spec" % ugensVersion,
      "de.sciss"    %% "desktop"                 % desktopVersion, // withJavadoc() withSources(),
      "de.sciss"    %% "fileutil"                % fileUtilVersion,
      "de.sciss"    %% "kollflitz"               % kollFlitzVersion,
      "de.sciss"    %  "submin"                  % subminVersion,
      "de.sciss"    %  "weblaf"                  % webLaFVersion,
      "de.sciss"    %% "scissdsp"                % dspVersion,
      "de.sciss"    %  "docking-frames"          % dockingVersion,
      "org.pegdown" %  "pegdown"                 % pegDownVersion
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-app" % mimaVersion)
  )
