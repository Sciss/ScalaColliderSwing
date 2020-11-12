/*
 *  WaveformViewImpl.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth
package swing
package impl

import java.awt.event.MouseEvent
import java.awt.{Color, Dimension, Font, Graphics, Graphics2D, Point, RenderingHints}
import java.io.File

import de.sciss.audiofile.AudioFile
import javax.swing.JComponent
import javax.swing.event.MouseInputAdapter
import de.sciss.audiowidgets.j.WavePainter
import de.sciss.audiowidgets.{Axis, AxisFormat}
import de.sciss.synth.Ops.stringToControl
import de.sciss.{osc, synth}

import scala.swing.{BorderPanel, BoxPanel, Component, Frame, Orientation, Swing}
import scala.util.Failure

object WaveformViewImpl {
  private final case class GUIRecordOut(in: GE)(chanFun: Int => Unit)
    extends UGenSource.ZeroOut with WritesBus {

    import UGenSource._
    // XXX TODO should not be UGenSource

    protected def makeUGens: Unit = unwrap(this, in.expand.outputs)

    protected def makeUGen(ins: Vec[UGenIn]): Unit = {
      if (ins.isEmpty) return

      import ugen._
      val rate = ins.map(_.rate).max
      val signal: GE = if (rate == audio) ins else K2A.ar(ins)
      val bufKey: String = "$buf" // bug in Scala 2.12 - need to specify return type
      val buf = bufKey.ir
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

  def apply(data: GUI.GraphFunctionData, duration: Double): Frame = {
    import data._

    val server = target.server

    require(server.isLocal, "Currently requires that Server is local")

    var numCh = 0
    val sg = SynthGraph {
     val signal  = data()
      GUIRecordOut(signal)(numCh = _)
    }
    val ug          = sg.expand(synth.impl.DefaultUGenGraphBuilderFactory)
    val defName     = "$swing_waveform" + numCh
    val sd          = SynthDef(defName, ug)
    val syn         = Synth(server)
    val sr          = server.sampleRate
    val numFr       = math.ceil(duration * sr).toInt

    val buf         = Buffer(server)
    val myArgs: List[ControlSet] = List("$buf" -> buf.id, "$dur" -> duration)
    val synthMsg    = syn.newMsg(defName, target, myArgs ++ args, addAction)
    val defFreeMsg  = sd.freeMsg
    val compl       = osc.Bundle.now(synthMsg, defFreeMsg)
    val recvMsg     = sd.recvMsg(buf.allocMsg(numFr, numCh, compl))

    import WavePainter.MultiResolution

    val path        = File.createTempFile("scalacollider", ".aif")

    val fontWait    = new Font(Font.SANS_SERIF, Font.PLAIN, 24)
    var paintFun: Graphics2D => Unit = { g =>
      g.setFont(fontWait)
      g.setColor(Color.white)
      g.drawString("\u231B ...", 10, 26) // u231A = 'watch', u231B = 'hourglass'
    }

    lazy val ggWave = new JComponent {
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

    val ggAxisH     = new Axis(Orientation.Horizontal)
    ggAxisH.format  = AxisFormat.Integer
    val ggAxisV     = new Axis(Orientation.Vertical  )
    ggAxisV.format  = AxisFormat.Integer
    // ggAxisV.minimum = -1
    // ggAxisV.maximum = 1
    // ggAxisV.fixedBounds = true

    val box = new BorderPanel {
      add(Component.wrap(ggWave), BorderPanel.Position.Center)
      add(new BoxPanel(Orientation.Horizontal) {
        contents += Swing.HStrut(ggAxisV.preferredSize.width)
        contents += ggAxisH
      }, BorderPanel.Position.North)
      add(ggAxisV, BorderPanel.Position.West )
    }

    // val box = scala.swing.Component.wrap(ggWave)
    val f   = GUI.makeFrame("Plot", "PlotFrame", box /* , smallBar = false */) {
      path.delete()
      ()
    }

    def openBuffer(): Unit = {
      val af = AudioFile.openRead(path)
      try {
        val num   = math.min(numFr, af.numFrames).toInt
        val data  = Array.ofDim[Float](numCh, num)
        af.read(data, 0, num)
        af.close()
        //println( "... read " + num + " frames from " + path.getAbsolutePath )
        val pntSrc  = MultiResolution.Source.wrap(data)
        val display = new WavePainter.Display {
          def numChannels : Int   = numCh
          def numFrames   : Long  = numFr

          def refreshAllChannels(): Unit = ggWave.repaint()

          def channelDimension(result: Dimension): Unit = {
            result.width  = ggWave.getWidth
            val h         = ggWave.getHeight
            result.height = (h - ((numCh - 1) * 4)) / numCh
          }

          def channelLocation(ch: Int, result: Point): Unit = {
            result.x        = 0
            val h           = ggWave.getHeight
            val viewHeight  = (h - ((numCh - 1) * 4)) / numCh
            val trackHeight = viewHeight + 4
            result.y        = trackHeight * ch
          }
        }

        val painter = MultiResolution(pntSrc, display)

        val zoom: WavePainter.HasZoom = new WavePainter.HasZoom {
          def startFrame: Long = painter.startFrame
          def startFrame_=(value: Long): Unit = {
            painter.startFrame  = value
            // println(s"startFrame = $value")
            ggAxisH.minimum     = value.toDouble / sr
          }

          def stopFrame: Long = painter.stopFrame
          def stopFrame_=(value: Long): Unit = {
            painter.stopFrame  = value
            // println(s"stopFrame = $value")
            ggAxisH.maximum    = value.toDouble / sr
          }

          def magLow: Double = painter.magLow
          def magLow_=(value: Double): Unit = {
            painter.magLow  = value
            // println(s"magLow = $value")
            ggAxisV.minimum = value * 100
          }

          def magHigh: Double = painter.magHigh
          def magHigh_=(value: Double): Unit = {
            painter.magHigh = value
            // println(s"magHigh = $value")
            ggAxisV.maximum = value * 100
          }
        }

        zoom.startFrame     = 0L
        zoom.stopFrame      = numFr
        zoom.magLow         = -1d
        zoom.magHigh        = 1d
        painter.peakColor   = Color.gray
        painter.rmsColor    = Color.white
        paintFun            = painter.paint
        WavePainter.HasZoom.defaultKeyActions(zoom, display).foreach(_.install(ggWave))
        ggWave.addMouseWheelListener(WavePainter.HasZoom.defaultMouseWheelAction(zoom, display))
        ggWave.repaint()
        ggWave.requestFocus()

        val mia: MouseInputAdapter = new MouseInputAdapter {
          private def frame(e: MouseEvent): Long = {
            val w    = ggWave.getWidth
            val clip = e.getX.clip(0, w)
            // val f = clip.linLin(0, w, zoom.startFrame, zoom.stopFrame)
            val f = clip.linLin(0.0, w, viewStart.toDouble, (viewStart + viewSpan).toDouble)
            (f + 0.5).toLong
          }

          private var dragStart = 0L
          private var viewStart = 0L
          private var viewSpan  = 0L

          override def mousePressed(e: MouseEvent): Unit = {
            viewStart = zoom.startFrame
            viewSpan  = zoom.stopFrame - viewStart
            dragStart = frame(e)
          }

          override def mouseDragged(e: MouseEvent): Unit = {
            val dragStop    = frame(e)
            val delta       = dragStart - dragStop
            // println(delta)
            val newStart    = math.max(0L, math.min(numFr - viewSpan, viewStart + delta))
            zoom.startFrame = newStart
            zoom.stopFrame  = newStart + viewSpan
            ggWave.repaint()
          }
        }
        ggWave.addMouseListener      (mia)
        ggWave.addMouseMotionListener(mia)
        ggWave.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR))

      } finally {
        if (af.isOpen) af.close()
      }
    }

    syn.onEnd {
      // println(s"----onEnd...")
      val syncMsg   = server.syncMsg()
      val syncReply = syncMsg.reply
      // println(s"----onEnd $syncId")
      val writeMsg  = buf.writeMsg(path.getAbsolutePath, completion = osc.Bundle.now(buf.freeMsg, syncMsg))
      val fut       = server.!!(writeMsg) {
        case `syncReply` =>
          // println("openBuffer")
          openBuffer()
      }
      val c = server.clientConfig
      import c.executionContext
      fut.onComplete {
        case Failure(message.Timeout()) => println("Timeout!")
        case _ =>
      }
      // println("----aqui")
    }

    // println(s"----waiting for $syn")
    // server.dumpOSC()
    server ! recvMsg // osc.Bundle.now( recvMsg, allocMsg )

    f
  }
}