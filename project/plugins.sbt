resolvers += Resolver.url( "sbt-plugin-releases",
   url( "http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/" ))( Resolver.ivyStylePatterns )

resolvers ++= Seq(
   "less is" at "http://repo.lessis.me",
   "coda" at "http://repo.codahale.com"
)

addSbtPlugin( "com.jsuereth" % "xsbt-gpg-plugin" % "0.6" ) // sorry... sbt ignores ~/.sbt/plugins for some reason :-(

addSbtPlugin( "me.lessis" % "ls-sbt" % "0.1.1" )

addSbtPlugin( "com.eed3si9n" % "sbt-assembly" % "0.7.4" )

addSbtPlugin( "de.sciss" % "sbt-appbundle" % "0.13" )
