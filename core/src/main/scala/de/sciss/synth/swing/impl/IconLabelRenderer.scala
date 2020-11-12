/*
 *  IconLabelRenderer.scala
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

package de.sciss.synth.swing.impl

import java.awt.{Dimension, Font, FontMetrics, Graphics2D, Shape}
import java.awt.geom.{Point2D, Rectangle2D, RectangularShape, RoundRectangle2D}
import javax.swing.Icon

import prefuse.Constants
import prefuse.render.{AbstractShapeRenderer, Renderer}
import prefuse.util.{ColorLib, FontLib, GraphicsLib, StringLib}
import prefuse.visual.VisualItem

import scala.annotation.switch

/**
 * Renderer that draws a label, which consists of a text string,
 * an icon, or both. This is based on the prefuse `LabelRenderer`
 * class, but changes image for icon. It was converted from Java to Scala,
 * so the code looks a bit ugly.
 *
 * <p>When created using the default constructor, the renderer attempts
 * to use text from the "label" field. To use a different field, use the
 * appropriate constructor or use the `setTextField` method.
 * To perform custom String selection, subclass this Renderer and override the 
 * `getText` method. When the text field is
 * <code>null</code>, no text label will be shown. Labels can span multiple
 * lines of text, determined by the presence of newline characters ('\n')
 * within the text string.</p>
 *
 * <p>By default, no icon is shown. To show an icon, the icon field needs
 * to be set, either using the appropriate constructor or the
 * `setIconField` method. The value of the icon field should
 * be an `Icon` instance to use.
 *
 * <p>The position of the icon relative to text can be set using the
 * `setIconPosition` method. Images can be placed to the
 * left, right, above, or below the text. The horizontal and vertical
 * alignments of either the text or the icon can be set explicitly
 * using the appropriate methods of this class (e.g.,
 * `setHorizontalTextAlignment`). By default, both the
 * text and icon are centered along both the horizontal and
 * vertical directions.</p>
 *
 * @author <a href="http://jheer.org">jeffrey heer</a>
 * @author Hanns Holger Rutz
 */
class IconLabelRenderer() extends AbstractShapeRenderer {
  /**
   * Helper method, which calculates the top-left co-ordinate of an item
   * given the item's alignment.
   */
  protected def mkAlignedPoint(p: Point2D, item: VisualItem, w: Double, h: Double, xAlign: Int, yAlign: Int): Unit = {
    var x: Double = item.getX
    var y: Double = item.getY
    if (java.lang.Double.isNaN(x) || java.lang.Double.isInfinite(x)) x = 0
    if (java.lang.Double.isNaN(y) || java.lang.Double.isInfinite(y)) y = 0
    if (xAlign == Constants.CENTER) {
      x = x - (w / 2)
    }
    else if (xAlign == Constants.RIGHT) {
      x = x - w
    }
    if (yAlign == Constants.CENTER) {
      y = y - (h / 2)
    }
    else if (yAlign == Constants.BOTTOM) {
      y = y - h
    }
    p.setLocation(x, y)
  }

  /** Creates a new LabelRenderer. Draws a text label using the given
    * text data field and does not draw an icon.
    * @param textField the data field for the text label.
    */
  def this(textField: String) = {
    this()
    this.textField = textField
  }

  /** Creates a new LabelRenderer. Draws a text label using the given text
    * data field, and draws the icon at the location reported by the
    * given icon data field.
    * @param textField the data field for the text label
    * @param iconField the data field for the icon location. This value
    *                  in the data field should be an `Icon`, or null for no icon. If the
    *                  `iconField` parameter is null, no icon at all will be drawn.
    */
  def this(textField: String, iconField: String) = {
    this()
    this.textField = textField
    iconField_=(iconField)
  }

