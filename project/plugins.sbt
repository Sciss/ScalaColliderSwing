resolvers += Resolver.url( "sbt-plugin-releases",
   url( "http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/" ))( Resolver.ivyStylePatterns )

addSbtPlugin( "com.eed3si9n" % "sbt-assembly" % "0.7.3" )

addSbtPlugin( "de.sciss" % "sbt-appbundle" % "0.12" )