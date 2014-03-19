# ScalaCollider-Swing

(experimental app branch)

## statement

ScalaCollider-Swing is a Swing GUI front-end for ScalaCollider. (C)opyright 2008-2014 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU General Public License](http://github.com/Sciss/ScalaColliderSwing/blob/master/licenses/ScalaColliderSwing-License.txt) v2+ and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`.

## requirements / building

ScalaCollider-Swing compiles against Scala 2.10 using sbt 0.13. Targets for sbt: `clean`, `update`, `compile`, `doc`, `package`, `standalone`, `appbundle`, where `standalone` creates a fully self-contained jar, and `appbundle` updates the Mac OS X application bundle.

A the `sbt` shell script by [paulp](https://github.com/paulp/sbt-extras), made available under a BSD-style license, is now included. So if you do not want to install `sbt` directly on your system, you can just use that script: `./sbt`.

To link to ScalaCollider-Swing:

    "de.sciss" %% "scalacolliderswing" % v

The current version `v` is `"1.13.+"`.

## running

Note that there is currently a problem with getting the embedded interpreter to work when using `sbt run`. You can use the application bundle instead on OS X:

    $ sbt scalacolliderswing-app/appbundle
    $ open app/ScalaColliderSwing.app

...or create the fully self-contained (double-clickable) jar on other platforms:

    $ sbt scalacolliderswing-app/assembly
    $ java -jar app/ScalaColliderSwing.jar

## documentation

 - There is a small screencast intro at [www.screencast.com/t/YjUwNDZjMT](http://www.screencast.com/t/YjUwNDZjMT)
 - ScalaCollider was also presented at [Scala Days 2012](http://skillsmatter.com/podcast/scala/scalacollider)

## download

The current version can be downloaded from [github.com/Sciss/ScalaColliderSwing](http://github.com/Sciss/ScalaColliderSwing).
