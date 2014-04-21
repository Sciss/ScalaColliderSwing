/*
 *  Plotting.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.swing

import scalax.chart.api._
import java.awt.{BasicStroke, Color}
import java.awt.geom.{AffineTransform, Rectangle2D, Ellipse2D}
import de.sciss.pdflitz
import org.jfree.chart.{ChartFactory, ChartPanel}
import scala.swing.{Action, Component, Frame}
import org.jfree.data.xy.{XYSeries, XYSeriesCollection}
import collection.immutable.{Seq => ISeq}
import org.jfree.chart.plot.PlotOrientation
import javax.swing.JMenu

private[swing] trait PlottingLowPri {
  _: Plotting.type =>

  implicit class Plot1D[A](sq: ISeq[A]) {
    def plot(legend: String = "", title: String = "Data", ylabel: String = "", discrete: Boolean = false)
            (implicit num: Numeric[A]): Plot = {
      val series = sq.zipWithIndex.map(_.swap).toXYSeries(name = legend)
      val tpe = if (discrete) TypeStep else TypeLine
      plotXY(series :: Nil, legends = if (legend == "") Nil else legend :: Nil,
        title = title, xlabel = "", ylabel = ylabel, tpe = tpe)
    }
  }
}

object Plotting extends PlottingLowPri{
  type Plot = Unit  // XXX TODO

  protected sealed trait Type
  protected case object TypeLine    extends Type
  protected case object TypeStep    extends Type
  protected case object TypeScatter extends Type

  private val strokes = {
    import BasicStroke._
    Vector(
      new BasicStroke(1.5f, CAP_SQUARE, JOIN_MITER, 10.0f, null, 0.0f),
      new BasicStroke(1.5f, CAP_BUTT  , JOIN_MITER,  1.0f, Array(6f, 6f), 0f),
      new BasicStroke(1.5f, CAP_BUTT  , JOIN_MITER, 10.0f, Array(2f, 2f), 0f),
      new BasicStroke(1.5f, CAP_BUTT  , JOIN_MITER, 10.0f, Array(6f, 2f, 2f, 2f), 0f)
    )
  }

  private val shapes = {
    val rsc     = math.Pi * 0.25
    val rect    = new Rectangle2D.Double(-2 * rsc, -2 * rsc, 4 * rsc, 4 * rsc)
    val rhombus = AffineTransform.getRotateInstance(45 * math.Pi / 180).createTransformedShape(rect)
    Vector(
      new Ellipse2D.Double(-2, -2, 4, 4),
      rect,
      rhombus
    )
  }

  implicit class Plot2D[A, B](it: Iterable[(A, B)]) {
    def plot(legend: String = "", title: String = "Data", xlabel: String = "", ylabel: String = "",
             scatter: Boolean = true)
            (implicit numA: Numeric[A], numB: Numeric[B]): Plot = {
      val series = it.toXYSeries(name = legend)
      val tpe = if (scatter) TypeScatter else TypeLine
      plotXY(series :: Nil, legends = if (legend == "") Nil else legend :: Nil,
        title = title, xlabel = xlabel, ylabel = ylabel, tpe = tpe)
    }
  }

  implicit class MultiPlot1D[A](sqs: ISeq[ISeq[A]]) {
    def plot(legends: ISeq[String] = Nil, title: String = "Data", ylabel: String = "", discrete: Boolean = false)
            (implicit num: Numeric[A]): Plot = {
      val ssz = sqs    .size
      val lsz = legends.size
      val li  = if (lsz >= ssz) legends else legends ++ (0 until (ssz - lsz)).map(i => (i + 65).toChar.toString)
      val series = (sqs zip li).map { case (sq, legend) =>
        sq.zipWithIndex.map(_.swap).toXYSeries(name = legend)
      }
      val tpe = if (discrete) TypeStep else TypeLine
      plotXY(series = series, legends = legends, title = title, xlabel = "", ylabel = ylabel, tpe = tpe)
    }
  }

  protected def plotXY(series: ISeq[XYSeries], legends: ISeq[String],
                     title: String, xlabel: String, ylabel: String, tpe: Type): Unit = {
    // val sz = datasets.size

    val dataset = new XYSeriesCollection
    series.foreach(dataset.addSeries)

    val chart = tpe match {
      case TypeStep =>
        ChartFactory.createXYStepChart(
          if (title  == "") null else title,
          if (xlabel == "") null else xlabel,
          if (ylabel == "") null else ylabel,
          dataset,
          PlotOrientation.VERTICAL,
          legends.nonEmpty, // legend
          false, // tooltips
          false // urls
        )

      case TypeLine =>
        ChartFactory.createXYLineChart(
          if (title  == "") null else title,
          if (xlabel == "") null else xlabel,
          if (ylabel == "") null else ylabel,
          dataset,
          PlotOrientation.VERTICAL,
          legends.nonEmpty, // legend
          false, // tooltips
          false // urls
        )

      case TypeScatter =>
        ChartFactory.createScatterPlot(
          if (title  == "") null else title,
          if (xlabel == "") null else xlabel,
          if (ylabel == "") null else ylabel,
          dataset,
          PlotOrientation.VERTICAL,
          legends.nonEmpty, // legend
          false, // tooltips
          false // urls
        )
    }

    val plot      = chart.getXYPlot
    val renderer  = plot.getRenderer
    // renderer.setBasePaint(Color.black)
    // renderer.setBaseOutlinePaint(Color.black)
    series.zipWithIndex.foreach { case (s, i) =>
      // plot.setDataset(i, dataset)
      // val renderer  = plot.getRendererForDataset(dataset)
      renderer.setSeriesPaint (i, Color.black) // if (i == 0) Color.black else Color.red)
      renderer.setSeriesStroke(i, strokes(i % strokes.size))
      renderer.setSeriesShape (i, shapes (i % shapes .size))
    }

    plot.setBackgroundPaint    (Color.white)
    plot.setDomainGridlinePaint(Color.gray )
    plot.setRangeGridlinePaint (Color.gray )

    val panel = new ChartPanel(chart, false)
    panel.setBackground(Color.white)

    val _title = title
    val fr = new Frame {
      title     = _title
      contents  = Component.wrap(panel)
      pack()
      centerOnScreen()
    }
    if (GUI.windowOnTop) fr.peer.setAlwaysOnTop(true)

    panel.getPopupMenu.getComponents.collectFirst {
      case m: JMenu if m.getText.toLowerCase.startsWith("save as") => m
    } .foreach { m =>
      val pdfAction = pdflitz.SaveAction(panel :: Nil)
      m.add(Action("PDF...") {
        // file dialog is hidden if plot window is always on top!
        fr.peer.setAlwaysOnTop(false)
        pdfAction()
        if (GUI.windowOnTop) fr.peer.setAlwaysOnTop(true)
      }.peer)
    }

    fr.open()
  }
}