/*
 *  JScopeView2.scala
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
import java.awt.{BasicStroke, Color, Dimension, EventQueue, Graphics, Graphics2D, RenderingHints}
import java.io.IOException
import java.util

import de.sciss.audiowidgets.Util
import de.sciss.osc.Message
import de.sciss.synth.Server
import de.sciss.synth.message.{BufferGetn, BufferSetn, Responder, Trigger}
import javax.swing.JComponent

import scala.collection.immutable.{Seq => ISeq}


trait ScopeViewOverlayPainter {
  def paintScopeOverlay(g: Graphics2D, width: Int, height: Int): Unit
}

/** @define info
  * An oscilloscope canvas component.
  * Has controls for resolution (zoom), setting
  * waveform color, and choosing between three
  * different styles: 0 - parallel, 1 - overlay, 2 - lissajous (x/y).
  */
trait ScopeViewLike {
  /** The drawing style can be one of
    * `0` (or `JScopeView.STYLE_PARALLEL`),
    * `1` (or `JScopeView.STYLE_OVERLAY`),
    * `2` (or `JScopeView.STYLE_LISSAJOUS`).
    *
    * In parallel or "normal" style, each channel is drawn separately
    * in a vertical per-channel arrangement. In overlay mode, all channels
    * are drawn superimposed on each other. In Lissajous or X/Y style,
    * the first channel specifies the x-coordinate, and the second channel
    * specifies the y-coordinate.
    */
  var style: Int
  var xZoom: Float
  var yZoom: Float
  var waveColors: ISeq[Color]

  var screenColor: Color

  def start(): Unit
  def stop (): Unit

  def dispose(): Unit

  def isRunning: Boolean
}

object JScopeView {
  // warning: while scsynth seems to have been updated to 64K buffers,
  // the /b_setn is only sent for sizes <= 8K for some reason!!
  private val OSC_BUF_SIZE    = 8192
//  private val SLICE_SIZE      = (OSC_BUF_SIZE - 27) / 5 // = 1633 or roughly 3 ms at 48 kHz; 4 bytes per float, 1 byte per type-tag : < 64K
  private val SLICE_SIZE      = (OSC_BUF_SIZE - 37) / 5 // = 1631, when using two ranges

  private val COLOR_FG_DARK   = Color.lightGray // Color.yellow
  private val COLOR_FG_LIGHT  = Color.darkGray  // new Color(127, 127, 0)

  final val STYLE_PARALLEL  = 0
  final val STYLE_OVERLAY   = 1
  final val STYLE_LISSAJOUS = 2

  object Config {
    final val Empty = Config(null, -1, 0, 0, 0, -1, 0)

    def defaultTrigFrames(server: Server): Int = {
      val sr      = server.sampleRate
      val fr0     = (sr / 30).toInt
      val bSz     = server.config.blockSize
      val blocks  = (fr0 + bSz - 1) / bSz
      blocks * bSz
    }

    def defaultTrigFreq(server: Server): Double = {
      val frames = defaultTrigFrames(server)
      server.sampleRate / frames
    }

    def default(server: Server, bufId: Int, useFrames: Int, numChannels: Int, nodeId: Int): Config = {
      val SLICE_SIZE_2M = SLICE_SIZE - 1
      val useSize       = useFrames * numChannels
      val slicesPerUse  = (useSize + SLICE_SIZE_2M) / SLICE_SIZE
      val latencyFrames = (server.sampleRate * server.clientConfig.latency).toInt
      val latencyPerUse = latencyFrames * slicesPerUse
      val size0         = Math.max(latencyPerUse, useSize << 1)
      val numSlices     = (size0 * numChannels + SLICE_SIZE_2M) / SLICE_SIZE
      val bufSize       = numSlices * SLICE_SIZE
      val bufFrames     = bufSize / numChannels
      val trigFrames    = defaultTrigFrames(server)
      Config(server, bufId = bufId, bufFrames = bufFrames, numChannels = numChannels,
        useFrames = useFrames, nodeId = nodeId, trigFrames = trigFrames)
    }
  }
  final case class Config(server: Server, bufId: Int, bufFrames: Int, numChannels: Int, useFrames: Int, nodeId: Int,
                          trigFrames: Int) {
    def isEmpty : Boolean = bufId <  0
    def nonEmpty: Boolean = bufId >= 0

    val bufSize   : Int = bufFrames  * numChannels
    val useSize   : Int = useFrames  * numChannels
    val trigSize  : Int = trigFrames * numChannels
  }
}

