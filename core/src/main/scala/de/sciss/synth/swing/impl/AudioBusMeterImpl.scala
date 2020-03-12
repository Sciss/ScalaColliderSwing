/*
 *  AudioBusMeterImpl.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth
package swing
package impl

import java.awt.EventQueue

import de.sciss.audiowidgets.PeakMeter
import de.sciss.osc.Message
import de.sciss.synth.Ops.stringToControl
import de.sciss.{osc, synth}

import scala.collection.immutable.{IndexedSeq => Vec, Seq => ISeq}
import scala.swing.{BoxPanel, Component, Orientation, Swing}

class AudioBusMeterImpl(val strips: ISeq[AudioBusMeter.Strip]) extends AudioBusMeter {
  private var wasClosed = false

  private var synths  = List.empty[Synth]
  private var resps   = List.empty[message.Responder]
  private var meters  = Vec .empty[PeakMeter]

  def dispose(): Unit = {
    require(EventQueue.isDispatchThread, "dispose() must be called on the event dispatch thread")
    if (wasClosed) return

    resps .foreach(_.remove ())
    meters.foreach(_.dispose())
    wasClosed = true
    var freeMsgs = Map.empty[Server, List[osc.Message]] withDefaultValue Nil
    // group free messages by server
    synths.foreach { synth =>
      val server  = synth.server
      freeMsgs    = freeMsgs.updated(server, synth.freeMsg :: freeMsgs(server))
    }
    // send them out per server, ignoring errors of nodes having already disappeared
    freeMsgs.foreach {
      case (server, msgs) => server ! osc.Bundle.now(osc.Message("/error", -1) +: msgs: _*)
    }
  }

  // group to send out bundles per server
  strips.groupBy(_.bus.server).foreach { case (server, strips1) =>
    var recvMsgs = List.empty[osc.Message]

    // group to send out synth defs per channel num
    strips1.groupBy(_.bus.numChannels).foreach { case (numChannels, strips2) =>
      import synth._
      import ugen._
      val d = SynthDef("$swing_meter" + numChannels) {
        val sig   = In.ar("bus".ir, numChannels)
        val tr    = Impulse.kr(20)
        val peak  = Peak.kr(sig, tr)
        val rms   = A2K.kr(Lag.ar(sig.squared, 0.1))
        SendReply.kr(tr, Flatten(Zip(peak, rms)), "/$meter")
      }

      var compMsgs: List[osc.Message] = d.freeMsg :: Nil

      strips2.foreach { strip =>
        import strip._

        val meter           = new PeakMeter
        meter.numChannels   = numChannels
        meter.caption       = true
        meter.borderVisible = true
        meters :+= meter

        val synth   = Synth(target.server)
        val newMsg  = synth.newMsg(d.name, target, Seq("bus" -> bus.index), addAction)
        compMsgs ::= newMsg

        val resp    = message.Responder.add(synth.server) {
          case Message("/$meter", synth.id, _, vals @ _*) =>
            val pairs = vals.asInstanceOf[Seq[Float]].toIndexedSeq
            val time  = System.currentTimeMillis()
            Swing.onEDT(meter.update(pairs, 0, time))
        }
        resps  ::= resp

        synth.onGo {
          Swing.onEDT {
            if (wasClosed) {
              import Ops._
              synth.free()
            } else {
              synths ::= synth
            }
          }
        }
      }

      val recvMsg = d.recvMsg(completion = osc.Bundle.now(compMsgs: _*))

      recvMsgs ::= recvMsg
    }

    server ! (recvMsgs match {
      case single :: Nil  => single
      case _              => osc.Bundle.now(recvMsgs: _*)
    })
  }

  val component: Component = new BoxPanel(Orientation.Horizontal) {
    contents ++= meters
  }
}
