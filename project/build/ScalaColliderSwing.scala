import xml._
import sbt.{ FileUtilities => FU, _}

class ScalaColliderSwingProject( info: ProjectInfo ) extends ProguardProject( info ) {
   val scalaCollider          = "de.sciss" %% "scalacollider" % "0.20"
   val scalaInterpreterPane   = "de.sciss" %% "scalainterpreterpane" % "0.16"
   val prefuse                = "prefuse" % "prefuse" % "beta-SNAPSHOT" from "http://github.com/downloads/Sciss/ScalaColliderSwing/prefuse-beta-SNAPSHOT.jar"

   val camelCaseName          = "ScalaColliderSwing"
   def appBundleName          = camelCaseName + ".app"
   def appBundleContentsPath  = appBundleName / "Contents"
   def appBundleJavaPath      = appBundleContentsPath / "Resources" / "Java"

   private val jarExt                 = ".jar"
   private val jarFilter: FileFilter  = "*" + jarExt

   /**
    *    Note: there have been always problems in the shrinking,
    *    even with the most severe keep options, and anyway the
    *    size reduction was minimal (some 8%), so now we just
    *    use proguard to put everything in one jar, without
    *    shrinking.
    */
   override def proguardOptions = List(
//      "-keep class de.sciss.** { *; }",
//      "-keep class scala.** { *; }",
//      "-keep class ch.epfl.** { *; }",
//      "-keep class prefuse.** { *; }",
      "-target 1.6",
//      "-dontoptimize",
      "-dontobfuscate",
      "-dontshrink",
      "-dontpreverify",
      "-forceprocessing"
   )

   override def minJarName = camelCaseName + "-full" + jarExt
   override def minJarPath: Path = minJarName

   private def allJarsPath = (publicClasspath +++ buildLibraryJar +++ jarPath) ** jarFilter
   override def proguardInJars = allJarsPath --- jarPath // the plugin adds jarPath again!!

   def packageAppTask = task {
      val jarsPath               = allJarsPath
      val javaPath               = appBundleJavaPath
      val cleanPaths             = javaPath * jarFilter
      val quiet                  = false
      val versionedNamePattern   = """([^-_]*)[-_].*.jar""".r

      FU.clean( cleanPaths.get, quiet, log )

      for( fromPath <- jarsPath.get ) {
         val versionedName = fromPath.asFile.getName
         val plainName     = versionedName match {
            case versionedNamePattern( name ) if( name != "scala" ) => name + jarExt
            case n => n
         }
         val toPath = javaPath / plainName
         log.log( if(quiet) Level.Debug else Level.Info, "Copying to file " + toPath.asFile )
         FU.copyFile( fromPath, toPath, log )
      }

// plist is a real shitty format. we will need apache commons configuration
// to parse it. that in turn means we need depedancies for this task...
// will do that in a future version. for now, just let's assume
// the classpath is correctly set in Info.plist
//
//      val infoXML = XML.loadFile( appBundleContentsPath / "Info.plist" )
//      println( infoXML )

      None // what is this for?
   }

   private def exec( quiet: Boolean, cmdAndArgs: String* ) : Option[ String ] = {
      val command = Process( cmdAndArgs )
      log.log( if( quiet ) Level.Debug else Level.Info, cmdAndArgs.mkString( "Executing command ", " ", "" ))
      val exitValue = command.run( log ).exitValue() // don't buffer output
      if( exitValue == 0 ) None else Some( "Nonzero exit value: " + exitValue )
   }

/*
   def standaloneTask = task {
      val jarsPath      = allJarsPath
      val toPath: Path  = camelCaseName + "-full" + jarExt   
      FU.withTemporaryDirectory( log )( tmpDir => {
         val tmpName = tmpDir.getAbsolutePath
         jarsPath.get.projection.map( fromPath =>
            exec( false, "unzip", "-q", if( fromPath == jarPath ) "-o" else "-n", "-d", tmpName, fromPath.absolutePath ))
         .find( _.isDefined ) getOrElse
            exec( false, "jar", "cfM", toPath.absolutePath, "-C", tmpName, "." )
      })
   }

   protected def standaloneAction =
      standaloneTask.dependsOn( `package` ) describedAs "Creates one big self-contained, executable jar."
*/

   protected def packageAppAction =
      packageAppTask.dependsOn( `package` ) describedAs "Copies all relevant jars into the OS X app bundle."

   lazy val packageApp = packageAppAction 
//   lazy val standalone = standaloneAction
   lazy val standalone = proguard
}
