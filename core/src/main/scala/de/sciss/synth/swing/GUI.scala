/*
 *  GUI.scala
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

import java.awt.{Color, FileDialog, RenderingHints}
import java.io.ByteArrayInputStream

import at.iem.scalacollider.ScalaColliderDOT
import de.sciss.file._
import de.sciss.synth.Ops.stringToControl
import de.sciss.synth.swing.impl.WaveformViewImpl
import de.sciss.synth.swing.j.JScopePanel
import de.sciss.synth.{AudioBus => SAudioBus, GraphFunction => SGraphFunction, Group => SGroup, Node => SNode, Server => SServer, SynthDef => SSynthDef}
import javax.imageio.ImageIO

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.{BorderPanel, Button, Component, Dimension, FlowPanel, Frame, Graphics2D, ScrollPane}

object GUI {
  var windowOnTop = false

  private def configure(f: Frame): f.type = {
    if (windowOnTop) f.peer.setAlwaysOnTop(true)
    f
  }

  final class Factory[A] private[swing] (target: => A) {
    def gui: A = target
  }

  final class Group private[swing](group: SGroup) {
    def tree(): Frame = {
      val ntp                       = new NodeTreePanel()
      ntp.nodeActionMenu            = true
      ntp.confirmDestructiveActions = true
      ntp.group                     = Some(group)
      val w                         = ntp.makeWindow()
      configure(w)
      w.open()
      w
    }
  }

  final class AudioBus private[swing](bus: SAudioBus) {
    def meter(target: SGroup = bus.server.rootNode, addAction: AddAction = addToTail): Frame =
      makeAudioBusMeter(bus.toString, AudioBusMeter.Strip(bus, target, addAction) :: Nil)

    def waveform(duration: Double = 0.1, target: SGroup = bus.server.rootNode, addAction: AddAction = addToTail): Frame = {
      val data = new GraphFunctionData(target = target, fadeTime = -1, addAction = addAction,
        args = ("$inbus" -> bus.index) :: Nil, fun = { () =>
          import ugen._
          In.ar("$inbus".ir, bus.numChannels)
        })
      val w = WaveformViewImpl(data, duration = duration)
      configure(w)
    }

    def scope(style: Int = 0, bufSize: Int = 0, zoom: Double = 1.0, target: SGroup = bus.server.rootNode,
              addAction: AddAction = addToTail): Frame = {
      val s = bus.server
      require (s == target.server)
      val p         = new JScopePanel
      p.style       = style
      p.yZoom       = zoom.toFloat
      p.target      = target
      p.addAction   = addAction
      p.bufferSize  = bufSize
      p.bus         = bus
      p.start()

      val w = makeFrame(s"Oscilloscope", "Oscilloscope", Component.wrap(p)) {
        p.dispose()
      }
      configure(w)
    }
  }

  final class SynthDef private[swing](sd: SSynthDef) {
    def diagram(): Frame = {
      val config        = ScalaColliderDOT.Config()
      config.rateColors = true
      config.graphName  = sd.name
      config.input      = sd.graph

      def saveImage(f: File, dpi: Int): Unit = {
        import scala.sys.process._
        val dot   = ScalaColliderDOT(config)
        val dotIn = new ByteArrayInputStream(dot.getBytes("UTF-8"))
        val isPNG = f.extL == "png"
        val opts  = if (isPNG) "-Tpng" :: s"-Gdpi=$dpi" :: Nil else "-Tsvg" :: Nil
        // cf. https://stackoverflow.com/questions/1286813/
        val res   = ("dot" :: opts).#<(dotIn).#>(f).!
        if (res != 0) sys.error(s"'dot' failed with code $res")
      }

      val seq = List(36, 72, 144).map { dpi =>
        val f = File.createTemp("temp", ".png")
        f.deleteOnExit()
        saveImage(f, dpi = dpi)
        f -> ImageIO.read(f)
      }
      val (tmpF, img1 :: img2 :: img3 :: Nil) = seq.unzip

      var scale = 0.5
      val imgW  = img3.getWidth
      val imgH  = img3.getHeight

      val canvas = new Component {
        override protected def paintComponent(g: Graphics2D): Unit = {
          g.setColor(Color.white)
          g.fillRect(0, 0, peer.getWidth, peer.getHeight)
          g.setRenderingHint(RenderingHints.KEY_INTERPOLATION , RenderingHints.VALUE_INTERPOLATION_BICUBIC)
          g.setRenderingHint(RenderingHints.KEY_RENDERING     , RenderingHints.VALUE_RENDER_QUALITY)
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING  , RenderingHints.VALUE_ANTIALIAS_ON)
//          g.drawImage(img, atImg, peer)

          val atOrig = g.getTransform
          if (scale < 0.25) {
            g.scale(scale * 4, scale * 4)
            g.drawImage(img1, 0, 0, peer)
          } else if (scale < 0.5) {
            g.scale(scale * 2, scale * 2)
            g.drawImage(img2, 0, 0, peer)
          } else {
            g.scale(scale * 1, scale * 1)
            g.drawImage(img3, 0, 0, peer)
          }
          g.setTransform(atOrig)
        }
      }

      def setScale(f: Double): Unit = {
        scale = f
        val w = (imgW * f).ceil.toInt
        val h = (imgH * f).ceil.toInt
        canvas.preferredSize = new Dimension(w, h)
        canvas.revalidate()
        canvas.repaint()
      }

      def adaptScale(f: Double): Unit = {
        val sx = (scale * f).clip(1.0 / 64, 2.0)
        setScale(sx)
      }

      setScale(0.5)

      val ggZoomIn = Button("+") {
        adaptScale(2.0.sqrt)
      }
      val ggZoomOut = Button("\u2013") {
        adaptScale(0.5.sqrt)
      }
      val scroll  = new ScrollPane(canvas)
      scroll.peer.putClientProperty("styleId", "undecorated")
//      val lbInfo  = new Label("Shift-Drag = Pan, Ctrl-Drag = Zoom, Ctrl-T = Reset")
      lazy val ggSave: Button = Button("Save…") {
        val dlg = new FileDialog(f.peer, "Save Diagram as SVG or PNG", FileDialog.SAVE)
        dlg.setFile("ugen-graph.svg")
        dlg.setVisible(true)
        for {
          dir  <- Option(dlg.getDirectory)
          name <- Option(dlg.getFile)
        } {
          val fOut = new File(dir, name)
          saveImage(fOut, dpi = 200)
        }
      }
      lazy val panel   = new BorderPanel {
        add(scroll, BorderPanel.Position.Center)
        add(new FlowPanel(ggSave, ggZoomIn, ggZoomOut /* lbInfo */), BorderPanel.Position.South)
      }
      // svgCanvas.loadSVGDocument(svgOut.toURI.toURL.toExternalForm)
      lazy val f = makeFrame(sd.name, s"SynthDef($sd.name)(...).gui.diagram", panel)(tmpF.foreach(_.delete()))
      f
    }
  }

  final class GraphFunctionData private[swing](val target: SNode, val fadeTime: Double,
                                               val addAction: AddAction,
                                               val args: Seq[ControlSet], fun: () => GE) {

    def apply(): GE = fun()
  }

  final class GraphFunction[A] private[swing](fun: SGraphFunction[A]) {
    def waveform(duration: Double = 0.1, target: Node = Server.default.defaultGroup,
                 fadeTime: Double = 0.02, addAction: AddAction = addToHead, args: Seq[ControlSet] = Nil): Frame = {
      val resIn: () => GE = fun.result match {
        case SGraphFunction.Result.In(view) => () => view(fun.peer())
        case _                              => () => ugen.DC.ar(0)    // XXX TODO --- not cool
      }
      val data = new GraphFunctionData(target = target, fadeTime = fadeTime, addAction = addAction,
        args = args, fun = resIn)
      val w = WaveformViewImpl(data, duration = duration)
      configure(w)
    }

    def diagram(fadeTime: Double = 0.02): Frame =
      new SynthDef(SGraphFunction.mkSynthDef(fun, fadeTime)).diagram()
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

  final class Server private[swing](server: SServer) {
    def tree(): Frame =
      new Group(server.rootNode).tree()

    def scope(): Frame =
      new AudioBus(SAudioBus(server, 0, server.config.outputBusChannels)).scope()

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
