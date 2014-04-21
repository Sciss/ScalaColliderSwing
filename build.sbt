import AssemblyKeys._

lazy val baseName               = "ScalaColliderSwing"

def baseNameL                   = baseName.toLowerCase

lazy val projectVersion         = "1.16.0"

lazy val scalaColliderVersion   = "1.12.0"

lazy val interpreterPaneVersion = "1.6.2"

// lazy val syntaxPaneVersion      = "1.1.2"

lazy val desktopVersion         = "0.5.1"

lazy val audioWidgetsVersion    = "1.6.1"

lazy val fileUtilVersion        = "1.1.1"

lazy val kollFlitzVersion       = "0.2.0"

lazy val webLaFVersion          = "1.27"

lazy val dockingVersion         = "1.1.1"

// lazy val swingBoxVersion        = "1.0"

lazy val pdflitzVersion         = "1.1.0"

lazy val chartVersion           = "0.4.2"

lazy val prefuseVersion         = "0.21"

lazy val commonSettings = Project.defaultSettings ++ Seq(
  version            := projectVersion,
  organization       := "de.sciss",
  scalaVersion       := "2.11.0",
  crossScalaVersions := Seq("2.11.0", "2.10.4"),
  homepage           := Some(url("https://github.com/Sciss/" + baseName)),
  licenses           := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt")),
  scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature", "-Xfuture"),
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

def appMainClass = Some("de.sciss.synth.swing.Main")

lazy val root = Project(
  id           = baseNameL,
  base         = file("."),
  aggregate    = Seq(core, interpreter, app),
  dependencies = Seq(core, interpreter, app),
  settings     = commonSettings ++ assemblySettings ++ Seq(
    publishArtifact in (Compile, packageBin) := false, // there are no binaries
    publishArtifact in (Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in (Compile, packageSrc) := false, // there are no sources
    // ---- assembly ----
    test      in assembly := (),
    mainClass in assembly := appMainClass,
    target    in assembly := baseDirectory.value,
    jarName   in assembly := "ScalaCollider.jar",
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
      {
        case "logback.xml" => MergeStrategy.last
        case x => old(x)
      }
    },
    // ---- appbundle ----
    appbundle.mainClass := appMainClass,
    appbundle.target := baseDirectory.value,
    appbundle.name   := "ScalaCollider",
    appbundle.icon   := Some(file("icons/application.png"))
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
      "de.sciss" %  "prefuse-core"         % prefuseVersion,
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
      // "de.sciss" %  "syntaxpane"           % syntaxPaneVersion
    )
  )
)

lazy val app = Project(
  id  = s"$baseNameL-app",
  base = file("app"),
  dependencies = Seq(core, interpreter),
  settings = commonSettings ++ Seq(
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
      "de.sciss"                 %% "pdflitz"               % pdflitzVersion,
 //     "de.sciss"                 %  "weblaf"                % webLaFVersion,
      "org.dockingframes"        %  "docking-frames-common" % dockingVersion,
 //     "net.sf.cssbox"            %  "swingbox"              % swingBoxVersion,
      // "org.fusesource.scalamd"   %% "scalamd"               % "1.6",
      "com.github.wookietreiber" %% "scala-chart"           % chartVersion
    )
  )
)

// ---- ls.implicit.ly ----

seq(lsSettings :_*)

(LsKeys.tags   in LsKeys.lsync) := Seq("sound-synthesis", "sound", "music", "supercollider")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) := Some(name.value)

