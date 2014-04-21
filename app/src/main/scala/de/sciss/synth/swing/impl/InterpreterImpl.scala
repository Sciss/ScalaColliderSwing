/*
 *  InterpreterImpl.scala
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
package impl

import scala.concurrent.{ExecutionContext, Future}
import de.sciss.{scalainterpreter => si}
import de.sciss.scalainterpreter.Interpreter.Result
import scala.tools.nsc.interpreter.Completion.ScalaCompleter
import de.sciss.model.impl.ModelImpl
import java.awt.EventQueue
import scala.swing.Swing

object InterpreterImpl {
  def apply(config: si.Interpreter.Config): Future[Interpreter] = {
    val peerFut = si.Interpreter.async(config)
    import ExecutionContext.Implicits.global
    peerFut.map { peer => new Impl(peer) }
  }

  private final class Impl(peer: si.Interpreter) extends Interpreter with ModelImpl[Interpreter.Update] {
    def completer: ScalaCompleter = peer.completer

    private def defer(body: => Unit): Unit =
      if (EventQueue.isDispatchThread) body else Swing.onEDT(body)

    def interpretWithoutResult(code: String, quiet: Boolean): Result = {
      val res = peer.interpretWithoutResult(code, quiet)
      intpDone(code, res)
      res
    }

    private def intpDone(code: String, result: Result): Unit =
      defer(dispatch(Interpreter.Update(code, result)))

    def interpret(code: String, quiet: Boolean): Result = {
      val res = peer.interpret(code, quiet)
      intpDone(code, res)
      res
    }
  }
}