  /** Rounds the corners of the bounding rectangle in which the text
    * string is rendered. This will only be seen if either the stroke
    * or fill color is non-transparent.
    * @param arcWidth the width of the curved corner
    * @param arcHeight the height of the curved corner
    */
  def setRoundedCorner(arcWidth: Int, arcHeight: Int): Unit = {
    if ((arcWidth == 0 || arcHeight == 0) && !m_bBox.isInstanceOf[Rectangle2D]) {
      m_bBox = new Rectangle2D.Double
    }
    else {
      if (!m_bBox.isInstanceOf[RoundRectangle2D]) m_bBox = new RoundRectangle2D.Double
      m_bBox.asInstanceOf[RoundRectangle2D].setRoundRect(0, 0, 10, 10, arcWidth, arcHeight)
      m_arcWidth  = arcWidth
      m_arcHeight = arcHeight
    }
  }

  /** Gets the field name to use for text labels.
    * @return the data field for text labels, or null for no text
    */
  def textField: String = m_labelName

  /** Sets the field name to use for text labels.
    * @param value the data field for text labels, or null for no text
    */
  def textField_=(value: String): Unit = m_labelName = value

  def maximumTextWidth: Int = m_maxTextWidth

  /** Sets the maximum width that should be allowed of the text label.
    * A value of -1 specifies no limit (this is the default).
    * @param value the maximum width of the text or -1 for no limit
    */
  def maximumTextWidth_=(value: Int): Unit = m_maxTextWidth = value

  /** Returns the text to draw. Subclasses can override this class to
    * perform custom text selection.
    * @param item the item to represent as a <code>String</code>
    * @return a <code>String</code> to draw
    */
  protected def getText(item: VisualItem): String =
    if (item.canGetString(m_labelName))
      item.getString(m_labelName)
    else null

  /** Gets the data field for icon locations. The value stored
    * in the data field should be an `Icon`, or null for no icon.
    *
    * @return the data field for icon locations, or null for no icon
    */
  def iconField: String = m_iconName

  /** Sets the data field for icon locations. The value stored
    * in the data field should be an `Icon`, or null for no icon. If the
    * <code>iconField</code> parameter is null, no icons at all will be
    * drawn.
    *
    * @param value the data field for icon locations, or null for
    *              no icons
    */
  def iconField_=(value: String): Unit = m_iconName = value

  /** Returns the icon to draw. Subclasses can override 
    * this class to perform custom icon selection beyond looking up the value
    * from a data field.
    * @param item the item for which to select an icon to draw
    * @return the icon to use, or null for no icon
    */
  protected def getIcon(item: VisualItem): Icon =
    if (item.canGet(m_iconName, classOf[Icon])) item.get(m_iconName).asInstanceOf[Icon] else null

  private def computeTextDimensions(item: VisualItem, text: String, size: Double): String = {
    m_font = item.getFont
    if (size != 1) {
      m_font = FontLib.getFont(m_font.getName, m_font.getStyle, size * m_font.getSize)
    }
    val fm: FontMetrics = Renderer.DEFAULT_GRAPHICS.getFontMetrics(m_font)
    var str: StringBuffer = null
    var nlines: Int = 1
    var w: Int = 0
    var start: Int = 0
    var end: Int = text.indexOf(m_delim)
    m_textDim.width = 0
    var line: String = null
    while (end >= 0) {
      line = text.substring(start, end)
      w = fm.stringWidth(line)
      if (m_maxTextWidth > -1 && w > m_maxTextWidth) {
        if (str == null) str = new StringBuffer(text.substring(0, start))
        str.append(StringLib.abbreviate(line, fm, m_maxTextWidth))
        str.append(m_delim)
        w = m_maxTextWidth
      }
      else if (str != null) {
        str.append(line).append(m_delim)
      }
      m_textDim.width = math.max(m_textDim.width, w)
      start = end + 1
      end = text.indexOf(m_delim, start)

      nlines += 1
    }
    line = text.substring(start)
    w = fm.stringWidth(line)
    if (m_maxTextWidth > -1 && w > m_maxTextWidth) {
      if (str == null) str = new StringBuffer(text.substring(0, start))
      str.append(StringLib.abbreviate(line, fm, m_maxTextWidth))
      w = m_maxTextWidth
    }
    else if (str != null) {
      str.append(line)
    }
    m_textDim.width = math.max(m_textDim.width, w)
    m_textDim.height = fm.getHeight * nlines
    if (str == null) text else str.toString
  }

