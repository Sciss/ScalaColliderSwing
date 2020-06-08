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

import java.awt.event.{ActionEvent, ComponentAdapter, ComponentEvent, InputEvent, ItemEvent, ItemListener, KeyEvent}
import java.awt.{BorderLayout, Color, Graphics2D}

import de.sciss.audiowidgets.AxisFormat
import de.sciss.audiowidgets.j.Axis
import de.sciss.numbers.Implicits._
import de.sciss.osc
import de.sciss.synth.{AddAction, AudioBus, Buffer, Bus, ControlBus, GraphFunction, Group, Ops, Synth, addToTail, audio}
import javax.swing.event.{AncestorEvent, AncestorListener, ChangeEvent, ChangeListener}
import javax.swing.{AbstractAction, Box, BoxLayout, JComboBox, JComponent, JPanel, JSpinner, KeyStroke, SpinnerNumberModel, SwingConstants}

import scala.collection.immutable.{Seq => ISeq}
import scala.math.{max, min, ceil}
import scala.swing.Swing
import scala.util.control.NonFatal

/** Component to view an oscilloscope for a real-time signal.
  *
  * The following keyboard shortcuts exist:
  *
  * - <kbd>Ctrl</kbd>-<kbd>Up</kbd>/<kdb>Down</kbd>: increase or decrease vertical zoom
  * - <kbd>Ctrl</kbd>-<kbd>Right</kbd>/<kdb>Left</kbd>: increase or decrease horizontal zoom
  * - <kbd>Space</kbd>: toggle run/pause
  * - <kbd>Period</kbd>: pause
  * - <kbd>J</kbd>/<kbd>L</kbd>: decrease or increase channel offset
  * - <kbd>K</kbd>: switch between audio and control rate buses
  * - <kbd>I</kbd>/<kbd>O</kbd>: switch to audio inputs and audio outputs
  * - <kbd>S</kbd>: switch between parallel and overlay mode
  * - <kbd>Shift</kbd>-<kbd>S</kbd>: switch between Lissajous (X/Y) and normal (X over time) mode
  */
class JScopePanel extends JPanel(new BorderLayout(0, 0)) with ScopeViewLike {
  private[this] val view          = new JScopeView

  private[this] val ggBusType     = new JComboBox(Array("Audio In", "Audio Out", "Audio Bus", "Control Bus"))
  private[this] val mBusOff       = new SpinnerNumberModel(0, 0, 8192, 1)
  private[this] val mBusNum       = new SpinnerNumberModel(1, 0 /*1*/, 8192, 1)
//  private[this] val mBufSize      = new SpinnerNumberModel(4096, 32, 65536, 1)
  private[this] val ggBusOff      = new JSpinner(mBusOff)
  private[this] val ggBusNum      = new JSpinner(mBusNum)
//  private[this] val ggBufSize     = new JSpinner(mBufSize)
  private[this] val ggStyle       = {
    val res = new JComboBox(Array("Parallel", "Overlay", "Lissajous"))
    res.addItemListener(new ItemListener {
      def itemStateChanged(e: ItemEvent): Unit = setStyleFromUI(res.getSelectedIndex)
    })
    res
  }

  private[this] val ggXAxis = {
    val a = new Axis(SwingConstants.HORIZONTAL)
    a.minimum = 0.0
    a.format  = AxisFormat.Integer
    a.addComponentListener(new ComponentAdapter {
      override def componentResized(e: ComponentEvent): Unit =
        updateXAxis()
    })
    a
  }

  private[this] var ggYAxes = new Array[Axis](0)

//  {
//    val a = new Axis(SwingConstants.VERTICAL)
//    a.fixedBounds = true
//    a.minimum     = -1.0
//    a.maximum     = +1.0
//    a
//  }

  private def setNumChannels(): Unit = {
    val num   = if (style == 0 && _bus != null) max(1, _bus.numChannels) else 1
    val oldCh = ggYAxes.length
    if (num != oldCh) {
      val axesNew = new Array[Axis](num)
      System.arraycopy(ggYAxes, 0, axesNew, 0, min(num, oldCh))
      if (num < oldCh) {
        var ch = oldCh
        while (ch > num) {
          ch -= 1
          pYAxes.remove(ch)
        }
      } else {
        var ch = oldCh
        while (ch < num) {
          val a = new Axis(SwingConstants.VERTICAL)
          setYZoom(a)
          axesNew(ch) = a
          pYAxes.add(a)
          ch += 1
        }
      }
      ggYAxes = axesNew
      pYAxes.revalidate()
      pYAxes.repaint()
    }
  }

