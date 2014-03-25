package de.sciss.synth.swing

import scala.swing.Component
import impl.{TextViewImpl => Impl}
import scala.concurrent.Future

object TextView {
  def apply(intp: Future[Interpreter]): TextView = Impl(intp)
}
trait TextView {
  def component: Component
}
