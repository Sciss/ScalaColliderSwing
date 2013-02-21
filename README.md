## ScalaCollider-Swing

### statement

ScalaCollider-Swing is a Swing GUI front-end for ScalaCollider. (C)opyright 2008-2013 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU General Public License](http://github.com/Sciss/ScalaColliderSwing/blob/master/licenses/ScalaColliderSwing-License.txt) and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`.

### requirements / building

ScalaCollider-Swing compiles against Scala 2.10 using sbt 0.12. Targets for sbt: `clean`, `update`, `compile`, `doc`, `package`, `standalone`, `appbundle`, where `standalone` creates a fully self-contained jar, and `appbundle` updates the Mac OS X application bundle.

To link to ScalaCollider-Swing:

    "de.sciss" %% "scalacolliderswing" % v

The current version `v` is `1.5.+`

### running

Note that there is currently a problem with getting the embedded interpreter to work when using `sbt run`. You can use the application bundle instead on OS X:

    $ sbt appbundle
    $ open ./ScalaColliderSwing.app

...or create the fully self-contained (double-clickable) jar on other platforms:

    $ sbt assembly
    $ java -jar target/scalacolliderswing-assembly-<current-version>.jar

Upon startup, a file named `"interpreter.txt"`, which should be a plain UTF-8 text file, is looked up in the current directory. If that is found, it will be read into the interpreter pane.

### creating an IntelliJ IDEA project

To develop ScalaCollider-Swing under IntelliJ IDEA, you can set up a project with the sbt-idea plugin. If you haven't globally installed the sbt-idea plugin yet, create the following contents in `~/.sbt/plugins/build.sbt`:

    addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.1.0")

Then to create the IDEA project, run `sbt gen-idea`.

### documentation

 - There is a small screencast intro at [www.screencast.com/t/YjUwNDZjMT](http://www.screencast.com/t/YjUwNDZjMT)
 - ScalaCollider was also presented at [Scala Days 2012](http://skillsmatter.com/podcast/scala/scalacollider)

### download

The current version can be downloaded from [github.com/Sciss/ScalaColliderSwing](http://github.com/Sciss/ScalaColliderSwing).