  private[this] val pYAxes = {
    val p = new JPanel
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS))
    p
  }

  private[this] var _bus        : Bus       = null
  private[this] var _target     : Group     = null
  private[this] var _addAction  : AddAction = addToTail
  private[this] var _bufSize    : Int       = 4096
  private[this] var _bufSizeSet : Boolean   = false

  private[this] var syn         : Synth     = null
  private[this] var synOnline               = false

  private def fix(c: JComponent): c.type = {  // WTF
    c.setMaximumSize(c.getPreferredSize)
    c
  }

  private[this] val pTop1: JPanel = {
    val p = new JPanel
    p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS))
    p.add(fix(ggBusType))
    p.add(fix(ggBusOff))
    ggBusOff.setToolTipText("Bus Offset")
    p.add(fix(ggBusNum))
    ggBusNum.setToolTipText("No. of Channels")
//    p.add(Box.createHorizontalStrut(8))
//    p.add(fix(ggBufSize))
//    ggBufSize.setToolTipText("Buffer Size")
    p.add(Box.createHorizontalGlue())
    p.add(fix(ggStyle))
    p
  }

  private[this] val pTop2: JPanel = {
    val p = new JPanel
    p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS))
    setNumChannels() // init
    p.add(Box.createHorizontalStrut(ggYAxes(0).getPreferredSize.width))
    p.add(ggXAxis)
    p
  }

  private[this] val pTop: JPanel = {
    val p = new JPanel
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS))
    p.add(pTop1)
    p.add(pTop2)
    p
  }

  private[this] val lBusOffNum: ChangeListener = new ChangeListener {
    def stateChanged(e: ChangeEvent): Unit =
      setBusFromUI(mBusOff.getNumber.intValue(), mBusNum.getNumber.intValue())
  }

  private[this] val lBusType: ItemListener = new ItemListener {
    def itemStateChanged(e: ItemEvent): Unit = {
      val t = max(0, ggBusType.getSelectedIndex)
      setBusTypeFromUI(t)
    }
  }

  // constructor
  {
    // install key actions
    val im        = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
    val am        = this.getActionMap
    val ksIncY    = KeyStroke.getKeyStroke(KeyEvent.VK_UP   , InputEvent.CTRL_MASK)
    val ksDecY    = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN , InputEvent.CTRL_MASK)
    val ksIncX    = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.CTRL_MASK)
    val ksDecX    = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT , InputEvent.CTRL_MASK)
    val ksDecCh   = KeyStroke.getKeyStroke(KeyEvent.VK_J, 0)
    val ksIncCh   = KeyStroke.getKeyStroke(KeyEvent.VK_L, 0)
    val ksSwRate  = KeyStroke.getKeyStroke(KeyEvent.VK_K, 0)
    val ksSwIn    = KeyStroke.getKeyStroke(KeyEvent.VK_I, 0)
    val ksSwOut   = KeyStroke.getKeyStroke(KeyEvent.VK_O, 0)
    val ksSwOver  = KeyStroke.getKeyStroke(KeyEvent.VK_S, 0)
    val ksSwXY    = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_MASK)
    val ksStart   = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)
    val ksStop    = KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, 0)

    val aIncY = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit =
        yZoom = yZoom * 2f
    }
    val aDecY = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit =
        yZoom = yZoom * 0.5f
    }
    val aIncX = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit =
        xZoom = xZoom * (if (style == 2) 2f else 0.5f)
    }
    val aDecX = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit =
        xZoom = xZoom * (if (style == 2) 0.5f else 2f)
    }
    val aIncCh = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit =
        Option(mBusOff.getNextValue).foreach(mBusOff.setValue)
    }
    val aDecCh = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit =
        Option(mBusOff.getPreviousValue).foreach(mBusOff.setValue)
    }
    val aSwRate = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit = {
        val newTpe = if (busType < 3) 3 else 2
        setBusTypeFromUI(newTpe)
      }
    }
    val aSwIn = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit = if (_bus != null) {
        val numAudioIn = _bus.server.config.inputBusChannels
        setBusTypeFromUI(0, 0, numAudioIn)
      }
    }
    val aSwOut = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit = if (_bus != null) {
        val numAudioOut = _bus.server.config.outputBusChannels
        setBusTypeFromUI(1, 0, numAudioOut)
      }
    }
    val aSwOver = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit =
        style = if (style == 0) 1 else 0
    }
    val aSwXY = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit =
        style = if (style == 2) 0 else 2
    }
    val aStart = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit =
        if (isRunning) stop() else start()
    }
    val aStop = new AbstractAction() {
      def actionPerformed(e: ActionEvent): Unit =
        stop()
    }

    am.put("y-inc", aIncY)
    im.put(ksIncY, "y-inc")
    am.put("y-dec", aDecY)
    im.put(ksDecY, "y-dec")
    am.put("x-inc", aIncX)
    im.put(ksIncX, "x-inc")
    am.put("x-dec", aDecX)
    im.put(ksDecX, "x-dec")
    am.put("ch-inc", aIncCh)
    im.put(ksIncCh, "ch-inc")
    am.put("ch-dec", aDecCh)
    im.put(ksDecCh, "ch-dec")
    am.put("switch-rate", aSwRate)
    im.put(ksSwRate, "switch-rate")
    am.put("switch-in", aSwIn)
    im.put(ksSwIn, "switch-in")
    am.put("switch-out", aSwOut)
    im.put(ksSwOut, "switch-out")
    am.put("switch-over", aSwOver)
    im.put(ksSwOver, "switch-over")
    am.put("switch-xy", aSwXY)
    im.put(ksSwXY, "switch-xy")
    am.put("start", aStart)
    im.put(ksStart, "start")
    am.put("stop", aStop)
    im.put(ksStop, "stop")

