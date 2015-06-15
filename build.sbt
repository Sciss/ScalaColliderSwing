lazy val baseName               = "ScalaColliderSwing"
lazy val baseNameL              = baseName.toLowerCase

lazy val projectVersion         = "1.26.0-SNAPSHOT"

// ---- core dependencies ----

lazy val scalaColliderVersion   = "1.18.0-SNAPSHOT"
lazy val prefuseVersion         = "1.0.0"
lazy val audioWidgetsVersion    = "1.10.0-SNAPSHOT"
lazy val ugensVersion           = "1.13.1"

// ---- interpreter dependencies ----

lazy val interpreterPaneVersion = "1.7.1"

// ---- plotting dependencies ----

lazy val pdflitzVersion         = "1.2.1"
lazy val chartVersion           = "0.4.2"

// ---- app dependencies ----

lazy val desktopVersion         = "0.7.0"
lazy val fileUtilVersion        = "1.1.1"
lazy val kollFlitzVersion       = "0.2.0"
lazy val xstreamVersion         = "1.4.8" // 1.4.7 corrupt sha1 on Maven Central
lazy val webLaFVersion          = "1.28"
lazy val dockingVersion         = "1.1.1"
lazy val dspVersion             = "1.3.0-SNAPSHOT"

lazy val commonSettings = Seq(
  version            := projectVersion,
  organization       := "de.sciss",
  scalaVersion       := "2.11.6",
  crossScalaVersions := Seq("2.11.6", "2.10.5"),
  homepage           := Some(url("https://github.com/Sciss/" + baseName)),
  licenses           := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt")),
  scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture"),
  aggregate in assembly := false,  // https://github.com/sbt/sbt-assembly/issues/147
  // ---- publishing ----
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

lazy val root = Project(id = baseNameL, base = file(".")).
  aggregate(core, interpreter, plotting, app).
  dependsOn(core, interpreter, plotting, app).
  settings(commonSettings).
  settings(
    publishArtifact in (Compile, packageBin) := false, // there are no binaries
    publishArtifact in (Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in (Compile, packageSrc) := false, // there are no sources
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
    },
    // ---- appbundle ----
    appbundle.mainClass := appMainClass,
    appbundle.target := baseDirectory.value,
    appbundle.name   := "ScalaCollider",
    appbundle.icon   := Some(file("icons/application.png"))
  )

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
    description    := "Standalone application for ScalaCollider",
    libraryDependencies ++= Seq(
      // experiment with making sources and docs available.
      // cf. http://stackoverflow.com/questions/22160701
 //     "de.sciss" %% "scalacollider" % scalaColliderVersion,
 //     "de.sciss" %% "scalacollider" % scalaColliderVersion classifier "javadoc",
 //     "de.sciss" %% "scalacollider" % scalaColliderVersion classifier "sources",
      "de.sciss"                 %% "desktop"               % desktopVersion, // withJavadoc() withSources(),
      "de.sciss"                 %% "fileutil"              % fileUtilVersion,
      "de.sciss"                 %% "kollflitz"             % kollFlitzVersion,
      "com.thoughtworks.xstream" % "xstream" % xstreamVersion, // PROBLEM WITH MAVEN CENTRAL
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