/** $info
  */
class JScopeView extends JComponent with ScopeViewLike {
  import JScopeView._

  private[this] val isDark    = Util.isDarkSkin
  private[this] val colorFg   = if (isDark) COLOR_FG_DARK else COLOR_FG_LIGHT

  @volatile
  private[this] var pntVector     = null : Array[Float]
  // note that since Graphics.drawPolyline is much
  // faster than Graphics2D.draw( GeneralPath ), we
  // use poly-lines. to allow for some amount of antialiasing
  // both x and y coordinates are scaled up by a factor of 4
  // and the Graphics2D context will scale them down again
  private[this] var polyX         = null : Array[Int]
  private[this] var polyY         = null : Array[Int]

  private[this] var _config       = Config.Empty: Config

  private[this] var polySize      = 0
  @volatile
  private[this] var polySizeC     = 0

  private[this] var _waveColors   = new Array[Color](0)
  private[this] val strokePoly    = new BasicStroke(4f)
  private[this] var recentWidth   = - 1
  private[this] var _xZoom        = 1.0f
  private[this] var _xZoomNorm    = 1.0f
  private[this] var _xZoomLissa   = -1.0f
  private[this] var _yZoom        = 1.0f
  private[this] var _overlay      = false
  private[this] var _lissajous    = false
  private[this] var _style        = 0
  private[this] var sah           = false
  private[this] var _isRunning    = false
  private[this] var _state        = null : State
  private[this] var _overlayPnt   = null : ScopeViewOverlayPainter

  // constructor
  {
    setOpaque(true)
    setBackground(if (isDark) Color.black else Color.white)
    setFocusable(true)
    addMouseListener(new MouseAdapter() {
      override def mousePressed(e: MouseEvent): Unit =
        requestFocus()
    })
    setPreferredSize(new Dimension(250, 250))
  }

