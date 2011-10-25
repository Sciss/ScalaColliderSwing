package de.sciss.synth.swing

import sys.error
import de.sciss.gui.PeakMeter
import de.sciss.osc.Message
import de.sciss.synth.{addToHead, addToTail, AudioBus, Group => SGroup, Server => SServer}
import swing.{Swing, BoxPanel, Orientation, Frame}

object GUI {
   class Factory[ T ] private[swing] ( target: => T ) { def gui: T = target }

   class Group private[swing] ( val group: SGroup ) {
      def tree { error( "TODO" )}
   }

   class Server private[swing] ( val server: SServer ) {
      def tree { new Group( server.rootNode ).tree }
      def meter : Frame = {
         val name       = server.name
         val opt        = server.options
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
               contents ++= sections.map( _._1 )
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