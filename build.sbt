import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val appName        = "ScalaCollider"
lazy val appNameL       = appName.toLowerCase
lazy val baseName       = s"${appName}Swing"
lazy val baseNameL      = baseName.toLowerCase

lazy val projectVersion = "1.41.5-SNAPSHOT"
lazy val mimaVersion    = "1.41.0"

lazy val authorName     = "Hanns Holger Rutz"
lazy val authorEMail    = "contact@sciss.de"

lazy val appDescription = "Standalone application for ScalaCollider"

lazy val deps = new {
  val core = new {
    val audioWidgets    = "1.14.3"
    val dot             = "0.10.4"
    val fileUtil        = "1.1.3"
    val prefuse         = "1.0.1"
    val scalaCollider   = "1.28.4"
    val ugens           = "1.19.5"
  }
  val intp = new {
    val interpreterPane = "1.10.1"
  }
  val plot = new {
    val chart           = "0.7.1"
    val pdflitz         = "1.4.1" // incurs GPL
  }
  val app = new {
    val desktop         = "0.10.4"
    val docking         = "2.0.0"
    val dsp             = "1.3.2"
    val kollFlitz       = "0.2.3"
    val pegDown         = "1.6.0"
    val submin          = "0.2.5" // incurs GPL
    val webLaF          = "2.1.5" // incurs GPL
  }
}

lazy val commonSettings = Seq(
  version            := projectVersion,
  organization       := "de.sciss",
  scalaVersion       := "2.12.8",
  crossScalaVersions := Seq("2.12.8", "2.11.12", "2.13.0"),
  homepage           := Some(url(s"https://git.iem.at/sciss/$baseName")),
  licenses           := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt")),
  scalacOptions ++= {
    val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xlint:-stars-align,-missing-interpolator,_", "-Xsource:2.13")
    if (isSnapshot.value) xs else xs ++ Seq("-Xelide-below", "INFO")  // elide logging in stable versions
  },
  updateOptions := updateOptions.value.withLatestSnapshots(false),
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
      <url>git@git.iem.at:sciss/{n}.git</url>
      <connection>scm:git:git@git.iem.at:sciss/{n}.git</connection>
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
//  javaOptions in Universal ++= Seq(
//    // -J params will be added as jvm parameters
//    "-J-Xmx1024m"
//    // others will be added as app parameters
//    // "-Dproperty=true",
//  ),
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
  debianPackageDependencies in Debian += "java8-runtime",
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
  test            in assembly := {},
  mainClass       in assembly := appMainClass,
  target          in assembly := baseDirectory.value,
  assemblyJarName in assembly := "ScalaCollider.jar",
  assemblyMergeStrategy in assembly := {
    case "logback.xml" => MergeStrategy.last
    case PathList("org", "xmlpull", _ @ _*)              => MergeStrategy.first
    case PathList("org", "w3c", "dom", "events", _ @ _*) => MergeStrategy.first // bloody Apache Batik
    case x =>
      val old = (assemblyMergeStrategy in assembly).value
      old(x)
  }
)

// ---- projects ----

lazy val root = project.withId(baseNameL).in(file("."))
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

lazy val core = project.withId(s"$baseNameL-core").in(file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(
    name           := s"$baseName-core",
    description    := "Swing components for ScalaCollider",
    libraryDependencies ++= Seq(
      "de.sciss"  %% "scalacollider"              % deps.core.scalaCollider,
      "de.sciss"  %  "scalacolliderugens-spec"    % deps.core.ugens,  // sbt bug
      "de.sciss"  %% "scalacolliderugens-core"    % deps.core.ugens,
      "de.sciss"  %% "scalacolliderugens-plugins" % deps.core.ugens,  // NB: sc3-plugins
      "de.sciss"  %% "fileutil"                   % deps.core.fileUtil,
      "de.sciss"  %  "prefuse-core"               % deps.core.prefuse,
      "de.sciss"  %% "audiowidgets-swing"         % deps.core.audioWidgets,
      "de.sciss"  %% "scalacollider-dot"          % deps.core.dot
    ),
    // ---- build info ----
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.synth.swing",
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-core" % mimaVersion)
  )

lazy val interpreter = project.withId(s"$baseNameL-interpreter").in(file("interpreter"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    description    := "REPL for ScalaCollider",
    libraryDependencies ++= Seq(
      "de.sciss"        %% "scalainterpreterpane" % deps.intp.interpreterPane,
      "org.scala-lang"  %  "scala-compiler"       % scalaVersion.value  // make sure we have the newest
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-interpreter" % mimaVersion)
  )

lazy val plotting = project.withId(s"$baseNameL-plotting").in(file("plotting"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    description := "Plotting functions for ScalaCollider",
    libraryDependencies ++= Seq(
      "de.sciss" %% "pdflitz"     % deps.plot.pdflitz,
      "de.sciss" %% "scala-chart" % deps.plot.chart
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-plotting" % mimaVersion)
  )

lazy val app = project.withId(s"$baseNameL-app").in(file("app"))
  .dependsOn(core, interpreter, plotting)
  .settings(commonSettings)
  .settings(
    description    := appDescription,
    libraryDependencies ++= Seq(
      // experiment with making sources and docs available.
      // cf. http://stackoverflow.com/questions/22160701
      //     "de.sciss" %% "scalacollider" % scalaColliderVersion,
      //     "de.sciss" %% "scalacollider" % scalaColliderVersion classifier "javadoc",
      //     "de.sciss" %% "scalacollider" % scalaColliderVersion classifier "sources",
      "de.sciss"    %  "scalacolliderugens-spec" % deps.core.ugens,
      "de.sciss"    %% "desktop"                 % deps.app.desktop, // withJavadoc() withSources(),
      "de.sciss"    %% "kollflitz"               % deps.app.kollFlitz,
      "de.sciss"    %  "submin"                  % deps.app.submin,
      "de.sciss"    %  "weblaf"                  % deps.app.webLaF,
      "de.sciss"    %% "scissdsp"                % deps.app.dsp,
      "de.sciss"    %  "docking-frames"          % deps.app.docking,
      "org.pegdown" %  "pegdown"                 % deps.app.pegDown
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-app" % mimaVersion)
  )
