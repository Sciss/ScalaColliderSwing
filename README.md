![logo](https://raw.githubusercontent.com/wiki/Sciss/ScalaCollider/images/scalacollider_logo.png)

# ScalaCollider-Swing

## statement

ScalaCollider-Swing is a Swing GUI front-end for [ScalaCollider](https://github.com/Sciss/ScalaCollider). ScalaCollider is a client for the [SuperCollider](http://supercollider.sourceforge.net/) sound synthesis server.

(C)opyright 2008-2014 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU General Public License](http://github.com/Sciss/ScalaColliderSwing/blob/master/licenses/ScalaColliderSwing-License.txt) v3+ and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`.

Please see the `licenses` folder for details. It contains the license headers for all dependencies and transitive dependencies. For the _binary release_ of ScalaCollider-Swing, source code is not included but available via the respective OSS project pages, as indicated in the license files, or&mdash;in compliance with GPL/LGPL&mdash;on request via E-Mail. All source code with group-ID `de.sciss` is available from [github.com/Sciss](https://github.com/Sciss).

## downloading

- A binary (executable) version is provided via [bintray.com/sciss/generic/ScalaCollider](https://bintray.com/sciss/generic/ScalaCollider).
- The source code can be downloaded from [github.com/Sciss/ScalaColliderSwing](http://github.com/Sciss/ScalaColliderSwing).

## building from source

ScalaCollider-Swing compiles against Scala 2.11 and 2.10 using sbt 0.13.

-`sbt assembly` creates a fully self-contained jar (platform neutral)
-`sbt appbundle` creates an Mac OS X application bundle

__Note__: The `appbundle` task is currently broken because it doesn't work with multi-project builds. Therefore, on OS X you currently need to use the fat jar as well.

A the `sbt` shell script by [paulp](https://github.com/paulp/sbt-extras), made available under a BSD-style license, is now included. So if you do not want to install `sbt` directly on your system, you can just use that script: `./sbt`.

To link to ScalaCollider-Swing:

    "de.sciss" %% "scalacolliderswing" % v

Or just to some sub-modules:

    "de.sciss" %% "scalacolliderswing-core"        % v  // only the core library extensions
    "de.sciss" %% "scalacolliderswing-interpreter" % v  // plus the Scala REPL
    "de.sciss" %% "scalacolliderswing-plotting"    % v  // plus plotting functions
    "de.sciss" %% "scalacolliderswing-app"         % v  // plus the desktop IDE

The current stable version `v` is `"1.19.0"`.

## running

The standalone jar, created via `sbt assembly` produces `ScalaCollider.jar` which is double-clickable and can be run via:

    $ java -jar ScalaCollider.jar

A Linux desktop template is included. You will have to edit this file to change the installation path. Future versions may simplify this procedure or provide a direct `.deb` package.

On OS X, you can use the __`ScalaCollider.command` shell script__ now. It should be associated automatically to open with `Terminal.app`.

## documentation

 - There is a small screencast intro at [www.screencast.com/t/YjUwNDZjMT](http://www.screencast.com/t/YjUwNDZjMT)
 - ScalaCollider was also presented at [Scala Days 2012](http://skillsmatter.com/podcast/scala/scalacollider)
 - See the [ScalaCollider](https://github.com/Sciss/ScalaCollider) site for mailing-list information, Wiki, and additional resources

### GUI commands

The GUI extensions are available by calling the special `.gui` method on supported objects. The extensions are imported with `import de.sciss.synth.swing.Implicits._`.

```scala

    // server
    s.gui.meter()               // show peak meters for main I/O
    s.gui.tree()                // show node tree for server's root group

    // group
    someGroup.gui.tree()        // show node tree with a given group as root

    // audio-bus
    someBus.gui.meter()         // show peak meters for the given bus
    someBus.gui.waveform(dur)   // record and plot bus output for duration in seconds

    // graph functions
    val fun = gui {             // use `gui { ... }` instead of `play { ... }` to capture function
      PinkNoise.ar
    }
    fun.waveform(dur)           // record and plot graph function for duration in seconds
```

Experimental support for JFreeChart is added (app sub-project only). Extensions are imported with `import de.sciss.synth.swing.Plotting.Implicits._`.

```scala

    Vector.fill(200)(random).plot()                 // 1D plot, lines
    Vector.fill(100)(random).plot(discrete = true)  // 1D plot, sample-and-hold

    Vector.tabulate(316) { i => (i * 0.003 * 0.2).cos -> (i * 0.025).sin } .plot()  // 2D plot, scatter

    Vector.fill(3)(Vector.fill(100)(random-random).integrate).plot() // multiple 1D plots
```

Chart properties and export to PNG and PDF are available through a context menu.

