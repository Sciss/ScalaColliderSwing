/*
 *  Interpreter.scala
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

import de.sciss.{scalainterpreter => si}
import impl.{InterpreterImpl => Impl}
import scala.concurrent.Future
import de.sciss.model.Model

object Interpreter {
  def apply(config: si.Interpreter.Config): Future[Interpreter] = Impl(config)

  case class Update(input: String, result: si.Interpreter.Result)
}
trait Interpreter extends si.Interpreter with Model[Interpreter.Update]