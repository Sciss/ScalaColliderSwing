import AssemblyKeys._

lazy val baseName               = "ScalaColliderSwing"

def baseNameL                   = baseName.toLowerCase

lazy val projectVersion         = "1.14.0-SNAPSHOT"

lazy val scalaColliderVersion   = "1.10.1"

lazy val interpreterPaneVersion = "1.6.0"

lazy val desktopVersion         = "0.4.2"

lazy val audioWidgetsVersion    = "1.5.0"

lazy val fileUtilVersion        = "1.1.0"

lazy val commonSettings = Project.defaultSettings ++ Seq(
  version            := projectVersion,
  organization       := "de.sciss",
  scalaVersion       := "2.10.4",
  crossScalaVersions := Seq("2.11.0-RC3", "2.10.4"),
  homepage           := Some(url("https://github.com/Sciss/" + baseName)),
  licenses           := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt")),
  scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature"),
  retrieveManaged    := true,
  // ---- publishing ----
  publishMavenStyle := true,
  publishTo :=
    Some(if (version.value endsWith "-SNAPSHOT")
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

lazy val root = Project(
  id           = baseNameL,
  base         = file("."),
  aggregate    = Seq(core, interpreter, app),
  dependencies = Seq(core, interpreter, app),
  settings     = commonSettings ++ Seq(
    publishArtifact in (Compile, packageBin) := false, // there are no binaries
    publishArtifact in (Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in (Compile, packageSrc) := false  // there are no sources
  )
)

lazy val core = Project(
  id   = s"$baseNameL-core",
  base = file("core"),
  settings = commonSettings ++ buildInfoSettings ++ Seq(
    name           := s"$baseName-core",
    description    := "Swing components for ScalaCollider",
    libraryDependencies ++= Seq(
      "de.sciss" %% "scalacollider"        % scalaColliderVersion,
      "de.sciss" %  "prefuse-core"         % "0.21",
      "de.sciss" %% "audiowidgets-swing"   % audioWidgetsVersion
    ),
    // ---- build info ----
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.synth.swing"
  )
)

lazy val interpreter = Project(
  id  = s"$baseNameL-interpreter",
  base = file("interpreter"),
  dependencies = Seq(core),
  settings = commonSettings ++ Seq(
    description    := "REPL for ScalaCollider",
    libraryDependencies ++= Seq(
      "de.sciss" %% "scalainterpreterpane" % interpreterPaneVersion
    )
  )
)

lazy val app = Project(
  id  = s"$baseNameL-app",
  base = file("app"),
  dependencies = Seq(core, interpreter),
  settings = commonSettings ++ assemblySettings ++ Seq(
    description    := "Standalone application for ScalaCollider",
    libraryDependencies ++= Seq(
      // experiment with making sources and docs available.
      // cf. http://stackoverflow.com/questions/22160701
 //     "de.sciss" %% "scalacollider" % scalaColliderVersion,
 //     "de.sciss" %% "scalacollider" % scalaColliderVersion classifier "javadoc",
 //     "de.sciss" %% "scalacollider" % scalaColliderVersion classifier "sources",
      "de.sciss"                 %% "desktop"               % desktopVersion, // withJavadoc() withSources(),
      "de.sciss"                 %% "fileutil"              % fileUtilVersion,
      "org.dockingframes"        %  "docking-frames-common" % "1.1.1",
      "net.sf.cssbox"            %  "swingbox"              % "1.0",
      "org.fusesource.scalamd"   %% "scalamd"               % "1.6",
      "com.github.wookietreiber" %% "scala-chart"           % "0.3.0"
    ),
    // ---- assembly ----
    test      in assembly := (),
    mainClass in assembly := Some("de.sciss.synth.swing.Main"),
    target    in assembly := baseDirectory.value,
    jarName   in assembly := s"$baseName.jar",
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
      {
        case "logback.xml" => MergeStrategy.last
        case x => old(x)
      }
    }
  )
)

// ---- packaging ----

seq(assemblySettings: _*)

seq(appbundle.settings: _*)

appbundle.icon      := Some(file("application.icns"))

appbundle.target    := baseDirectory.value

// ---- ls.implicit.ly ----

seq(lsSettings :_*)

(LsKeys.tags   in LsKeys.lsync) := Seq("sound-synthesis", "sound", "music", "supercollider")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) := Some(name.value)

