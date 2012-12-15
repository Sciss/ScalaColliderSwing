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

import sys.error
import de.sciss.gui.PeakMeter
import de.sciss.osc.Message
import de.sciss.synth.{Group => SGroup, Server => SServer, AudioBus => SAudioBus, Synth => SSynth, osc => sosc, SynthDef => SSynthDef, AddAction, Ops, addToHead, addToTail}
import swing.{Swing, BoxPanel, Orientation, Frame}
import de.sciss.{synth, osc}
import collection.breakOut

object GUI {
   final class Factory[ T ] private[swing] ( target: => T ) { def gui: T = target }

   final class Group private[swing] ( val group: SGroup ) {
      def tree() { error( "TODO" )}
   }

   final class AudioBus private[swing] ( val bus: SAudioBus ) {
      def meter( target: SGroup = bus.server.rootNode, addAction: AddAction = addToTail ) : Frame = {
         makeAudioBusMeter( bus.server, bus.toString(), AudioBusMeterConfig( bus, target, addAction ) :: Nil )
      }
   }

   private final case class AudioBusMeterConfig( bus: SAudioBus, target: SGroup, addAction: AddAction )

   private def makeAudioBusMeter( server: SServer, name: String, configs: Seq[ AudioBusMeterConfig ]) : Frame = {
      val chans: Set[ Int ] = configs.map( _.bus.numChannels )( breakOut )
      val synthDefs: Map[ Int, SSynthDef ] = chans.map({ numChannels =>
         import synth._
         import ugen._
         val d = SSynthDef( "$swing_meter" + numChannels ) {
            val sig     = In.ar( "bus".ir, numChannels )
            val tr      = Impulse.kr( 20 )
            val peak    = Peak.kr( sig, tr )
            val rms     = A2K.kr( Lag.ar( sig.squared, 0.1 ))
            SendReply.kr( tr, Flatten( Zip( peak, rms )), "/$meter" )
         }
         numChannels -> d
      })( breakOut )

      var newMsgs    = Map.empty[ Int, List[ osc.Message ]]
      var resps      = List.empty[ sosc.Responder ]
      var synths     = List.empty[ SSynth ]
      var meters     = Vector.empty[ PeakMeter ]
      var wasClosed  = false

      configs.foreach { cfg =>
         import cfg._
         val numChannels      = bus.numChannels
         val synth            = new SSynth( target.server )
         val d                = synthDefs( numChannels )
		   val newMsg           = synth.newMsg( d.name, target, Seq( "bus" -> bus.index ), addAction )
         val meter            = new PeakMeter
         meter.numChannels    = numChannels
         meter.hasCaption     = true
         meter.borderVisible  = true

         val resp             = sosc.Responder.add( server ) {
            case Message( "/$meter", synth.id, _, vals @ _* ) =>
               val pairs   = vals.asInstanceOf[ Seq[ Float ]].toIndexedSeq
               val time    = System.currentTimeMillis()
               Swing.onEDT( meter.update( pairs, 0, time ))
         }

         synth.onGo { Swing.onEDT {
            if( wasClosed ) {
               import Ops._
               synth.free()
            } else {
               synths ::= synth
            }
         }}

         newMsgs += numChannels -> (newMsg :: newMsgs.getOrElse( numChannels, d.freeMsg :: Nil ))
         resps  ::= resp
//         synths ::= synth
         meters :+= meter
      }

      val recvMsgs: List[ osc.Message ] = synthDefs.map({ case (numChannels, d) =>
         d.recvMsg( completion = Some( osc.Bundle.now( newMsgs( numChannels ): _* )))
      })( breakOut )

      server ! (recvMsgs match {
         case single :: Nil => single
         case _ => osc.Bundle.now( recvMsgs: _* )
      })

      val f = new Frame {
         peer.getRootPane.putClientProperty( "Window.style", "small" )
         title = "Meter (" + name + ")"
         contents = new BoxPanel( Orientation.Horizontal ) {
            contents ++= meters
         }
         pack().centerOnScreen()

         override def toString = "MeterFrame@" + hashCode().toHexString

         override def closeOperation() {
            resps.foreach( _.remove() )
            meters.foreach( _.dispose() )
            wasClosed      = true
            val freeMsgs   = synths.map( _.freeMsg )
            this.dispose()
            freeMsgs match {
               case single :: Nil => server ! single
               case Nil =>
               case _ => server ! osc.Bundle.now( freeMsgs: _* )
            }
         }
         visible = true
      }
      f
   }

   final class Server private[swing] ( val server: SServer ) {
      def tree() { new Group( server.rootNode ).tree() }

      def meter() : Frame = {
         val opt        = server.config
         val numInputs  = opt.inputBusChannels
         val numOutputs = opt.outputBusChannels
         val target     = server.rootNode
         val inBus      = SAudioBus( server, index = numOutputs, numChannels = numInputs )
         val outBus     = SAudioBus( server, index = 0,          numChannels = numOutputs )
         val inCfg      = AudioBusMeterConfig( inBus,  target, addToHead )
         val outCfg     = AudioBusMeterConfig( outBus, target, addToTail )
         makeAudioBusMeter( server, server.toString(), inCfg :: outCfg :: Nil )
      }
   }
}