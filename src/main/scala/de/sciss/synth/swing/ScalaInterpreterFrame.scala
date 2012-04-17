/*
 *  ScalaInterpreterFrame.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2012 Hanns Holger Rutz. All rights reserved.
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

import java.awt.GraphicsEnvironment
import de.sciss.scalainterpreter.{ LogPane, ScalaInterpreterPane }
import javax.swing.{ JFrame, JSplitPane, SwingConstants, WindowConstants }
import de.sciss.synth.swing.ScalaColliderSwing.REPLSupport
import tools.nsc.interpreter.{NamedParam, IMain}
import java.io.{File, FileInputStream, PrintStream}

class ScalaInterpreterFrame( replSupport: REPLSupport )
extends JFrame( "ScalaCollider Interpreter" ) {

   val pane = new ScalaInterpreterPane
   private val sync = new AnyRef
//   private var inCode: Option[ IMain => Unit ] = None
   
   // ---- constructor ----
   {
      val cp = getContentPane

//      pane.initialCode = Some(
//"""
//import math._
//import de.sciss.osc.{ OSCBundle, OSCMessage, OSCPacket, UDP, TCP }
//import de.sciss.synth._
//import de.sciss.synth.swing.SynthGraphPanel._
//import de.sciss.synth.swing.Implicits._
//import de.sciss.synth.io._
//import de.sciss.synth.osc._
//import de.sciss.synth.ugen._
//import replSupport._
//"""
//      )

      // note: for the auto-completion in the pane to work, we must
      // import de.sciss.synth.ugen._ instead of ugen._
      // ; also name aliasing seems to be broken, thus the stuff
      // in de.sciss.osc is hidden
      pane.customImports = Seq(
         "math._",
         "de.sciss.synth.{osc => sosc, _}", "de.sciss.osc", "osc.Implicits._",
         "osc.Dump.{Off, Both, Text}", "osc.{TCP, UDP}", "swing.SynthGraphPanel._",
         "swing.Implicits._", /* "io._", */ "de.sciss.synth.ugen._", "replSupport._"
      )

      pane.customBindings = Seq( NamedParam( "replSupport", replSupport ))
//         in.bind( "s", classOf[ Server ].getName, ntp )
//         in.bind( "in", classOf[ Interpreter ].getName, in )

      val lp = new LogPane
      lp.init()
      pane.out = Some( lp.writer )
      Console.setOut( lp.outputStream )
      Console.setErr( lp.outputStream )
      System.setErr( new PrintStream( lp.outputStream ))

      try {
         val fis  = new FileInputStream( new File( new File( "" ).getAbsoluteFile.getParentFile, "interpreter.txt" ))
         val txt  = try {
            val arr = new Array[ Byte ]( fis.available() )
            fis.read( arr )
            new String( arr, "UTF-8" )
         } finally {
            fis.close()
         }
         pane.initialText += txt

      } catch {
         case e => e.printStackTrace()
      }

      pane.init()
      val sp = new JSplitPane( SwingConstants.HORIZONTAL )
      sp.setTopComponent( pane )
      sp.setBottomComponent( lp )
      cp.add( sp )
      val b = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
      setSize( b.width / 2, b.height * 7 / 8 )
      sp.setDividerLocation( b.height * 2 / 3 )
      setLocationRelativeTo( null )
//      setLocation( x, getY )
      setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
//      setVisible( true )
   }

   def withInterpreter( fun: IMain => Unit ) {
      sync.synchronized {
         pane.interpreter.map( fun( _ ))
//         getOrElse {
//            inCode = Some( fun )
//         }
      }
   }
}