  /*

   Technique outline.

   The synth sends triggers for an integer counter, referring to
   a given 'frame rate' (ideally related to SLICE_SIZE but given that
   we can have multi-channel buffers, perhaps it needs to be less frequent).
   For example, if we relate to visual update rates, say 20-30 Hz.
   Say the frame size is F -- then the trigger emitting 1, 2, 3, 4, 5, ...
   means the recorder has advanced to 1F, 2F, 3F, ... frames.

   The buffer query instance (BQI) should remain faithful to the numUseFrames,
   in case the user wants to really sync the display with some period.

   The BQI when initialised, simply waits for the first trigger to arrive, that is
   its starting point. First trigger value t0 then gives an advancement sizeWritten = t0*F*numChannels.
   The advancement is rounded down to an integer multiple of the numUseFrames,
   to obtain sizeQueried.
   (0) The BQI calculates sizeQueried = sizeWritten - (sizeWritten % (numUseFrames*numChannels
   and resets its vecOff count to zero, and if sizeWritten is greater
   than sizeQueried, (1) queries the next slice. It maintains a flag hasQueried to reflect whether
   there is an ongoing query (true) or not (false).

   Two things happen now in parallel: (a) the BQI receives new triggers, (b) and it possibly receives
   the b_setn messages. (a) If a trigger is received and hasQueried is true, it simply stores the
   new sizeWritten value. If hasQueried is false, it checks again if the new sizeWritten permits
   the first query, i.e. goes back to step (1). (b) if b_setn is received, sizeQueried is advanced and data copied.
   If a full numUseFrames have now been acquired, GUI is updated, and we proceed to step (3).
   If not, then sizeQueried is compared to sizeWritten. If it is less, we can query the next slice,
   i.e. go back to step (1). If not, we'll have to wait for a trigger update.

   (3) After a a full numUseFrames have been acquired, we "resynchronise", calculating the new
   sizeQueried value and returning to step (0). In other words, either we were fast enough, and this
   simply the adjacent part of the buffer, or we are skipping over an integer number of numUseFrames
   sections.

   From this, it's clear that for simplicity (avoiding were short b_getn at the buffer end) the actual
   buffer size should be a multiple of SLICE_SIZE. Furthermore, since we round down to integer multiples of
   numUseFrames, the buffer size should be at least 2*numUseFrames*numChannels. Furthermore, to take the
   latency between client and server into account, the buffer duration should be at least the latency.
   For example, SLICE_SIZE is 1632, numUseFrames is 1024, numChannels is 3, and the latency is specified as
   200ms, and the sampling rate be 44.1 kHz, thus giving the latency as 8820 sample frames.
   The buffer size thus becomes

   val size0     = max(8820, 1024 * 2)                           // = 8820
   val numSlices = (size0 * 3 + SLICE_SIZE - 1) / SLICE_SIZE     // = 17
   val bufSize   = numSlices * SLICE_SIZE                        // = 27744
   val bufFrames = bufSize / 3                                   // = 9248

   Or should use a value greater than the nominal latency? Because we have multiple
   round trips (1.88 in this case per full numUseFrames). So perhaps here we should expect a total
   time for one full buffer acquisition to be 4 * 100ms. So an improved calculation:

   val slicesPerUse  = (1024 * 3 + SLICE_SIZE - 1) / SLICE_SIZE  // = 2
   val latencyPerUse = 8820 * 2 * slicesPerUse                   // 35280
   val size0         = max(35280, 1024 * 2)                      // = 35280
   val numSlices     = (size0 * 3 + SLICE_SIZE - 1) / SLICE_SIZE // = 65
   val bufSize       = numSlices * SLICE_SIZE                    // = 106080
   val bufFrames     = bufSize / 3                               // = 35360

   Does that make sense? Looks overly large, but that's due to large latency assumed. Perhaps we should
   assume that the latency is conservative, to avoid "late" messages in normal playback, but that since jitter
   is less severe problem here, we use "single" latency per b_getn instead of two (round trip). That would
   essentially half the latencyPerUse, and so:

   val slicesPerUse  = (1024 * 3 + SLICE_SIZE - 1) / SLICE_SIZE  // = 2
   val latencyPerUse = 8820 * slicesPerUse                       // 17640
   val size0         = max(17640, 1024 * 2)                      // = 17640
   val numSlices     = (size0 * 3 + SLICE_SIZE - 1) / SLICE_SIZE // = 33
   val bufSize       = numSlices * SLICE_SIZE                    // = 53856
   val bufFrames     = bufSize / 3                               // = 17952

   Or around 400ms. I think we should try this approach and see if it works with sync errors appearing
   reasonably seldom to deem the oscilloscope very accurate.

   Side-note: With this many different b_getn messages, perhaps it no longer makes sense to pre-calculate and
   store them all, like SwingOSC did back in 2005'ish?

   ----

   Notes:
   - we cannot avoid "inefficient" slices, because there is no guarantee
     that numUseFrames * numChannels is a multiple of SLICE_SIZE. So unless we give up
     hard sync on the given numUseFrames, we have to live with that. We send out
     a b_getn message with two ranges if we reach the end of the buffer.

  */

  private final class State(val c: Config) extends PartialFunction[Message, Unit] { self =>

    private[this] var hasQueried    = false
    private[this] var initialized   = false
    private[this] var sizeWritten   = 0L
    private[this] var sizeQueried   = 0L

