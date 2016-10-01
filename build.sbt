name           := "ScalaColliderSwing"
version        := "0.34"
organization   := "de.sciss"
scalaVersion   := "2.11.8"
description    := "A Swing and REPL front-end for ScalaCollider"
homepage       := Some(url("https://github.com/Sciss/ScalaColliderSwing"))
licenses       := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))

libraryDependencies ++= Seq(
   "de.sciss" %% "scalacollider"        % "0.34",
   "de.sciss" %% "scalainterpreterpane" % "1.7.3",
   "de.sciss" %  "prefuse-core"         % "0.21",
   "de.sciss" %% "audiowidgets"         % "0.13"
)

scalacOptions ++= Seq("-deprecation", "-unchecked")

// ---- publishing ----

publishMavenStyle := true

publishTo :=
   Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
<scm>
  <url>git@github.com:Sciss/ScalaColliderSwing.git</url>
  <connection>scm:git:git@github.com:Sciss/ScalaColliderSwing.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>

// ---- packaging ----

// seq( assemblySettings: _* )
// test in assembly := {}
// seq( appbundle.settings: _* )
// appbundle.icon := Some( file( "application.icns" ))
