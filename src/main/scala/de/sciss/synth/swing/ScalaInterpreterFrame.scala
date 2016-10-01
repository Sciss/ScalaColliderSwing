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
import java.io.{File, FileInputStream}
import javax.swing.{JFrame, JSplitPane, SwingConstants, WindowConstants}

import de.sciss.scalainterpreter.{CodePane, Interpreter, InterpreterPane, LogPane}
import de.sciss.synth.swing.ScalaColliderSwing.REPLSupport

import scala.tools.nsc.interpreter.{IMain, NamedParam}

class ScalaInterpreterFrame( replSupport: REPLSupport )
extends JFrame( "ScalaCollider Interpreter" ) {

   val iConfig = Interpreter.Config()
  val pConfig = InterpreterPane.Config()
  val cConfig = CodePane.Config()
  val lpConfig = LogPane.Config()
  val lp = LogPane(lpConfig)

  iConfig.imports ++= Seq(
      "math._",
      "de.sciss.synth.{osc => sosc, _}", "de.sciss.osc", "osc.Implicits._",
      "osc.Dump.{Off, Both, Text}", "osc.{TCP, UDP}", "swing.SynthGraphPanel._",
      "swing.Implicits._", /* "io._", */ "de.sciss.synth.ugen._", "replSupport._"
  )

  iConfig.bindings :+= NamedParam( "replSupport", replSupport )
  iConfig.out = Some( lp.writer )

  val file = new File( /* new File( "" ).getAbsoluteFile.getParentFile, */ "interpreter.txt" )
  if( file.exists() ) try {
    val fis  = new FileInputStream( file )
    val txt  = try {
      val arr = new Array[ Byte ]( fis.available() )
      fis.read( arr )
      new String( arr, "UTF-8" )
    } finally {
      fis.close()
    }
    cConfig.text += txt

  } catch {
    case e: Throwable => e.printStackTrace()
  }

  val pane = InterpreterPane(pConfig, iConfig, cConfig)
   private val sync = new AnyRef
//   private var inCode: Option[ IMain => Unit ] = None
   
   // ---- constructor ----
   {
      val cp = getContentPane

     lp.makeDefault()

//      Console.setOut( lp.outputStream )
//      Console.setErr( lp.outputStream )
//      System.setErr( new PrintStream( lp.outputStream ))

//      pane.init()
      val sp = new JSplitPane( SwingConstants.HORIZONTAL )
      sp.setTopComponent( pane.component.peer )
      sp.setBottomComponent( lp.component.peer )
      cp.add( sp )
      val b = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
      setSize( b.width / 2, b.height * 7 / 8 )
      sp.setDividerLocation( b.height * 2 / 3 )
      setLocationRelativeTo( null )
//      setLocation( x, getY )
      setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
//      setVisible( true )
   }

//   def withInterpreter( fun: IMain => Unit ) {
//      sync.synchronized {
//         pane.interpreter.map( fun( _ ))
////         getOrElse {
////            inCode = Some( fun )
////         }
//      }
//   }
}