    private[this] var getOff1       = -1
    private[this] var getLen1       = 0
    private[this] var getLen2       = 0   // this must be zero if only using one bufGetN!

    private[this] val vector        = new Array[Float](c.useSize)
    private[this] var vecOff        = 0
    private[this] var vecRemain     = 0

    private[this] val responder     = Responder(c.server)(this)

    def start   (): Unit = responder.add()
    def dispose (): Unit = responder.remove()

    def isDefinedAt(m: Message): Boolean = m match {
      case b: BufferSetn  if b.id == c.bufId && hasQueried /*&& b.indicesAndValues.size == 1*/ => true
      case t: Trigger     if t.nodeId == c.nodeId => true
      case _ => false
    }

//    private def newVector(): Unit = {
//      sizeQueried = sizeWritten - (sizeWritten % c.useSize)
//      vecRemain   = Math.min(polySizeC, c.useSize)
//      LOG(s"newVector(); sizeQueried = $sizeQueried; vecRemain $vecRemain")
//      tryQuery()
//    }

    private def newVector(): Unit = {
      val newSzQueried  = sizeWritten - (sizeWritten % c.useSize)
      val newSzQueriedP = newSzQueried - c.useSize
      // test: see if we speed up updates by allowing to poll the previous frame.
      // yes, that seems to be the case (we avoid clogging).
      sizeQueried = if (newSzQueriedP > sizeQueried) newSzQueriedP else newSzQueried
      vecRemain   = Math.min(polySizeC, c.useSize)
      vecOff      = 0
//      LOG(s"newVector(); sizeQueried = $sizeQueried; vecRemain $vecRemain")
      tryQuery()
    }

    private def tryQuery(): Unit = {
      val avail   = (sizeWritten - sizeQueried).toInt
      hasQueried  = avail >= vecRemain || avail >= SLICE_SIZE
//      LOG(s"tryQuery(); avail = $avail; hasQueried $hasQueried")
      if (hasQueried) {
        getOff1 = (sizeQueried % c.bufSize).toInt
        val bufRem  = c.bufSize - getOff1
        val tmpLen  = Math.min(vecRemain, SLICE_SIZE)
        val m = if (bufRem < tmpLen) {  // we hit the buffer end, use two queries
          getLen1 = bufRem
          getLen2 = tmpLen - bufRem
          BufferGetn(c.bufId, Range(getOff1, getOff1 + getLen1), Range(0, getLen2))

        } else {  // use one query
          getLen1 = tmpLen
          getLen2 = 0
          BufferGetn(c.bufId, Range(getOff1, getOff1 + getLen1))
        }

        c.server ! m
      }
    }

    private def invokeRepaint(): Unit = {
      val pnt = pntVector
      if (pnt != null) {
        val n   = Math.min(vecOff, pnt.length)
        val m   = pnt.length
//        LOG(s"invokeRepaint n $n m $m")
        pnt.synchronized {
          System.arraycopy(vector, 0, pnt, 0, n)
          if (n < m) {
            util.Arrays.fill(pnt, n, m, 0f)
          }
        }
        repaint() // paint complete waveform
        getToolkit.sync()
      }
    }

    private def bufSetN(b: BufferSetn): Unit = {
      val pairs     = b.indicesAndValues
      val numPairs  = pairs.size
//      val OLD_OFF = vecOff
      val vec       = vector
      var j         = vecOff
      var pi = 0
      while (pi < numPairs) {
        val sq    = pairs(pi)._2
        val sqLen = Math.min(sq.length, vecRemain)

        var i = 0
        while (i < sqLen) {
          vec(j) = sq(i) // XXX TODO --- or is toArray faster?
          i += 1
          j += 1
        }
        vecRemain   -= sqLen
        sizeQueried += sqLen

        pi += 1
      }
      vecOff = j
//      LOG(s"bufSetN ${self.hashCode().toHexString} vecOff $OLD_OFF -> $vecOff")
      if (vecRemain == 0) {
        invokeRepaint()
        newVector()

      } else {
        tryQuery()
      }
    }