  /**
   * @see prefuse.render.AbstractShapeRenderer#getRawShape(prefuse.visual.VisualItem)
   */
  protected def getRawShape(item: VisualItem): Shape = {
    m_text = getText(item)
    val icon: Icon = getIcon(item)
    val size: Double = item.getSize
    var iw: Double = 0
    var ih: Double = 0
    if (icon != null) {
      ih = icon.getIconHeight
      iw = icon.getIconWidth
    }
    var tw: Int = 0
    var th: Int = 0
    if (m_text != null) {
      m_text = computeTextDimensions(item, m_text, size)
      th = m_textDim.height
      tw = m_textDim.width
    }
    var w: Double = 0
    var h: Double = 0
    (m_iconPos: @switch) match {
      case Constants.LEFT | Constants.RIGHT =>
        w = tw + size * (iw + 2 * m_horizBorder + (if (tw > 0 && iw > 0) m_iconMargin else 0))
        h = math.max(th, size * ih) + size * 2 * m_vertBorder

      case Constants.TOP | Constants.BOTTOM =>
        w = math.max(tw, size * iw) + size * 2 * m_horizBorder
        h = th + size * (ih + 2 * m_vertBorder + (if (th > 0 && ih > 0) m_iconMargin else 0))

      case _ =>
        throw new IllegalStateException("Unrecognized icon alignment setting.")
    }
    mkAlignedPoint(m_pt, item, w, h, m_xAlign, m_yAlign)
    m_bBox match {
      case rr: RoundRectangle2D =>
        rr.setRoundRect(m_pt.getX, m_pt.getY, w, h, size * m_arcWidth, size * m_arcHeight)
      case _ =>
        m_bBox.setFrame(m_pt.getX, m_pt.getY, w, h)
    }
    m_bBox
  }

