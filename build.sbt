import AssemblyKeys._

name           := "scalacolliderswing"

appbundleName  := "ScalaColliderSwing"

version        := "0.30-SNAPSHOT"

organization   := "de.sciss"

scalaVersion   := "2.9.1"

libraryDependencies ++= Seq(
   "de.sciss" %% "scalacollider" % "0.30-SNAPSHOT",
   "de.sciss" %% "scalainterpreterpane" % "0.18",
   "de.sciss" % "prefuse-core" % "0.21",
   "de.sciss" %% "scalaaudiowidgets" % "0.10"
)

retrieveManaged := true

scalacOptions += "-deprecation"

// ---- publishing ----

publishTo := Some(ScalaToolsReleases)

pomExtra :=
<licenses>
  <license>
    <name>GPL v2+</name>
    <url>http://www.gnu.org/licenses/gpl-2.0.txt</url>
    <distribution>repo</distribution>
  </license>
</licenses>

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

// ---- packaging ----

seq( assemblySettings: _* )

test in assembly := {}

seq( appbundleSettings: _* )
