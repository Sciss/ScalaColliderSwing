/*
 *  GUI.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth
package swing

import java.awt.FileDialog
import java.io.{ByteArrayInputStream, File}
import javax.swing.JButton

import at.iem.scalacollider.ScalaColliderDOT
import de.sciss.synth.Ops.stringToControl
import de.sciss.synth.{AudioBus => SAudioBus, GraphFunction => SGraphFunction, Group => SGroup, Node => SNode, Server => SServer, SynthDef => SSynthDef}
import org.apache.batik.swing.JSVGCanvas

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.{BorderPanel, Button, Component, FlowPanel, Frame, Label, ScrollPane}

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

  final class AudioBus private[swing](bus: SAudioBus) {
    def meter(target: SGroup = bus.server.rootNode, addAction: AddAction = addToTail): Frame =
      makeAudioBusMeter(bus.toString, AudioBusMeter.Strip(bus, target, addAction) :: Nil)

    def waveform(duration: Double = 0.1, target: SGroup = bus.server.rootNode, addAction: AddAction = addToTail): Frame = {
      val data = new GraphFunctionData(target = target, fadeTime = -1, addAction = addAction,
        args = ("$inbus" -> bus.index) :: Nil, fun = { () =>
          import ugen._
          In.ar("$inbus".ir, bus.numChannels)
        })
      val w = impl.WaveformViewImpl(data, duration = duration)
      configure(w)
    }
  }

  final class SynthDef private[swing](sd: SSynthDef) {
    def diagram(): Frame = {
      val config        = ScalaColliderDOT.Config()
      config.rateColors = true
      config.graphName  = sd.name
      config.input      = sd.graph

      def saveSVG(f: File): Unit = {
        import scala.sys.process._
        val dot   = ScalaColliderDOT(config)
        val dotIn = new ByteArrayInputStream(dot.getBytes("UTF-8"))
        val res   = Seq("dot", "-Tsvg").#<(dotIn).#>(f).!
        if (res != 0) sys.error(s"'dot' failed with code $res")
      }

      val svgOut = File.createTempFile("temp", "svg")
      svgOut.deleteOnExit()
      saveSVG(svgOut)

      // XXX TODO --- zoom http://stackoverflow.com/questions/10838971
      // XXX TODO --- font quality: http://stackoverflow.com/questions/32272822/
      val svgCanvas     = new JSVGCanvas(null, true, false)
      val zoomIn  = new svgCanvas.ZoomAction(math.sqrt(2.0))
      val ggZoomIn  = new JButton(zoomIn)
      ggZoomIn.setText("+")
      val zoomOut = new svgCanvas.ZoomAction(math.sqrt(0.5))
      val ggZoomOut = new JButton(zoomOut)
      ggZoomOut.setText("\u2013")
      val scroll  = new ScrollPane(Component.wrap(svgCanvas))
      scroll.peer.putClientProperty("styleId", "undecorated")
      val lbInfo  = new Label("Shift-Drag = Pan, Ctrl-Drag = Zoom, Ctrl-T = Reset")
      lazy val ggSave: Button = Button("Save…") {
        val dlg = new FileDialog(f.peer, "Save Diagram as SVG", FileDialog.SAVE)
        dlg.setFile("ugen-graph.svg")
        dlg.setVisible(true)
        for {
          dir  <- Option(dlg.getDirectory)
          name <- Option(dlg.getFile)
        } {
          val fOut = new File(dir, name)
          saveSVG(fOut)
        }
      }
      lazy val panel   = new BorderPanel {
        add(scroll, BorderPanel.Position.Center)
        add(new FlowPanel(ggSave, Component.wrap(ggZoomIn), Component.wrap(ggZoomOut), lbInfo), BorderPanel.Position.South)
      }
      svgCanvas.loadSVGDocument(svgOut.toURI.toURL.toExternalForm)
      lazy val f = makeFrame(sd.name, s"SynthDef($sd.name)(...).gui.diagram", panel)(svgOut.delete())
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
      val w = impl.WaveformViewImpl(data, duration = duration)
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