  /**
   * @see prefuse.render.Renderer#render(java.awt.Graphics2D, prefuse.visual.VisualItem)
   */
  override def render(g: Graphics2D, item: VisualItem): Unit = {
    val shape: RectangularShape = getShape(item).asInstanceOf[RectangularShape]
    if (shape == null) return

    val tpe: Int = getRenderType(item)
    if (tpe == AbstractShapeRenderer.RENDER_TYPE_FILL || tpe == AbstractShapeRenderer.RENDER_TYPE_DRAW_AND_FILL)
      GraphicsLib.paint(g, item, shape, getStroke(item), AbstractShapeRenderer.RENDER_TYPE_FILL)

    val text: String = m_text
    val icon: Icon = getIcon(item)
    if (text == null && icon == null) return
    val size: Double = item.getSize
    val useInt: Boolean = 1.5 > math.max(g.getTransform.getScaleX, g.getTransform.getScaleY)
    var x: Double = shape.getMinX + size * m_horizBorder
    var y: Double = shape.getMinY + size * m_vertBorder
    if (icon != null) {
      val w: Double = size * icon.getIconWidth
      val h: Double = size * icon.getIconHeight
      var ix: Double = x
      var iy: Double = y
      (m_iconPos: @switch) match {
        case Constants.LEFT =>
          x += w + size * m_iconMargin

        case Constants.RIGHT =>
          ix = shape.getMaxX - size * m_horizBorder - w

        case Constants.TOP =>
          y += h + size * m_iconMargin

        case Constants.BOTTOM =>
          iy = shape.getMaxY - size * m_vertBorder - h

        case _ =>
          throw new IllegalStateException("Unrecognized icon alignment setting.")
      }
      (m_iconPos: @switch) match {
        case Constants.LEFT | Constants.RIGHT =>
          m_vIconAlign match {
            case Constants.TOP =>
            case Constants.BOTTOM =>
              iy = shape.getMaxY - size * m_vertBorder - h
            case Constants.CENTER =>
              iy = shape.getCenterY - h / 2
          }

        case Constants.TOP | Constants.BOTTOM =>
          m_hIconAlign match {
            case Constants.LEFT =>
            case Constants.RIGHT =>
              ix = shape.getMaxX - size * m_horizBorder - w
            case Constants.CENTER =>
              ix = shape.getCenterX - w / 2
          }
      }
      if (useInt && size == 1.0) {
        // g.drawImage(icon, ix.asInstanceOf[Int], iy.asInstanceOf[Int], null)
        icon.paintIcon(null, g, ix.toInt, iy.toInt)
      }
      else {
        m_transform.setTransform(size, 0, 0, size, ix, iy)
        // g.drawImage(icon, m_transform, null)
        val atOrig = g.getTransform
        g.transform(m_transform)
        icon.paintIcon(null, g, 0, 0)
        g.setTransform(atOrig)
      }
    }
    val textColor: Int = item.getTextColor
    if (text != null && ColorLib.alpha(textColor) > 0) {
      g.setPaint(ColorLib.getColor(textColor))
      g.setFont(m_font)
      val fm: FontMetrics = Renderer.DEFAULT_GRAPHICS.getFontMetrics(m_font)
      var tw: Double = 0.0
      (m_iconPos: @switch) match {
        case Constants.TOP | Constants.BOTTOM =>
          tw = shape.getWidth - 2 * size * m_horizBorder
        case _ =>
          tw = m_textDim.width
      }
      var th: Double = 0.0
      (m_iconPos: @switch) match {
        case Constants.LEFT | Constants.RIGHT =>
          th = shape.getHeight - 2 * size * m_vertBorder
        case _ =>
          th = m_textDim.height
      }
      y += fm.getAscent
      (m_vTextAlign: @switch) match {
        case Constants.TOP =>
        case Constants.BOTTOM =>
          y += th - m_textDim.height
        case Constants.CENTER =>
          y += (th - m_textDim.height) / 2
      }
      val lh: Int = fm.getHeight
      var start: Int = 0
      var end: Int = text.indexOf(m_delim)
      while (end >= 0) {
        drawString(g, fm, text.substring(start, end), useInt, x, y, tw)
        start = end + 1
        end = text.indexOf(m_delim, start)
        y += lh
      }
      drawString(g, fm, text.substring(start), useInt, x, y, tw)
    }
    if (tpe == AbstractShapeRenderer.RENDER_TYPE_DRAW || tpe == AbstractShapeRenderer.RENDER_TYPE_DRAW_AND_FILL) {
      GraphicsLib.paint(g, item, shape, getStroke(item), AbstractShapeRenderer.RENDER_TYPE_DRAW)
    }
  }

  private final def drawString(g: Graphics2D, fm: FontMetrics, text: String, useInt: Boolean, 
                               x: Double, y: Double, w: Double): Unit = {
    var tx: Double = 0.0
    (m_hTextAlign: @switch) match {
      case Constants.LEFT =>
        tx = x
      case Constants.RIGHT =>
        tx = x + w - fm.stringWidth(text)
      case Constants.CENTER =>
        tx = x + (w - fm.stringWidth(text)) / 2
      case _ =>
        throw new IllegalStateException("Unrecognized text alignment setting.")
    }
    if (useInt) {
      g.drawString(text, tx.asInstanceOf[Int], y.asInstanceOf[Int])
    }
    else {
      g.drawString(text, tx.asInstanceOf[Float], y.asInstanceOf[Float])
    }
  }

