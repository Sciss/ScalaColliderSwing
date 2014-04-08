/*
 *  GUI.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth
package swing

import de.sciss.synth.{GraphFunction => SGraphFunction, Group => SGroup, Server => SServer, Node => SNode,
   AudioBus => SAudioBus, Synth => SSynth, message, SynthGraph => SSynthGraph}
import scala.swing.Frame
import de.sciss.{synth, osc}
import collection.immutable.{IndexedSeq => Vec, Seq => ISeq}
import java.io.File
import de.sciss.audiowidgets.j.WavePainter
import javax.swing.JComponent
import java.awt.{Font, Point, RenderingHints, Color, Dimension, Graphics2D, Graphics}

object GUI {
  final class Factory[T] private[swing] (target: => T) {
    def gui: T = target
  }

  final class Group private[swing](val group: SGroup) {
    def tree() : Frame = {
      val ntp                       = new NodeTreePanel()
      ntp.nodeActionMenu            = true
      ntp.confirmDestructiveActions = true
      ntp.group                     = Some(group)
      val ntpw                      = ntp.makeWindow()
      ntpw.open()
      ntpw
    }
  }

  final class AudioBus private[swing](val bus: SAudioBus) {
    def meter(target: SGroup = bus.server.rootNode, addAction: AddAction = addToTail): Frame =
      makeAudioBusMeter(bus.toString, AudioBusMeter.Strip(bus, target, addAction) :: Nil)

    def waveform(duration: Double = 0.1, target: SGroup = bus.server.rootNode, addAction: AddAction = addToTail): Frame = {
      val gf = new GraphFunction(target = target, fadeTime = None, outBus = 0, addAction = addAction,
        args = ("$inbus" -> bus.index) :: Nil, thunk = {
          import ugen._
          In.ar("$inbus".ir, bus.numChannels)
        })
      gf.waveform(duration)
    }
  }

  private final case class GUIRecordOut(in: GE)(chanFun: Int => Unit)
    extends UGenSource.ZeroOut with WritesBus {
    // XXX TODO should not be UGenSource

    protected def makeUGens {
      unwrap(in.expand.outputs)
    }

    protected def makeUGen(ins: Vec[UGenIn]): Unit = {
      if (ins.isEmpty) return

      import synth._
      import ugen._
      val rate = ins.map(_.rate).max
      val signal: GE = if (rate == audio) ins else K2A.ar(ins)
      val buf = "$buf".ir
      //         val dur        = "$dur".ir
      //         val out        = Out.ar( bus, signal )

      // XXX RecordBuf doneAction is broken for multi-channel signals
      /* val out = */ RecordBuf.ar(signal, buf, loop = 0 /* , doneAction = freeSelf */)
      //         val out = DiskOut.ar( buf, signal )
      /* val line = */ Line.kr(0, 0, dur = "$dur".ir, doneAction = freeSelf)
      chanFun(ins.size)
      //         out.expand
      //         line.expand
    }
  }

  final class GraphFunction[T] private[swing](target: SNode, outBus: Int, fadeTime: Option[Double],
                                              addAction: AddAction,
                                              args: Seq[ControlSetMap], thunk: => T)
                                             (implicit result: SGraphFunction.Result.In[T]) {
    def waveform(duration: Double = 0.1): Frame = {
      val server = target.server

      require(server.isLocal, "Currently requires that Server is local")

      var numCh = 0
      val sg = SSynthGraph {
        val r       = thunk
        val signal  = result.view(r)
        GUIRecordOut(signal)(numCh = _)
      }
      val ug      = sg.expand(synth.impl.DefaultUGenGraphBuilderFactory)
      val defName = "$swing_waveform" + numCh
      val sd      = SynthDef(defName, ug)
      val syn     = SSynth(server)
      val sr      = server.sampleRate
      val numFr   = math.ceil(duration * sr).toInt

      //         def roundUp( i: Int ) = { val j = i + 32768 - 1; j - j % 32768 }
      //
      //         val numFramesC = roundUp( numFrames )
      //         val durC       = numFramesC / server.sampleRate
      val buf         = Buffer(server)
      val myArgs: List[ControlSetMap] = List("$buf" -> buf.id, "$dur" -> duration)
      val synthMsg    = syn.newMsg(defName, target, myArgs ++ args, addAction)
      val defFreeMsg  = sd.freeMsg
      val compl       = osc.Bundle.now(synthMsg, defFreeMsg)
      val recvMsg     = sd.recvMsg(buf.allocMsg(numFr, numCh, compl))
      //         val allocMsg   = buf.allocMsg( numFrames, numChannels,
      //            completion = buf.writeMsg( path, numFrames = 0, leaveOpen = true, completion = compl ))

      import WavePainter.MultiResolution

      val path        = File.createTempFile("scalacollider", ".aif")

      val fontWait    = new Font(Font.SANS_SERIF, Font.PLAIN, 24)
      var paintFun: Graphics2D => Unit = { g =>
        g.setFont(fontWait)
        g.setColor(Color.white)
        g.drawString("\u231B ...", 10, 26) // u231A = 'watch', u231B = 'hourglass'
      }

      lazy val component = new JComponent {
        setFocusable(true)
        setPreferredSize(new Dimension(400, 400))

        override def paintComponent(g: Graphics): Unit = {
          val g2 = g.asInstanceOf[Graphics2D]
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING  , RenderingHints.VALUE_ANTIALIAS_ON)
          g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE )
          g2.setColor(Color.black)
          g2.fillRect(0, 0, getWidth, getHeight)
          paintFun(g2) // painter.paint( g2 )
        }
      }

      val box = scala.swing.Component.wrap(component)
      val f   = makeFrame("Plot", "PlotFrame", box /* , smallBar = false */) {
        path.delete()
      }

      def openBuffer(): Unit = {
        val af = io.AudioFile.openRead(path)
        try {
          val num   = math.min(numFr, af.numFrames).toInt
          val data  = Array.ofDim[Float](numCh, num)
          af.read(data, 0, num)
          af.close()
          //println( "... read " + num + " frames from " + path.getAbsolutePath )
          val pntSrc  = MultiResolution.Source.wrap(data)
          val display = new WavePainter.Display {
            def numChannels = numCh
            def numFrames   = numFr

            def refreshAllChannels(): Unit = component.repaint()

            def channelDimension(result: Dimension): Unit = {
              result.width  = component.getWidth
              val h         = component.getHeight
              result.height = (h - ((numCh - 1) * 4)) / numCh
            }

            def channelLocation(ch: Int, result: Point): Unit = {
              result.x        = 0
              val h           = component.getHeight
              val viewHeight  = (h - ((numCh - 1) * 4)) / numCh
              val trackHeight = viewHeight + 4
              result.y        = trackHeight * ch
            }
          }
          val painter = MultiResolution(pntSrc, display)
          painter.startFrame  = 0L
          painter.stopFrame   = numFr
          painter.magLow      = -1
          painter.magHigh     = 1
          painter.peakColor   = Color.gray
          painter.rmsColor    = Color.white
          paintFun            = painter.paint
          WavePainter.HasZoom.defaultKeyActions(painter, display).foreach(_.install(component))
          component.addMouseWheelListener(WavePainter.HasZoom.defaultMouseWheelAction(painter, display))
          component.repaint()
          component.requestFocus()

        } finally {
          if (af.isOpen) af.close()
        }
      }

      syn.onEnd {
        // println(s"----onEnd...")
        val syncMsg   = server.syncMsg()
        val syncID    = syncMsg.id
        // println(s"----onEnd $syncID")
        val writeMsg  = buf.writeMsg(path.getAbsolutePath, completion = osc.Bundle.now(buf.freeMsg, syncMsg))
        val fut       = server.!!(writeMsg) {
          case message.Synced(`syncID`) =>
            // println("openBuffer")
            openBuffer()
        }
        val c = server.clientConfig
        import c.executionContext
        fut.onFailure {
          case message.Timeout() => println("Timeout!")
        }
        // println("----aqui")
      }

      // println(s"----waiting for $syn")
      // server.dumpOSC()
      server ! recvMsg // osc.Bundle.now( recvMsg, allocMsg )

      f
    }
  }

  private def makeAudioBusMeter(name: String, strips: ISeq[AudioBusMeter.Strip]): Frame = {
    val meter = AudioBusMeter(strips)
    makeFrame(s"Meter ($name)", "MeterFrame", meter.component)(meter.dispose())
  }

  private def makeFrame(name: String, string: String, component: scala.swing.Component, smallBar: Boolean = true)
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
    def tree(): Frame = new Group(server.rootNode).tree()

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