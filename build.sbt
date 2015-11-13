import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val appName                = "ScalaCollider"
lazy val appNameL               = appName.toLowerCase
lazy val baseName               = s"${appName}Swing"
lazy val baseNameL              = baseName.toLowerCase

lazy val projectVersion         = "1.26.0"

lazy val authorName             = "Hanns Holger Rutz"
lazy val authorEMail            = "contact@sciss.de"

lazy val appDescription         = "Standalone application for ScalaCollider"

// ---- core dependencies ----

lazy val scalaColliderVersion   = "1.17.4"
lazy val prefuseVersion         = "1.0.0"
lazy val audioWidgetsVersion    = "1.9.1"
lazy val ugensVersion           = "1.13.4"

// ---- interpreter dependencies ----

lazy val interpreterPaneVersion = "1.7.2"

// ---- plotting dependencies ----

lazy val pdflitzVersion         = "1.2.1"
lazy val chartVersion           = "0.5.0"

// ---- app dependencies ----

lazy val desktopVersion         = "0.7.1"
lazy val fileUtilVersion        = "1.1.1"
lazy val kollFlitzVersion       = "0.2.0"
lazy val webLaFVersion          = "1.28"
lazy val dockingVersion         = "1.1.1"
lazy val dspVersion             = "1.2.2"

lazy val commonSettings = Seq(
  version            := projectVersion,
  organization       := "de.sciss",
  scalaVersion       := "2.11.7",
  crossScalaVersions := Seq("2.11.7", "2.10.6"),
  homepage           := Some(url(s"https://github.com/Sciss/$baseName")),
  licenses           := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt")),
  scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture"),
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

// lazy vall appbundleSettings = Seq(
//    // ---- appbundle ----
//    appbundle.mainClass := appMainClass,
//    appbundle.target := baseDirectory.value,
//    appbundle.name   := "ScalaCollider",
//    appbundle.icon   := Some(file("icons/ScalaCollider.png"))
// )

lazy val assemblySettings = Seq(
  // ---- assembly ----
  test            in assembly := (),
  mainClass       in assembly := appMainClass,
  target          in assembly := baseDirectory.value,
  assemblyJarName in assembly := "ScalaCollider.jar",
  assemblyMergeStrategy in assembly := {
    case "logback.xml" => MergeStrategy.last
    case PathList("org", "xmlpull", xs @ _*) => MergeStrategy.first
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
      "de.sciss" %% "scalacollider"        % scalaColliderVersion,
      "de.sciss" %% "scalacolliderugens-core"    % ugensVersion,
      "de.sciss" %% "scalacolliderugens-plugins" % ugensVersion,  // NB: sc3-plugins
      "de.sciss" %  "prefuse-core"         % prefuseVersion,
      "de.sciss" %% "audiowidgets-swing"   % audioWidgetsVersion
    ),
    // ---- build info ----
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.synth.swing"
  )

lazy val interpreter = Project(id = s"$baseNameL-interpreter", base = file("interpreter")).
  dependsOn(core).
  settings(commonSettings).
  settings(
    description    := "REPL for ScalaCollider",
    libraryDependencies ++= Seq(
      "de.sciss" %% "scalainterpreterpane" % interpreterPaneVersion,
      "org.scala-lang" %  "scala-compiler" % scalaVersion.value  // make sure we have the newest
    )
  )

lazy val plotting = Project(id = s"$baseNameL-plotting", base = file("plotting")).
  dependsOn(core).
  settings(commonSettings).
  settings(
    description := "Plotting functions for ScalaCollider",
    libraryDependencies ++= Seq(
      "de.sciss"                 %% "pdflitz"     % pdflitzVersion,
      "com.github.wookietreiber" %% "scala-chart" % chartVersion
    )
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
      "de.sciss"                 %% "desktop"               % desktopVersion, // withJavadoc() withSources(),
      "de.sciss"                 %% "fileutil"              % fileUtilVersion,
      "de.sciss"                 %% "kollflitz"             % kollFlitzVersion,
      "de.sciss"                 %  "weblaf"                % webLaFVersion,
      "de.sciss"                 %% "scissdsp"              % dspVersion,
      "org.dockingframes"        %  "docking-frames-common" % dockingVersion
 //     "net.sf.cssbox"            %  "swingbox"              % swingBoxVersion,
      // "org.fusesource.scalamd"   %% "scalamd"               % "1.6",
    )
  )

// ---- ls.implicit.ly ----

// seq(lsSettings :_*)
// (LsKeys.tags   in LsKeys.lsync) := Seq("sound-synthesis", "sound", "music", "supercollider")
// (LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")
// (LsKeys.ghRepo in LsKeys.lsync) := Some(name.value)

