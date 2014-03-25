/*
 *  Plotting.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.swing

import scalax.chart.Charting._
import java.awt.{BasicStroke, Color}
// import de.sciss.pdflitz
import org.jfree.chart.{ChartFactory, ChartPanel}
import scala.swing.{Component, Frame}
import javax.swing.WindowConstants
import org.jfree.data.xy.{XYSeries, XYSeriesCollection}
import collection.immutable.{Seq => ISeq}
import org.jfree.chart.plot.PlotOrientation

object Plotting {
  type Plot = Unit  // XXX TODO

  private val strokes = {
    import BasicStroke._
    Vector(
      new BasicStroke(1.5f, CAP_SQUARE, JOIN_MITER, 10.0f, null, 0.0f),
      new BasicStroke(1.5f, CAP_BUTT  , JOIN_MITER,  1.0f, Array(6f, 6f), 0f),
      new BasicStroke(1.5f, CAP_BUTT  , JOIN_MITER, 10.0f, Array(2f, 2f), 0f),
      new BasicStroke(1.5f, CAP_BUTT  , JOIN_MITER, 10.0f, Array(6f, 2f, 2f, 2f), 0f)
    )
  }

  implicit class Plot1D[A](sq: ISeq[A]) {
    def plot(legend: String = "", title: String = "Data", ylabel: String = "")(implicit num: A => Number): Plot = {
      val series = sq.zipWithIndex.map(_.swap).toXYSeries(name = legend)
      plotXY(series :: Nil, legends = if (legend == "") Nil else legend :: Nil,
        title = title, xlabel = "", ylabel = ylabel)
    }
  }

  implicit class Plot2D[A, B](it: Iterable[(A, B)]) {
    def plot(legend: String = "", title: String = "Data", xlabel: String = "", ylabel: String = "")
            (implicit numA: A => Number, numB: B => Number): Plot = {
      val series = it.toXYSeries(name = legend)
      plotXY(series :: Nil, legends = if (legend == "") Nil else legend :: Nil,
        title = title, xlabel = xlabel, ylabel = ylabel)
    }
  }

  implicit class MultiPlot1D[A](sqs: ISeq[ISeq[A]]) {
    def plot(legends: ISeq[String] = Nil, title: String = "Data", ylabel: String = "")(implicit num: A => Number): Plot = {
      val series = (sqs zip legends).map { case (sq, legend) =>
        sq.zipWithIndex.map(_.swap).toXYSeries(name = legend)
      }
      plotXY(series = series, legends = legends, title = title, xlabel = "", ylabel = ylabel)
    }
  }

  private def plotXY(series: ISeq[XYSeries], legends: ISeq[String],
                     title: String, xlabel: String, ylabel: String): Unit = {
    // val sz = datasets.size

    val dataset = new XYSeriesCollection
    series.foreach(dataset.addSeries)

    val chart = ChartFactory.createXYLineChart(
      if (title == "") null else title,
      if (xlabel == "") null else xlabel,
      if (ylabel == "") null else ylabel,
      dataset,
      PlotOrientation.VERTICAL,
      legends.nonEmpty, // legend
      false,  // tooltips
      false   // urls
    )
    val plot      = chart.getXYPlot
    val renderer  = plot.getRenderer
    // renderer.setBasePaint(Color.black)
    // renderer.setBaseOutlinePaint(Color.black)
    series.zipWithIndex.foreach { case (s, i) =>
      // plot.setDataset(i, dataset)
      // val renderer  = plot.getRendererForDataset(dataset)
      renderer.setSeriesPaint (i, Color.black) // if (i == 0) Color.black else Color.red)
      renderer.setSeriesStroke(i, strokes(i % strokes.size))
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
      // peer.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
      // new pdflitz.SaveAction(panel :: Nil).setupMenu(this)
      pack()
      centerOnScreen()
    }
    fr.open()
    // val f = chart.toFrame(title = title)
  }
}