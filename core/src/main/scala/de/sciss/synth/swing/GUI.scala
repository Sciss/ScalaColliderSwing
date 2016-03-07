/*
 *  GUI.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth
package swing

import de.sciss.synth.{GraphFunction => SGraphFunction, Group => SGroup, Server => SServer, Node => SNode, AudioBus => SAudioBus}
import scala.swing.Frame
import collection.immutable.{Seq => ISeq}

object GUI {
  var windowOnTop = false

  private def configure(f: Frame): f.type = {
    if (windowOnTop) f.peer.setAlwaysOnTop(true)
    f
  }

  final class Factory[A] private[swing] (target: => A) {
    def gui: A = target
  }

  final class Group private[swing](val group: SGroup) {
    def tree() : Frame = {
      val ntp                       = new NodeTreePanel()
      ntp.nodeActionMenu            = true
      ntp.confirmDestructiveActions = true
      ntp.group                     = Some(group)
      val ntpw                      = ntp.makeWindow()
      configure(ntpw)
      ntpw.open()
      ntpw
    }
  }

  final class AudioBus private[swing](val bus: SAudioBus) {
    def meter(target: SGroup = bus.server.rootNode, addAction: AddAction = addToTail): Frame =
      makeAudioBusMeter(bus.toString, AudioBusMeter.Strip(bus, target, addAction) :: Nil)

    def waveform(duration: Double = 0.1, target: SGroup = bus.server.rootNode, addAction: AddAction = addToTail): Frame = {
      val data = new GraphFunctionData(target = target, fadeTime = None, outBus = 0, addAction = addAction,
        args = ("$inbus" -> bus.index) :: Nil, thunk = {
          import ugen._
          In.ar("$inbus".ir, bus.numChannels)
        })
      val w = impl.WaveformViewImpl(data, duration = duration)
      configure(w)
    }
  }

  final class GraphFunctionData[A] private[swing](val target: SNode, val outBus: Int, val fadeTime: Option[Double],
                                                  val addAction: AddAction,
                                                  val args: Seq[ControlSet], thunk: => A)
                                                 (implicit result: SGraphFunction.Result.In[A]) {

    def apply(): GE = result.view(thunk)
  }

  final class GraphFunction[A] private[swing](data: GraphFunctionData[A]) {
    def waveform(duration: Double = 0.1): Frame = {
      val w = impl.WaveformViewImpl(data, duration = duration)
      configure(w)
    }
  }

  private def makeAudioBusMeter(name: String, strips: ISeq[AudioBusMeter.Strip]): Frame = {
    val meter = AudioBusMeter(strips)
    val w     = makeFrame(s"Meter ($name)", "MeterFrame", meter.component)(meter.dispose())
    configure(w)
    w
  }

  private[swing] def makeFrame(name: String, string: String, component: scala.swing.Component, smallBar: Boolean = true)
                       (onClose: => Unit): Frame = {
    new Frame {
      if (smallBar) peer.getRootPane.putClientProperty("Window.style", "small")
      title = name
      contents = component
      //         new BoxPanel( Orientation.Horizontal ) {
      //            contents ++= meters
      //         }
      pack().centerOnScreen()
      visible = true

      override def toString() = s"$string@${hashCode().toHexString}"

      override def closeOperation(): Unit = {
        onClose
        this.dispose()
      }
    }
  }

  final class Server private[swing](val server: SServer) {
    def tree(): Frame = {
      val w = new Group(server.rootNode).tree()
      configure(w)
    }

    def meter(): Frame = {
      val opt         = server.config
      val numInputs   = opt.inputBusChannels
      val numOutputs  = opt.outputBusChannels
      val target      = server.rootNode
      val inBus       = SAudioBus(server, index = numOutputs, numChannels = numInputs )
      val outBus      = SAudioBus(server, index = 0         , numChannels = numOutputs)
      val inCfg       = AudioBusMeter.Strip(inBus , target, addToHead)
      val outCfg      = AudioBusMeter.Strip(outBus, target, addToTail)
      makeAudioBusMeter(server.toString(), inCfg :: outCfg :: Nil)
    }
  }
}
