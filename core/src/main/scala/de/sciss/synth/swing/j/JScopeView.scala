/*
 *  JScopeView.scala
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

import java.awt.event.{MouseAdapter, MouseEvent}
import java.awt.{BasicStroke, Color, Dimension, Graphics, Graphics2D, RenderingHints}
import java.io.IOException
import java.util

import de.sciss.audiowidgets.Util
import de.sciss.osc.Message
import de.sciss.synth.message.{BufferGetn, BufferSetn, Responder}
import de.sciss.synth.{Buffer, Server}
import javax.swing.JComponent

import scala.collection.immutable.{Seq => ISeq}
import scala.util.control.NonFatal

trait ScopeViewLike {
  var style: Int
  var xZoom: Float
  var yZoom: Float
  var waveColors: ISeq[Color]

  def start(): Unit
  def stop (): Unit

  def dispose(): Unit

  def isRunning: Boolean
}

// Quick and dirty translation from SwingOSC (Java)
object JScopeView {
  // warning: while scsynth seems to have been updated to 64K buffers,
  // the /b_setn is only sent for sizes <= 8K for some reason!!
  private val OSC_BUF_SIZE    = 8192
  private val SLICE_SIZE      = (OSC_BUF_SIZE - 32) / 5 // 4 bytes per float, 1byte per type-tag : < 64K
  private val COLOR_FG_DARK   = Color.yellow
  private val COLOR_FG_LIGHT  = new Color(127, 127, 0)

  final val STYLE_CHANNELS  = 0
  final val STYLE_OVERLAY   = 1
  final val STYLE_LISSAJOUS = 2
}

class JScopeView extends JComponent with ScopeViewLike {
  import JScopeView._

  private[this] val isDark    = Util.isDarkSkin
  private[this] val colorFg   = if (isDark) COLOR_FG_DARK else COLOR_FG_LIGHT

  // constructor
  setOpaque(true)
  setBackground(if (isDark) Color.black else Color.white)
  setFocusable(true)
  addMouseListener(new MouseAdapter() {
    override def mousePressed(e: MouseEvent): Unit =
      requestFocus()
  })
  setPreferredSize(new Dimension(250, 250))

  @volatile
  private[this] var vector        = null : Array[Float]

  @volatile
  private[this] var pntVector     = null : Array[Float]
  // note that since Graphics.drawPolyline is much
  // faster than Graphics2D.draw( GeneralPath ), we
  // use poly-lines. to allow for some amount of antialiasing
  // both x and y coordinates are scaled up by a factor of 4
  // and the Graphics2D context will scale them down again
  private[this] var polyX         = null : Array[Int]
  private[this] var polyY         = null : Array[Int]

  private[this] var size          = 0

  @volatile
  private[this] var bufNum        = -1
  private[this] var numFrames     = 0
  private[this] var numChannels   = 0
  private[this] var _buffer       = null : Buffer

  @volatile
  private[this] var sliceIdx      = 0

//  @volatile
//  private[this] var numSlices     = 0

  private[this] var polySize      = 0

  @volatile
  private[this] var polySizeC     = 0

  private[this] var _waveColors   = new Array[Color](0)
  private[this] val strokePoly    = new BasicStroke(4f)
  private[this] var recentWidth   = - 1
  private[this] var _xZoom        = 1.0f
  private[this] var _yZoom        = 1.0f
  private[this] var overlay       = false
  private[this] var lissajous     = false
  private[this] var _style        = 0

  @volatile
  private[this] var _isRunning    = false

  private[this] var _server       = null : Server
  private[this] var msgBufGetN    = null : Array[BufferGetn]
  private[this] var msgBufOff     = null : Array[Int]
  private[this] var responder     = null : Responder

//  private[this] val timer  = {
//    val res = new Timer(1000, this)
//    res.setRepeats(true)
//    res
//  }

//  def server_=(value: Server): Unit = {
//    val wasListening  = _isRunning
//    if (wasListening) stop()
//    _server   = value
//    responder = Responder(value)(handler)
//    if (wasListening) start()
//  }
//
//  def server: Server = _server

  def start(): Unit = {
    if (! _isRunning ) {
//      if (_server == null) throw new IllegalStateException("Server has not been specified")
      try {
        if (responder != null) {
          responder.add()
          query()
        }
        _isRunning = true
//        timer.restart()
      } catch {
        case e1: IOException =>
          msgBufGetN = null
          _isRunning = false
          System.out.println(e1)
      }
    }
  }

  def stop(): Unit = {
    if (_isRunning) {
//      timer.stop()
      if (responder != null) responder.remove()
      _isRunning    = false
    }
  }

  def dispose(): Unit = {
    stop()
    msgBufGetN    = null
    _server       = null
    responder     = null
    vector        = null
    pntVector     = null
  }

  def isRunning: Boolean = _isRunning

  def buffer_=(value: Buffer): Unit = {
    _buffer       = value
    if (responder != null) responder.remove()

    if (value != null) {
      numFrames   = value.numFrames
      numChannels = value.numChannels
      bufNum      = value.id
      _server     = value.server
      responder   = Responder(value.server)(handler)
    } else {
      numFrames   = 0
      numChannels = 0
      bufNum      = -1
      _server     = null
      responder   = null
    }

    size          = numFrames * numChannels
    vector        = new Array[Float](size)
    pntVector     = new Array[Float](size)
    val polySize  = numFrames << 1
    polyX         = new Array[Int](polySize)
    polyY         = new Array[Int](polySize)
    recentWidth   = -1 // need to recalculate X coordinates

    createMessages()
    if (_isRunning) {
      if (value != null) {
        responder.add()
        query()
      } else {
        repaint()
      }
    }
  }

  def buffer: Buffer = _buffer

  private def createMessages(): Unit = {
    val _numSlices = (size + SLICE_SIZE - 1) / SLICE_SIZE
    if (_numSlices == 0) return
    val _msgBufGetN  = new Array[BufferGetn](_numSlices)
    val _msgBufOff   = new Array[Int       ](_numSlices)
    var i       = 0
    var off     = 0
    while (i < _numSlices) {
      _msgBufOff (i) = off
      _msgBufGetN(i) = BufferGetn(bufNum, off until Math.min(off + SLICE_SIZE, size))

      i   += 1
      off += SLICE_SIZE
    }
    msgBufGetN  = _msgBufGetN
    msgBufOff   = _msgBufOff
//    numSlices   = -numSlices
    sliceIdx    = -1
  }

//  def setBufNum(bufNum: Int): Unit = {
//    if (_isRunning) {
//      msgBufGetN = null // don't query until we've got the buffer specs
//
//      this.bufNum = bufNum
//      try {
//        _server ! BufferQuery(bufNum)
//      }
//      catch {
//        case e1: IOException =>
//          System.out.println(e1)
//      }
//    }
//    else throw new IllegalStateException("JScopeView.setBufNum : call startListening before!")
//
//  }

  def style_=(value: Int): Unit = {
    this._style  = value
    overlay     = value >= STYLE_OVERLAY
    lissajous   = value == STYLE_LISSAJOUS
    recentWidth = -1
    repaint()
  }

  def style: Int = _style

  def xZoom_=(value: Float): Unit = {
    _xZoom       = value
    recentWidth = -1 // triggers recalculation
    repaint()
  }

  def xZoom: Float = _xZoom

  def yZoom_=(f: Float): Unit = {
    _yZoom = f
    repaint()
  }

  def yZoom: Float = _yZoom

  def waveColors_=(c: ISeq[Color]): Unit = {
    _waveColors = c.toArray
    repaint()
  }

  def waveColors: ISeq[Color] = _waveColors.toSeq

  private def query(): Unit = {
    val _msgBufGetN = msgBufGetN
    val _msgBufOff  = msgBufOff
    val s           = _server
    if (_msgBufGetN != null && _msgBufOff != null && s != null)
      try {
        sliceIdx = {
          val tmp = sliceIdx + 1
          if ((tmp == _msgBufGetN.length) || (_msgBufOff(tmp) > polySizeC)) 0 else tmp
        }
//        println(s"sliceIdx = $sliceIdx")
        s ! _msgBufGetN(sliceIdx)
      } catch {
        case e1: IOException =>
          System.out.println(e1)
      }

//    timer.restart()
  }

  override def paintComponent(g: Graphics): Unit = {
    super.paintComponent(g)
    try {
      paintComponent1(g)
    } catch {
      case NonFatal(ex) =>
        ex.printStackTrace()
    }
  }

  private def paintComponent1(g: Graphics): Unit = {
//    super.paintComponent(g)
    val g2          = g.asInstanceOf[Graphics2D]
    val w           = getWidth
    val h           = getHeight
    val atOrig      = g2.getTransform
    val strokeOrig  = g2.getStroke
    var sy          = 0.0
    var offY        = 0f
    var x           = 0
    var y           = 0
    g2.setColor(getBackground)
    g2.fillRect(0, 0, w, h)

    if ((pntVector == null) || (numChannels == 0)) return

    var sah = false

    if (lissajous) {
      // the interpretation of the xZoom value is completely
      // stupid in cocoa scope. in lissajous mode increasing zoom
      // will increase scale, while in normal mode it's the other way round
      val offX  = w << 1
      val sx    = offX * _xZoom
      polySize  = numFrames
      polySizeC = polySize * numChannels

      var i  = 0
      var k  = 0
      while (i < polySize) {
        x = (pntVector(k) * sx + offX).toInt
        polyX(i) = x

        i += 1
        k += numChannels
      }
      recentWidth = w
    }
    else if (w != recentWidth) { // have to recalculate horizontal coordinate
      val sx    = 4 / _xZoom
      polySize  = Math.min(numFrames, (w * _xZoom).toInt + 1)
      polySizeC = polySize * numChannels
      sah       = sx > 12
      if (sah) {
        x = 0
        var i  = 0
        var j  = 0
        while (i < polySize) {
          polyX(j) = x
          j += 1
          x = (i * sx).toInt
          polyX(j) = x
          j += 1
          i += 1
        }
      } else {
        var i = 0
        while (i < polySize) {
          x = (i * sx).toInt
          polyX(i) = x
          i += 1
        }
      }
      recentWidth = w
    }

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    if (overlay) {
      offY  = h * 0.5f
      sy    = -(h << 1) * _yZoom
    }
    else {
      sy    = -(h << 1) * _yZoom / numChannels
    }

    var ch = if (lissajous) 1 else 0
    while (ch < numChannels) {
      g2.setColor(if (_waveColors.length > ch) _waveColors(ch) else colorFg)
      g2.setStroke(strokePoly)
      if (overlay) g2.translate(0, offY)
      else {
        offY = (((ch << 1) + 1) * h).toFloat / (numChannels << 1)
        g2.translate(0, offY)
      }
      g2.scale(0.25f, 0.25f)
      if (sah) { // sample-and-hold
        var i  = 0
        var j  = 0
        var k  = ch
        while (i < polySize) {
          y = (pntVector(k) * sy).toInt
          polyY(j) = y
          j += 1
          polyY(j) = y
          j += 1
          i += 1
          k += numChannels
        }
        g2.drawPolyline(polyX, polyY, polySize << 1)
      }
      else {
        var i  = 0
        var k  = ch
        while (i < polySize) {
          y = (pntVector(k) * sy).toInt
          polyY(i) = y

          i += 1
          k += numChannels
        }
        g2.drawPolyline(polyX, polyY, polySize)
      }
      g2.setTransform(atOrig)
      g2.setStroke(strokeOrig)

      ch += 1
    }
  }

//  // called upon timeout
//  override def actionPerformed(e: ActionEvent): Unit =
//    if (_isRunning) setBufNum(bufNum)

  // ------------- OSCListener interface -------------
  private[this] val handler: PartialFunction[Message, Unit] = {
    case BufferSetn(id, (idx, values)) if id == bufNum /*&& idx == sliceOff*/ =>
      val stop  = idx + values.size
      val vec   = vector
      if (vec != null) {
        val num = Math.min(vec.length, stop) - idx
        if (num > 0) {
//          println(s"baap idx $idx num $num polySizeC $polySizeC")
          var i = 0
          var j = idx
          while (i < num) {
            vec(j) = values(i)  // XXX TODO --- or is toArray faster?
            i += 1
            j += 1
          }
//          System.arraycopy(values /* NOT AN ARRAY */, 0, vec, idx, num)
          if (idx + num >= polySizeC /*|| idx + 1 == numSlices*/) {
//            println("boop")
            val pnt = pntVector
            if (pnt != null) {
              val n   = Math.min(vec.length, pnt.length)
              val m   = pnt.length - n
              System.arraycopy(vec, 0, pnt, 0, n)
              if (m > 0) util.Arrays.fill(pnt, n, m, 0f)
              repaint() // paint complete waveform
            }
          }
        }
      }
      if (_isRunning) query()
  }
}