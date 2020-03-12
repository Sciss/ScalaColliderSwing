/*
 *  Plotting.scala
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

package de.sciss.synth.swing

import java.awt.geom.{AffineTransform, Ellipse2D, Point2D, Rectangle2D}
import java.awt.{BasicStroke, Color}

import de.sciss.chart.Chart
import de.sciss.chart.api._
import de.sciss.chart.event.{ChartMouseClicked, ChartMouseMoved}
import de.sciss.pdflitz
import javax.swing.JMenu
import org.jfree.chart.plot.PlotOrientation
import org.jfree.chart.{ChartFactory, ChartMouseEvent, ChartMouseListener, ChartPanel}
import org.jfree.data.xy.{XYSeries, XYSeriesCollection}

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.event.{MouseClicked, MouseMoved}
import scala.swing.{Action, Component, Frame, Point}

object Plotting {
  // var windowOnTop = false

  private sealed trait Type
  private case object TypeLine    extends Type
  private case object TypeStep    extends Type
  private case object TypeScatter extends Type

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

  trait PlottingLowPri {
    // _: Plotting.type =>

    implicit class Plot1D[A](sq: ISeq[A]) {
      def plot(legend: String = "", title: String = "Data", ylabel: String = "", discrete: Boolean = false,
               frame: Boolean = true)
              (implicit num: Numeric[A]): Plot = {
        val series = sq.zipWithIndex.map(_.swap).toXYSeries(name = legend)
        val tpe = if (discrete) TypeStep else TypeLine
        plotXY(series :: Nil, legends = if (legend == "") Nil else legend :: Nil,
          title = title, xlabel = "", ylabel = ylabel, tpe = tpe, frame = frame)
      }
    }
  }

  object Implicits extends PlottingLowPri {

    implicit class Plot2D[A, B](it: Iterable[(A, B)]) {
      def plot(legend: String = "", title: String = "Data", xlabel: String = "", ylabel: String = "",
               scatter: Boolean = true, frame: Boolean = true)
              (implicit numA: Numeric[A], numB: Numeric[B]): Plot = {
        val series = it.toXYSeries(name = legend)
        val tpe = if (scatter) TypeScatter else TypeLine
        plotXY(series :: Nil, legends = if (legend == "") Nil else legend :: Nil,
          title = title, xlabel = xlabel, ylabel = ylabel, tpe = tpe, frame = frame)
      }
    }

    implicit class MultiPlot1D[A](sqs: ISeq[ISeq[A]]) {
      def plot(legends: ISeq[String] = Nil, title: String = "Data", ylabel: String = "",
               discrete: Boolean = false, frame: Boolean = true)
              (implicit num: Numeric[A]): Plot = {
        val ssz = sqs    .size
        val lsz = legends.size
        val li  = if (lsz >= ssz) legends else legends ++ (0 until (ssz - lsz)).map(i => (i + 65).toChar.toString)
        val series = (sqs zip li).map { case (sq, legend) =>
          sq.zipWithIndex.map(_.swap).toXYSeries(name = legend)
        }
        val tpe = if (discrete) TypeStep else TypeLine
        plotXY(series = series, legends = legends, title = title, xlabel = "", ylabel = ylabel, tpe = tpe,
          frame = frame)
      }
    }
  }

  protected def plotXY(series: ISeq[XYSeries], legends: ISeq[String],
                     title: String, xlabel: String, ylabel: String, tpe: Type, frame: Boolean): Plot = {
    // val sz = datasets.size

    val dataset = new XYSeriesCollection
    series.foreach(dataset.addSeries)

    val _chartJ = tpe match {
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
    val _chart = Chart.fromPeer(_chartJ)

    val plot      = _chartJ.getXYPlot
    val renderer  = plot.getRenderer
    // renderer.setBasePaint(Color.black)
    // renderer.setBaseOutlinePaint(Color.black)
    series.zipWithIndex.foreach { case (_, i) =>
      // plot.setDataset(i, dataset)
      // val renderer  = plot.getRendererForDataset(dataset)
      renderer.setSeriesPaint (i, Color.black) // if (i == 0) Color.black else Color.red)
      renderer.setSeriesStroke(i, strokes(i % strokes.size))
      renderer.setSeriesShape (i, shapes (i % shapes .size))
    }

    plot.setBackgroundPaint    (Color.white)
    plot.setDomainGridlinePaint(Color.gray )
    plot.setRangeGridlinePaint (Color.gray )

    val _panelJ = new ChartPanel(_chartJ, false)
    _panelJ.setBackground(Color.white)

    // recover some of the private functionality of scala-chart,
    // while preserving our non-buffered panel creation.
    // also publish axis translated values
    val _panel = Component.wrap(_panelJ)
    _panelJ.addChartMouseListener(new ChartMouseListener {
      override final def chartMouseClicked(event: ChartMouseEvent): Unit =
        _panel.publish(ChartMouseClicked(new MouseClicked(event.getTrigger), Option(event.getEntity)))
      override final def chartMouseMoved(event: ChartMouseEvent): Unit =
        _panel.publish(ChartMouseMoved(new MouseMoved(event.getTrigger), Option(event.getEntity)))
    })

    val _title = title

    val __frame = if (frame) {
      val _frame = new Frame {
        title = _title
        contents = Component.wrap(_panelJ)
        pack()
        centerOnScreen()
      }
      if (GUI.windowOnTop) _frame.peer.setAlwaysOnTop(true)

      _panelJ.getPopupMenu.getComponents.collectFirst {
        case m: JMenu if m.getText.toLowerCase.startsWith("save as") => m
      }.foreach { m =>
        val pdfAction = pdflitz.SaveAction(_panelJ :: Nil)
        m.add(Action("PDF...") {
          // file dialog is hidden if plot window is always on top!
          _frame.peer.setAlwaysOnTop(false)
          pdfAction()
          if (GUI.windowOnTop) _frame.peer.setAlwaysOnTop(true)
        }.peer)
      }

      _frame.open()
      _frame
    } else null

    val res: Plot = new Plot {
      override def toString = s"Plot($title)@${hashCode().toHexString}"

      def frame     : Frame     = if (__frame != null) __frame else sys.error("Plot was defined without frame")
      val chart     : Chart     = _chart
      val component : Component = _panel
    }

    _panel.listenTo(_panel)
    _panel.reactions += {
      case ChartMouseClicked(trig, _) =>
        // println("clicked") // s"clicked x=$chartX, y = $chartY")
        res.publish(Plot.Clicked(res, trig, mkChartPoint(plot, _panelJ, trig.point)))

      case ChartMouseMoved(trig, _) =>
        // println("moved")
        res.publish(Plot.Moved(res, trig, mkChartPoint(plot, _panelJ, trig.point)))
    }

    res
  }

  // cf. http://stackoverflow.com/questions/1512112/jfreechart-get-mouse-coordinates
  private def mkChartPoint(plot: XYPlot, panel: ChartPanel, screen: Point): Point2D = {
    // take note of the comment in the answer
    val p2d       = screen // panel.translateScreenToJava2D(screen)
    val plotArea  = panel.getScreenDataArea
    val chartX    = plot.getDomainAxis.java2DToValue(p2d.getX, plotArea, plot.getDomainAxisEdge)
    val chartY    = plot.getRangeAxis .java2DToValue(p2d.getY, plotArea, plot.getRangeAxisEdge )
    new Point2D.Double(chartX, chartY)
  }
}