  /** Gets the horizontal text alignment within the layout. One of
    * `Constants.LEFT`, `Constants.RIGHT`, or
    * `Constants.CENTER`. The default is centered text.
    * @return the horizontal text alignment
    */
  def horizontalTextAlignment: Int = m_hTextAlign

  /** Sets the horizontal text alignment within the layout. One of
    * `Constants.LEFT`, `Constants.RIGHT`, or
    * `Constants.CENTER`. The default is centered text.
    * @param value the desired horizontal text alignment
    */
  def horizontalTextAlignment_=(value: Int): Unit = {
    if (value != Constants.LEFT && value != Constants.RIGHT && value != Constants.CENTER)
      throw new IllegalArgumentException("Illegal horizontal text alignment value.")
    m_hTextAlign = value
  }

  /** Gets the vertical text alignment within the layout. One of
    * `Constants.TOP`, `Constants.BOTTOM`, or
    * `Constants.CENTER`. The default is centered text.
    * @return the vertical text alignment
    */
  def verticalTextAlignment: Int = m_vTextAlign

  /** Sets the vertical text alignment within the layout. One of
    * `Constants.TOP`, `Constants.BOTTOM`, or
    * `Constants.CENTER`. The default is centered text.
    * @param value the desired vertical text alignment
    */
  def verticalTextAlignment_=(value: Int): Unit = {
    if (value != Constants.TOP && value != Constants.BOTTOM && value != Constants.CENTER) 
      throw new IllegalArgumentException("Illegal vertical text alignment value.")
    m_vTextAlign = value
  }

  /** Gets the horizontal icon alignment within the layout. One of
    * `Constants.LEFT`, `Constants.RIGHT`, or
    * `Constants.CENTER`. The default is a centered icon.
    * @return the horizontal icon alignment
    */
  def horizontalIconAlignment: Int = m_hIconAlign

  /** Sets the horizontal icon alignment within the layout. One of
    * `Constants.LEFT`, `Constants.RIGHT`, or
    * `Constants.CENTER`. The default is a centered icon.
    * @param value the desired horizontal icon alignment
    */
  def horizontalIconAlignment_=(value: Int): Unit = {
    if (value != Constants.LEFT && value != Constants.RIGHT && value != Constants.CENTER) 
      throw new IllegalArgumentException("Illegal horizontal text alignment value.")
    m_hIconAlign = value
  }

  /** Gets the vertical icon alignment within the layout. One of
    * `Constants.TOP`, `Constants.BOTTOM`, or
    * `Constants.CENTER`. The default is a centered icon.
   * @return the vertical icon alignment
   */
  def verticalIconAlignment: Int = m_vIconAlign

  /** Sets the vertical icon alignment within the layout. One of
    * `Constants.TOP`, `Constants.BOTTOM`, or
    * `Constants.CENTER`. The default is a centered icon.
    * @param value the desired vertical icon alignment
    */
  def verticalIconAlignment_=(value: Int): Unit = {
    if (value != Constants.TOP && value != Constants.BOTTOM && value != Constants.CENTER)
      throw new IllegalArgumentException("Illegal vertical text alignment value.")
    m_vIconAlign = value
  }

  /** Gets the icon position, determining where the icon is placed with
    * respect to the text. One of `Constants.LEFT`,
    * `Constants.RIGHT`, `Constants.TOP`, or
    * `Constants.BOTTOM`.  The default is left.
    * @return the icon position
    */
  def iconPosition: Int = m_iconPos

  /** Sets the icon position, determining where the icon is placed with
    * respect to the text. One of `Constants.LEFT`,
    * `Constants.RIGHT`, `Constants.TOP`, or
    * `Constants.BOTTOM`.  The default is left.
    * @param value the desired icon position
    */
  def iconPosition_=(value: Int): Unit = {
    if (value != Constants.TOP && value != Constants.BOTTOM && value != Constants.LEFT && 
      value != Constants.RIGHT && value != Constants.CENTER) 
      throw new IllegalArgumentException("Illegal icon position value.")
    
    m_iconPos = value
  }