//    am.put("DUMP", new AbstractAction() {
//      def actionPerformed(e: ActionEvent): Unit = view.DUMP = !view.DUMP
//    })
//    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "DUMP")

    add(pTop  , BorderLayout.NORTH  )
    add(pYAxes, BorderLayout.WEST   )
    add(view  , BorderLayout.CENTER )
    addBusListeners()

//    ggBufSize.addChangeListener(new ChangeListener {
//      def stateChanged(e: ChangeEvent): Unit =
//        bufferSize = mBufSize.getNumber.intValue()
//    })

    view.overlayPainter = new ScopeViewOverlayPainter {
      def paintScopeOverlay(g: Graphics2D, width: Int, height: Int): Unit =
        if (!isRunning) {
          g.setColor(Color.orange)
          g.fillRect( 4, 4, 4, 16)
          g.fillRect(12, 4, 4, 16)
        }
    }

    view.addAncestorListener(new AncestorListener {
      def ancestorAdded(e: AncestorEvent): Unit =
        view.requestFocus()

      def ancestorRemoved (e: AncestorEvent): Unit = ()
      def ancestorMoved   (e: AncestorEvent): Unit = ()
    })

//    view.addComponentListener(new ComponentAdapter {
//      override def componentShown(e: ComponentEvent): Unit =
//        view.requestFocus()
//    })
  }

  private def removeBusListeners(): Unit = {
    ggBusOff  .removeChangeListener (lBusOffNum )
    ggBusNum  .removeChangeListener (lBusOffNum )
    ggBusType .removeItemListener   (lBusType   )
  }

  private def addBusListeners(): Unit = {
    ggBusOff  .addChangeListener    (lBusOffNum )
    ggBusNum  .addChangeListener    (lBusOffNum )
    ggBusType .addItemListener      (lBusType   )
  }

  def style: Int = view.style

  def style_=(value: Int): Unit = {
    ggStyle.setSelectedIndex(value)
    setStyleFromUI(value)
  }

  private def setStyleFromUI(value: Int): Unit = {
    view.style = value
    if (value == 2 && mBusNum.getNumber.intValue() != 2) {
      setBusFromUI(mBusOff.getNumber.intValue(), 2)
    }
    updateXAxis()
    setNumChannels()
  }

  def xZoom: Float = view.xZoom

  def xZoom_=(value: Float): Unit = {
    view.xZoom = value
    updateXAxis()
  }

  private def updateXAxis(): Unit = {
    var bestBufSize = view.getWidth
    if (style == 2) {
      val maxVal          = 1.0 / xZoom
      val minVal          = -maxVal
      ggXAxis.minimum     = minVal
      ggXAxis.maximum     = maxVal
      ggXAxis.fixedBounds = maxVal >= 0.5
      ggXAxis.format      = AxisFormat.Decimal
      bestBufSize *= 4
    } else {
      val numFramesF      = bestBufSize * xZoom
      ggXAxis.maximum     = numFramesF
      ggXAxis.fixedBounds = false
      ggXAxis.format      = AxisFormat.Integer
      bestBufSize = ceil(numFramesF).toInt
    }

    if (!_bufSizeSet) {
      bestBufSize = bestBufSize.nextPowerOfTwo.clip(64, 65536)
      setBufferSize(bestBufSize)
    }
  }

  def yZoom: Float = view.yZoom

  def yZoom_=(value: Float): Unit = {
    view.yZoom = value
    var ch = 0
    while (ch < ggYAxes.length) {
      setYZoom(ggYAxes(ch))
      ch += 1
    }
  }

  private def setYZoom(a: Axis): Unit = {
    val maxVal    = 1.0 / yZoom
    val minVal    = -maxVal
    a.minimum     = minVal
    a.maximum     = maxVal
    a.fixedBounds = maxVal >= 0.5
  }

  def waveColors: ISeq[Color] = view.waveColors

  def waveColors_=(value: ISeq[Color]): Unit =
    view.waveColors = value

  def start(): Unit = view.start()

  def stop(): Unit = {
    view.stop()
    view.repaint()
  }

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

  /** The default buffer size is dynamically
    * updated according to the number of frames
    * currently displayed (in Lissajous mode
    * four times the width). If it is set
    * explicitly, the dynamic adjustment is turned off.
    * It can be turned on by setting it to zero.
    */
  def bufferSize: Int = _bufSize

  /** The default buffer size is dynamically
    * updated according to the number of frames
    * currently displayed (in Lissajous mode
    * four times the width). If it is set
    * explicitly, the dynamic adjustment is turned off.
    * It can be turned on by setting it to zero.
    */
  def bufferSize_=(value: Int): Unit =
    if (value > 0) {
      _bufSizeSet = true
      setBufferSize(value)
    } else if (_bufSizeSet) {
      _bufSizeSet = false
      updateXAxis()
    }

  private def setBufferSize(value: Int): Unit = {
    if (_bufSize != value) {
      require (value > 0)
      _bufSize    = value
      if (isRunning) bus = bus
    }
  }

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