    private def trig(t: Int): Unit = {
      sizeWritten = t.toLong * c.trigSize
//      LOG(s"trig($t); sizeWritten = $sizeWritten; hasQueried $hasQueried")
      if (!hasQueried) {
        if (initialized) {
          tryQuery()
        } else {
          initialized = true
//          LOG(  s"INIT polySizeC $polySizeC, useSize ${c.useSize}")
          newVector()
        }
      }
    }

//    private def LOG(what: => String): Unit = ()

    def apply(m: Message): Unit = m match {
      case b: BufferSetn  => bufSetN(b) // we already checked the guard in `isDefinedAt`
      case t: Trigger     => trig(t.value.toInt - 1)
    }
  }

  //  private[this] val timer  = {
  //    val res = new Timer(1000, this)
  //    res.setRepeats(true)
  //    res
  //  }

  def start(): Unit = {
    require (EventQueue.isDispatchThread)
    if (! _isRunning ) {
      try {
        if (_config.nonEmpty) {
          runState()
        }
        _isRunning = true

      } catch {
        case e1: IOException =>
          _isRunning = false
          System.out.println(e1)
      }
    }
  }

  private def runState(): Unit = {
    assert (_state == null)
    _state = new State(_config)
    _state.start()
  }

  def stop(): Unit = {
    require (EventQueue.isDispatchThread)
    if (_isRunning) {
      if (_state != null) {
        _state.dispose()
        _state = null
      }
      _isRunning = false
    }
  }

  def dispose(): Unit = {
    stop()
    assert (_state == null)
    _config     = Config.Empty
    pntVector   = null
  }

  def isRunning: Boolean = _isRunning

  def config_=(value: Config): Unit = {
    _config = value
    if (_state != null) {
      _state.dispose()
      _state = null
    }

    pntVector     = new Array[Float](value.useSize)
    val polySize  = value.useFrames << 1
    polyX         = new Array[Int](polySize)
    polyY         = new Array[Int](polySize)
    recentWidth   = -1 // need to recalculate X coordinates

    if (_isRunning) {
      if (value.nonEmpty) {
        runState()
      } else {
        repaint()
      }
    }
  }

  def config: Config = _config

  def overlayPainter: ScopeViewOverlayPainter = _overlayPnt

  def overlayPainter_=(value: ScopeViewOverlayPainter): Unit = {
    _overlayPnt = value
    repaint()
  }

  def style_=(value: Int): Unit = {
    val wasLissa  = _style == STYLE_LISSAJOUS
    _style        = value
    _overlay      = value >= STYLE_OVERLAY
    _lissajous    = value == STYLE_LISSAJOUS
    if (wasLissa) {
      if (!_lissajous) {
        _xZoom = _xZoomNorm
      }
    } else {
      if (_lissajous) {
        _xZoom = if (_xZoomLissa > 0f) _xZoomLissa else _yZoom
      }
    }
    recentWidth = -1
    repaint()
  }

  def style: Int = _style

