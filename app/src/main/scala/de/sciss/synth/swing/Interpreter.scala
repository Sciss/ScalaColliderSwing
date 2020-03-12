/*
 *  Interpreter.scala
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

import de.sciss.{scalainterpreter => si}
import impl.{InterpreterImpl => Impl}
import scala.concurrent.Future
import de.sciss.model.Model

object Interpreter {
  type Config     = si.Interpreter.Config
  val  Config     = si.Interpreter.Config

  type Result     = si.Interpreter.Result
  type Success    = si.Interpreter.Success
  val  Success    = si.Interpreter.Success

  type Error      = si.Interpreter.Error
  val  Error      = si.Interpreter.Error

  val  Incomplete = si.Interpreter.Incomplete

  def apply(config: Config): Future[Interpreter] = Impl(config)

  case class Update(input: String, result: Result)
}
trait Interpreter extends si.Interpreter with Model[Interpreter.Update]