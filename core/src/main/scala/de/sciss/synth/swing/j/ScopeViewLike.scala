/*
 *  ScopeViewLike.scala
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

import java.awt.{Color, Graphics2D}

import scala.collection.immutable.{Seq => ISeq}

trait ScopeViewOverlayPainter {
  def paintScopeOverlay(g: Graphics2D, width: Int, height: Int): Unit
}

/** An oscilloscope canvas component.
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

  def start(): Unit
  def stop (): Unit

  def dispose(): Unit

  def isRunning: Boolean
}