  /** Gets the horizontal alignment of this node with respect to its
    * x, y coordinates.
    * @return the horizontal alignment, one of
    *         { @link prefuse.Constants#LEFT}, { @link prefuse.Constants#RIGHT}, or
    *         { @link prefuse.Constants#CENTER}.
    */
  def horizontalAlignment: Int = m_xAlign

  /** Gets the vertical alignment of this node with respect to its
    * x, y coordinates.
    * @return the vertical alignment, one of
    *         { @link prefuse.Constants#TOP}, { @link prefuse.Constants#BOTTOM}, or
    *         { @link prefuse.Constants#CENTER}.
    */
  def verticalAlignment: Int = m_yAlign

  /** Sets the horizontal alignment of this node with respect to its
    * x, y coordinates.
    * @param value the horizontal alignment, one of
    *              { @link prefuse.Constants#LEFT}, { @link prefuse.Constants#RIGHT}, or
    *              { @link prefuse.Constants#CENTER}.
    */
  def horizontalAlignment_=(value: Int): Unit = 
    m_xAlign = value

  /** Sets the vertical alignment of this node with respect to its
    * x, y coordinates.
    * @param value the vertical alignment, one of
    *              { @link prefuse.Constants#TOP}, { @link prefuse.Constants#BOTTOM}, or
    *              { @link prefuse.Constants#CENTER}.
    */
  def verticalAlignment_=(value: Int): Unit =
    m_yAlign = value

  /** Returns the amount of padding in pixels between the content 
    * and the border of this item along the horizontal dimension.
    * @return the horizontal padding
    */
  def horizontalPadding: Int = m_horizBorder

  /** Sets the amount of padding in pixels between the content 
    * and the border of this item along the horizontal dimension.
    * @param value the horizontal padding to set
    */
  def horizontalPadding_=(value: Int): Unit =
    m_horizBorder = value

  /** Returns the amount of padding in pixels between the content 
    * and the border of this item along the vertical dimension.
    * @return the vertical padding
    */
  def verticalPadding: Int = m_vertBorder

  /** Sets the amount of padding in pixels between the content 
    * and the border of this item along the vertical dimension.
    * @param value the vertical padding
    */
  def verticalPadding_=(value: Int): Unit =
    m_vertBorder = value

  /** Gets the padding, in pixels, between an icon and text.
    * @return the padding between an icon and text
    */
  def iconTextPadding: Int = m_iconMargin

  /** Sets the padding, in pixels, between an icon and text.
    * @param value the padding to use between an icon and text
    */
  def iconTextPadding_=(value: Int): Unit = m_iconMargin = value

  protected var m_delim       : String = "\n"
  protected var m_labelName   : String = "label"
  protected var m_iconName    : String = _
  protected var m_xAlign      : Int = Constants.CENTER
  protected var m_yAlign      : Int = Constants.CENTER
  protected var m_hTextAlign  : Int = Constants.CENTER
  protected var m_vTextAlign  : Int = Constants.CENTER
  protected var m_hIconAlign  : Int = Constants.CENTER
  protected var m_vIconAlign  : Int = Constants.CENTER
  protected var m_iconPos     : Int = Constants.LEFT
  protected var m_horizBorder : Int = 2
  protected var m_vertBorder  : Int = 0
  protected var m_iconMargin  : Int = 2
  protected var m_arcWidth    : Int = 0
  protected var m_arcHeight   : Int = 0
  protected var m_maxTextWidth: Int = -1
  // /** Transform used to scale and position icons */
  // protected var m_transform: AffineTransform = new AffineTransform
  /** The holder for the currently computed bounding box */
  protected var m_bBox        : RectangularShape  = new Rectangle2D.Double
  protected var m_pt          : Point2D           = new Point2D.Double
  protected var m_font        : Font              = _
  protected var m_text        : String            = _
  protected var m_textDim     : Dimension         = new Dimension
}

