/*
 *  JScopePanel.scala
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

package de.sciss.synth.swing.j

import java.awt.event.{ItemEvent, ItemListener}
import java.awt.{BorderLayout, Color}

import de.sciss.osc
import de.sciss.synth.{AddAction, AudioBus, Buffer, Bus, ControlBus, GraphFunction, Group, Ops, Synth, addToTail, audio}
import javax.swing.event.{ChangeEvent, ChangeListener}
import javax.swing.{Box, BoxLayout, JComboBox, JComponent, JPanel, JSpinner, SpinnerNumberModel}

import scala.math.{max, min}
import scala.swing.Swing
import scala.util.control.NonFatal

class JScopePanel extends JPanel(new BorderLayout(0, 0)) with ScopeViewLike {
  private[this] val view = new JScopeView

  private[this] val ggBusType     = new JComboBox(Array("Audio In", "Audio Out", "Audio Bus", "Control Bus"))
  private[this] val mBusOff       = new SpinnerNumberModel(0, 0, 8192, 1)
  private[this] val mBusNum       = new SpinnerNumberModel(1, 0 /*1*/, 8192, 1)
  private[this] val ggBusOff      = new JSpinner(mBusOff)
  private[this] val ggBusNum      = new JSpinner(mBusNum)
  private[this] val ggStyle       = {
    val res = new JComboBox(Array("Channels", "Overlay", "Lissajous"))
    res.addItemListener(new ItemListener {
      def itemStateChanged(e: ItemEvent): Unit =
        view.style = res.getSelectedIndex
    })
    res
  }

  private def fix(c: JComponent): c.type = {  // WTF
    c.setMaximumSize(c.getPreferredSize)
    c
  }

  private[this] val pTop: JPanel = {
    val p = new JPanel
    p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS))
    p.add(fix(ggBusType))
    p.add(fix(ggBusOff))
    ggBusOff.setToolTipText("Bus Offset")
    p.add(fix(ggBusNum))
    ggBusNum.setToolTipText("No. of Channels")
    p.add(Box.createHorizontalGlue())
    p.add(fix(ggStyle))
    p
  }

  private[this] val lBusOffNum: ChangeListener = new ChangeListener {
    def stateChanged(e: ChangeEvent): Unit =
      setBusFromUI(mBusOff.getNumber.intValue(), mBusNum.getNumber.intValue())
  }

  private[this] val lBusType: ItemListener = new ItemListener {
    def itemStateChanged(e: ItemEvent): Unit = {
      val t = max(0, ggBusType.getSelectedIndex)
      setBusTypeFromUI(t, mBusOff.getNumber.intValue(), mBusNum.getNumber.intValue())
    }
  }

  private def removeBusListeners(): Unit = {
    ggBusOff.removeChangeListener(lBusOffNum)
    ggBusNum.removeChangeListener(lBusOffNum)
    ggBusType.removeItemListener(lBusType)
  }

  private def addBusListeners(): Unit = {
    ggBusOff.addChangeListener(lBusOffNum)
    ggBusNum.addChangeListener(lBusOffNum)
    ggBusType.addItemListener(lBusType)
  }

  private[this] var _bus        : Bus       = null
  private[this] var _target     : Group     = null
  private[this] var _addAction  : AddAction = addToTail
  private[this] var _bufSize    : Int       = 4096

  add(pTop , BorderLayout.NORTH )
  add(view , BorderLayout.CENTER)
  addBusListeners()

  def style: Int = view.style

  def style_=(value: Int): Unit = {
    ggStyle.setSelectedIndex(value)
    view.style = value
  }

  def xZoom: Float = view.xZoom

  def xZoom_=(value: Float): Unit =
    view.xZoom = value

  def yZoom: Float = view.yZoom

  def yZoom_=(value: Float): Unit =
    view.yZoom = value

  def waveColors: Seq[Color] = view.waveColors

  def waveColors_=(value: Seq[Color]): Unit =
    view.waveColors = value

  def start (): Unit = view.start()
  def stop  (): Unit = view.stop()

  def isRunning: Boolean = view.isRunning

  def target: Group = {
    if (_target != null || _bus == null) _target else _bus.server.rootNode
  }

  def target_=(value: Group): Unit = {
//    val old = target
    _target = value
//    if (value != old) {
//    }
  }

  def addAction: AddAction = _addAction

  def addAction_=(value: AddAction): Unit = {
    _addAction = value
  }

  def bufferSize: Int = _bufSize

  def bufferSize_=(value: Int): Unit = {
    require (value > 0)
    _bufSize = value
  }

  private[this] var syn: Synth = null
  private[this] var synOnline = false

  def dispose(): Unit = {
    view.dispose()
    val _syn  = syn
    syn       = null
    if (_syn != null) freeSynth(_syn)
  }

  private def freeSynth(syn: Synth): Unit = {
    try {
      val s         = syn.server
      val freeMsg   = syn.freeMsg
      if (synOnline) {
        s ! freeMsg
      } else {
        val syncMsg   = s.syncMsg()
        val synced    = syncMsg.reply
        s.!!(syncMsg) { case `synced` => s ! freeMsg }
      }
    } catch {
      case NonFatal(_) => // ignore
    }
  }
  
  private[this] var busType = 0

  private def setBusFromUI(off: Int, num: Int): Unit = if (_bus != null) {
    val b           = _bus
    val s           = b.server
    val numAudioIn  = s.config.inputBusChannels
    val numAudioOut = s.config.outputBusChannels
    val numAudio    = s.config.audioBusChannels
    val numControl  = s.config.controlBusChannels
    var index       = off
    var numChannels = num
    val newBus: Bus = busType match {
      case 0 =>
        index       = min(index, numAudioIn - 1) + numAudioOut
        numChannels = min(numChannels, max(0, numAudioOut + numAudioIn - index))
        AudioBus(s, index, numChannels)

      case 1 =>
        index       = min(index, numAudioOut - 1)
        numChannels = min(numChannels, max(0, numAudioOut - index))
        AudioBus(s, index, numChannels)

      case 2 =>
        index       = min(index, numAudio - 1)
        numChannels = min(numChannels, max(0, numAudio - index))
        AudioBus(s, index, numChannels)

      case 3 =>
        index       = min(index, numControl - 1)
        numChannels = min(numChannels, max(0, numControl - index))
        ControlBus(s, index, numChannels)
    }

    println(s"setBusFromUI($off, $num) index $index numChannels $numChannels")

    bus = newBus
  }

  private def setBusTypeFromUI(tpeIdx: Int, off: Int, num: Int): Unit = if (_bus != null) {
    val oldTpe      = busType
    if (oldTpe == tpeIdx) return
    busType         = max(0, min(3, tpeIdx))

    val b           = _bus
    val s           = b.server
    val numAudioIn  = s.config.inputBusChannels
    val numAudioOut = s.config.outputBusChannels
    val numAudio    = s.config.audioBusChannels
    val numControl  = s.config.controlBusChannels
    var offset      = off
    var numChannels = num

    busType match {
      case 0 if oldTpe == 2 =>
        offset      = max(0, min(offset - numAudioOut, numAudioIn - 1))
        numChannels = min(numChannels, max(0, numAudioIn - offset))
      case 0 =>
        offset      = 0
        numChannels = min(numChannels, numAudioIn)
      case 1 if oldTpe == 2 =>
        offset      = max(0, min(offset, numAudioOut - 1))
        numChannels = min(numChannels, max(0, numAudioOut - offset))
      case 1 =>
        offset      = 0
        numChannels = min(numChannels, numAudioOut)
      case 2 if oldTpe == 0 =>
        offset      = offset + numAudioOut
      case 2 if oldTpe == 1 =>
        // nada
      case 2 =>
        offset      = 0
        numChannels = min(numChannels, numAudio)
      case 3 =>
        offset      = 0
        numChannels = min(numChannels, numControl)
    }

    println(s"setBusTypeFromUI($tpeIdx, $off, $num) offset $offset numChannels $numChannels")

    setBusFromUI(off = offset, num = numChannels)
  }

  def bus: Bus = _bus

  def bus_=(value: Bus): Unit = {
    _bus = value

    val numChannels = value.numChannels
    val s           = bus.server
    val oldSyn      = syn

    if (numChannels > 0) {
      import Ops._
      val gf = new GraphFunction(() => {
        import de.sciss.synth.ugen._
        val inOff = "out".kr
        val in = if (value.rate == audio) {
          In.ar(inOff, numChannels)
        } else K2A.ar(
          In.kr(inOff, numChannels)
        )
        RecordBuf.ar(in, buf = "buf".kr, run = 1)
        ()
      })
      val synDef    = GraphFunction.mkSynthDef(gf)

      syn           = Synth (s)
      val b         = Buffer(s)
      val newMsg    = syn.newMsg(synDef.name, target = target, args = List("out" -> bus.index, "buf" -> b.id),
        addAction = addAction)

      val doneAlloc0  = newMsg :: synDef.freeMsg :: Nil
      val doneAlloc   = if (oldSyn == null) doneAlloc0 else oldSyn.freeMsg :: doneAlloc0

      val allocMsg  = b.allocMsg(numFrames = _bufSize, numChannels = bus.numChannels, completion =
        Some(osc.Bundle.now(doneAlloc: _*))
      )
      val recvMsg   = synDef.recvMsg
      val syncMsg   = s.syncMsg()
      val synced    = syncMsg.reply

      syn.onEnd { b.free() }

      synOnline = false
      s.!!(osc.Bundle.now(recvMsg, allocMsg, syncMsg)) {
        case `synced` =>
          // println("SYNCED")
          Swing.onEDT {
            synOnline   = true
            view.buffer = b
            //          start()
          }
      }

    } else {
      if (oldSyn != null) {
        freeSynth(oldSyn)
        syn           = null
        view.buffer   = null
      }
    }

    val numAudioIn  = s.config.inputBusChannels
    val numAudioOut = s.config.outputBusChannels
    val tpeIdx      = busType

    val (newTpe: Int, offNom: Int, numMax: Int) = value match {
      case ab: AudioBus if tpeIdx != 2 && ab.index >= numAudioOut && ab.index + numChannels <= numAudioOut + numAudioIn =>
        (0, ab.index - numAudioOut, numAudioIn)

      case ab: AudioBus if tpeIdx != 2 && ab.index >= 0 && ab.index + numChannels <= numAudioOut =>
        (1, ab.index, numAudioOut)

      case ab: AudioBus =>
        (2, ab.index, s.config.audioBusChannels)

      case cb: ControlBus =>
        (3, cb.index, s.config.controlBusChannels)
    }

    println(s"tpeIdx $tpeIdx index ${value.index} num ${value.numChannels} numAudioOut $numAudioOut numAudioIn $numAudioIn newTpe $newTpe numMax $numMax")

    removeBusListeners()
    ggBusType.setSelectedIndex(newTpe)
    mBusOff.setMaximum(numMax - 1)
    mBusOff.setValue(offNom)
    mBusNum.setMaximum(numMax)
    mBusNum.setValue(numChannels)
    busType = newTpe
    addBusListeners()
  }
}
