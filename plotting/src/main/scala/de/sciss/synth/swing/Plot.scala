package de.sciss.synth.swing

import scala.swing.{Publisher, Component, Frame}
import scalax.chart.Chart
import scala.swing.event.{MouseMoved, MouseEvent, MouseClicked}
import java.awt.geom.Point2D

object Plot {
  //  object Source {
  //    implicit def fromIntSeq1D   (seq: Seq[Int   ]): Source = new Dummy
  //    implicit def fromFloatSeq1D (seq: Seq[Float ]): Source = new Dummy
  //    implicit def fromDoubleSeq1D(seq: Seq[Double]): Source = new Dummy
  //
  //    private class Dummy extends Source
  //  }
  //  sealed trait Source
  //
  //  def apply(source: Source): Unit = println("Ok")

  sealed trait Event extends scala.swing.event.Event {
    def plot : Plot
    def event: MouseEvent
    def point: Point2D
  }
  case class Clicked(plot: Plot, event: MouseClicked, point: Point2D) extends Event
  case class Moved  (plot: Plot, event: MouseMoved  , point: Point2D) extends Event
}
trait Plot extends Publisher {
  def frame     : Frame
  def chart     : Chart
  def component : Component
}