//    println(s"setBusFromUI($off, $num) index $index numChannels $numChannels")

    bus = newBus
  }

  private def setBusTypeFromUI(tpeIdx: Int): Unit =
    setBusTypeFromUI(tpeIdx, mBusOff.getNumber.intValue(), mBusNum.getNumber.intValue())

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

//    println(s"setBusTypeFromUI($tpeIdx) offset $offset numChannels $numChannels")

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
        val buf   = "buf".kr
        if (value.rate == audio) {
          val in = In.ar(inOff, numChannels)
          RecordBuf.ar(in, buf = buf)
        } else {
          // XXX TODO --- should use RecordBuf.kr with shorter buffers
          val in = In.kr(inOff, numChannels)
          RecordBuf.ar(K2A.ar(in), buf = buf)
        }
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

//    println(s"tpeIdx $tpeIdx index ${value.index} num ${value.numChannels} numAudioOut $numAudioOut numAudioIn $numAudioIn newTpe $newTpe numMax $numMax")

    removeBusListeners()
    ggBusType.setSelectedIndex(newTpe)
    mBusOff.setMaximum(numMax - 1)
    mBusOff.setValue(offNom)
    mBusNum.setMaximum(numMax)
    mBusNum.setValue(numChannels)
    busType = newTpe
    setNumChannels()
    addBusListeners()
  }
}
