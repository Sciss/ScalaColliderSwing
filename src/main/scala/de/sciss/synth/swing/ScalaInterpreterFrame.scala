/*
 *  ScalaInterpreterFrame.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.swing

import de.sciss.scalainterpreter.{CodePane, LogPane, InterpreterPane, NamedParam, Interpreter}
import javax.swing.{ JFrame, JSplitPane, SwingConstants, WindowConstants }
import de.sciss.synth.swing.ScalaColliderSwing.REPLSupport
import java.io.{IOException, File, FileInputStream}
import java.awt.GraphicsEnvironment

class ScalaInterpreterFrame(replSupport: REPLSupport)
  extends JFrame("ScalaCollider Interpreter") {

  private val lp = {
    val p = LogPane()
    p.makeDefault(error = true)
    p
  }

  val pane = {
    //      val paneCfg = InterpreterPane.Config()
    // note: for the auto-completion in the pane to work, we must
    // import de.sciss.synth.ugen._ instead of ugen._
    // ; also name aliasing seems to be broken, thus the stuff
    // in de.sciss.osc is hidden

    val codeCfg = CodePane.Config()

    val file = new File(/* new File( "" ).getAbsoluteFile.getParentFile, */ "interpreter.txt")
    if (file.isFile) try {
      val fis = new FileInputStream(file)
      val txt = try {
        val arr = new Array[Byte](fis.available())
        fis.read(arr)
        new String(arr, "UTF-8")
      } finally {
        fis.close()
      }
      codeCfg.text = txt

    } catch {
      case e: IOException => e.printStackTrace()
    }

    val intpCfg = Interpreter.Config()
    intpCfg.imports = List(
      //         "Predef.{any2stringadd => _}",
      "scala.math._",
      "de.sciss.osc",
      "de.sciss.osc.{TCP, UDP}",
      "de.sciss.osc.Dump.{Off, Both, Text}",
      "de.sciss.osc.Implicits._",
      "de.sciss.synth._",
      "de.sciss.synth.Ops._",
      "de.sciss.synth.swing.SynthGraphPanel._",
      "de.sciss.synth.swing.Implicits._",
      "de.sciss.synth.ugen._",
      "replSupport._"
    )
    // intpCfg.quietImports = false

    intpCfg.bindings = List(NamedParam("replSupport", replSupport))
    //         in.bind( "s", classOf[ Server ].getName, ntp )
    //         in.bind( "in", classOf[ Interpreter ].getName, in )
    intpCfg.out = Some(lp.writer)

    InterpreterPane(interpreterConfig = intpCfg, codePaneConfig = codeCfg)
  }

  //   private val sync = new AnyRef
  //   private var inCode: Option[ IMain => Unit ] = None

  // ---- constructor ----
  {
    val cp = getContentPane
    //      val lpCfg = LogPane.Settings()
    //      val lp = LogPane()
    //      pane.out = Some( lp.writer )
    //      lp.makeDefault( error = true )
    //      Console.setOut( lp.outputStream )
    //      Console.setErr( lp.outputStream )
    //      System.setErr( new PrintStream( lp.outputStream ))

    //      pane.init()
    val sp = new JSplitPane(SwingConstants.HORIZONTAL)
    sp.setTopComponent(pane.component)
    sp.setBottomComponent(lp.component)
    cp.add(sp)
    val b = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
    setSize(b.width / 2, b.height * 7 / 8)
    sp.setDividerLocation(b.height * 2 / 3)
    setLocationRelativeTo(null)
    //      setLocation( x, getY )
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
    //      setVisible( true )
  }

  def withInterpreter(fun: InterpreterPane => Unit): Unit = {
    //      sync.synchronized {
    fun(pane)
    //         getOrElse {
    //            inCode = Some( fun )
    //         }
    //      }
  }
}