  def xZoom_=(value: Float): Unit = {
    _xZoom = value
    if (_lissajous) _xZoomLissa = value else _xZoomNorm = value
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

  def waveColors: ISeq[Color] = _waveColors.toList

  def screenColor: Color = getBackground

  def screenColor_=(value: Color): Unit = setBackground(value)

  //  var DUMP = false

  override def paintComponent(g: Graphics): Unit = {
    super.paintComponent(g)
    val g2          = g.asInstanceOf[Graphics2D]
    val w           = getWidth
    val h           = getHeight
    val atOrig      = g2.getTransform
    val strokeOrig  = g2.getStroke
    var x           = 0
    var y           = 0
    g2.setColor(getBackground)
    g2.fillRect(0, 0, w, h)

    val c = config
    val numChannels = c.numChannels
    val pnt = pntVector
    if ((pnt == null) || (numChannels == 0)) return
    val numFrames   = c.useFrames
    val _polyX      = polyX
    val _polyY      = polyY
    var _polySize   = polySize

    pnt.synchronized {
      if (_lissajous) {
        // the interpretation of the xZoom value is completely
        // stupid in cocoa scope. in lissajous mode increasing zoom
        // will increase scale, while in normal mode it's the other way round
        val offX  = w << 1
        val sx    = offX * _xZoom
        //      polySize  = numFrames
        _polySize = Math.min(numFrames, w * 4)
        polySize  = _polySize
        polySizeC = _polySize * numChannels

        var i  = 0
        var k  = 0
        while (i < _polySize) {
          x = (pnt(k) * sx + offX).toInt
          _polyX(i) = x

          i += 1
          k += numChannels
        }
        recentWidth = w

      } else if (w != recentWidth) { // have to recalculate horizontal coordinate
        _polySize  = Math.min(numFrames, (w * _xZoom).toInt + 1)
        polySize  = _polySize
        polySizeC = _polySize * numChannels
        val sx    = 4 / _xZoom
        sah       = sx > 12

        //        if (DUMP) {
        //          println(s"polySize $polySize, polySizeC $polySizeC, sah $sah, _xZoom ${_xZoom}, sx $sx")
        //          //        DUMP = false
        //        }

        if (sah) {
          x = 0
          var i  = 0
          var j  = 0
          while (i < _polySize) {
            _polyX(j) = x
            j += 1
            x = (i * sx).toInt
            _polyX(j) = x
            j += 1
            i += 1
          }
        } else {
          var i = 0
          while (i < _polySize) {
            x = (i * sx).toInt
            _polyX(i) = x
            i += 1
          }
        }

        //        if (DUMP) {
        //          println(s"now x is $x")
        //        }

        recentWidth = w
      }

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      // note: the integer divisions for `hCh` and `offY` are so that
      // the zero position aligns with the y-Axis views in JScopePanel
      val numChEff  = if (_overlay || _lissajous) 1 else numChannels
      val hCh       = h / numChEff
      val sy        = -(h << 1) * _yZoom / numChEff
      var offY      = -hCh/2
      val numChDraw = if (_lissajous) 1 else numChannels
      val yChOff    = if (_lissajous) 1 else 0

      var ch = 0
      while (ch < numChDraw) {
        g2.setColor(if (_waveColors.length > ch) _waveColors(ch) else colorFg)
        g2.setStroke(strokePoly)
        if (ch < numChEff) offY += hCh
        g2.translate(0, offY)
        g2.scale(0.25f, 0.25f)
        if (sah) { // sample-and-hold
          var i  = 0
          var j  = 0
          var k  = ch
          while (i < _polySize) {
            y = (pnt(k) * sy).toInt
            _polyY(j) = y
            j += 1
            _polyY(j) = y
            j += 1
            i += 1
            k += numChannels
          }
          //        val n = polySize << 1
          //        if (DUMP) {
          //          println(s"polyX.last = ${polyX(n - 1)}, n = $n")
          //        }
          g2.drawPolyline(_polyX, _polyY, _polySize << 1)
          //        g2.setColor(Color.red)
          //        g2.drawLine(0, 0, polyX(n - 1), h)
        }
        else {
          var i  = 0
          var k  = ch + yChOff
          while (i < _polySize) {
            y = (pnt(k) * sy).toInt
            _polyY(i) = y

            i += 1
            k += numChannels
          }
          g2.drawPolyline(_polyX, _polyY, _polySize)
          //        g2.setColor(Color.blue)
          //        g2.drawLine(0, h, polyX(polySize - 1), 0)
        }
        g2.setTransform(atOrig)
        g2.setStroke(strokeOrig)

        ch += 1
      }
    }

    if (_overlayPnt != null) _overlayPnt.paintScopeOverlay(g2, w, h)
  }
}