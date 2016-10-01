/*
 *  GUI.scala
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

import de.sciss.gui.j.PeakMeter

import sys.error
import de.sciss.osc.Message
import de.sciss.synth.{AudioBus, addToHead, addToTail, Group => SGroup, Server => SServer}

import swing.{BoxPanel, Component, Frame, Orientation, Swing}

object GUI {
   class Factory[ T ] private[swing] ( target: => T ) { def gui: T = target }

   class Group private[swing] ( val group: SGroup ) {
      def tree { error( "TODO" )}
   }

   class Server private[swing] ( val server: SServer ) {
      def tree { new Group( server.rootNode ).tree }
      def meter : Frame = {
         val name       = server.name
         val opt        = server.config
         val numInputs  = opt.inputBusChannels
         val numOutputs = opt.outputBusChannels
         val sections   = List(
            ("in",  AudioBus( server, numOutputs, numInputs ), addToHead),   // hardware inputs
            ("out", AudioBus( server, 0, numOutputs ), addToTail)            // hardware outputs
         ).map {
            case (suffix, bus, addAction) =>
               val meter = new PeakMeter {
                  numChannels    = bus.numChannels
                  hasCaption     = true
                  borderVisible  = true
captionLabels = false // XXX currently they have wrong layout
               }

               import de.sciss.synth._
               import ugen._

               val df = SynthDef( "$" + name + "-" + suffix + "putmeter" ) {
                  val sig     = In.ar( bus.index, bus.numChannels )
                  val tr      = Impulse.kr( 20 )
                  val peak    = Peak.kr( sig, tr )
                  val rms     = A2K.kr( Lag.ar( sig.squared, 0.1 ))
                  SendReply.kr( tr, Flatten( Zip( peak, rms )), "/$meter" )
               }
               val synth = df.play( target = server.rootNode, addAction = addAction )
               val resp = osc.Responder.add({
                  case Message( "/$meter", synth.id, _, vals @ _* ) =>
                     val pairs   = vals.asInstanceOf[ Seq[ Float ]].toIndexedSeq
                     val time    = System.currentTimeMillis
                     Swing.onEDT( meter.update( pairs, 0, time ))
               }, server )

               (meter, synth, resp)
         }

         val f = new Frame {
            title = "Meter : " + name
            contents = new BoxPanel( Orientation.Horizontal ) {
               contents ++= sections.map(tup => Component.wrap(tup._1))
            }
            pack().centerOnScreen()

            override def closeOperation() {
               sections.foreach {
                  case (meter, synth, resp) =>
                     this.dispose()
                     resp.remove
                     synth.free
                     meter.dispose()
               }
            }
            visible = true
         }
         f
      }
   }
}