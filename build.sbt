import AssemblyKeys._

name           := "ScalaColliderSwing"

version        := "0.33-SNAPSHOT"

organization   := "de.sciss"

scalaVersion   := "2.9.1"

description := "A Swing and REPL front-end for ScalaCollider"

homepage := Some( url( "https://github.com/Sciss/ScalaColliderSwing" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

resolvers += "Clojars Repository" at "http://clojars.org/repo"  // for jsyntaxpane

libraryDependencies ++= Seq(
   "de.sciss" %% "scalacollider" % "0.33-SNAPSHOT",
   "de.sciss" %% "scalainterpreterpane" % "0.20",
   "de.sciss" % "prefuse-core" % "0.21",
   "de.sciss" %% "scalaaudiowidgets" % "0.10"
)

retrieveManaged := true

// this should make it possible to launch from sbt, but there is still a class path issue?
// fork in run := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" )

// ---- publishing ----

publishTo <<= version { (v: String) =>
   Some( "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/".+(
      if( v.endsWith( "-SNAPSHOT")) "snapshots/" else "releases/"
   ))
}

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

seq( appbundle.settings: _* )

appbundle.icon := Some( file( "application.icns" ))

