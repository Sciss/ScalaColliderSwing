/*
 *  TextView.scala
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

package de.sciss.synth.swing

import scala.swing.Component
import impl.{TextViewImpl => Impl}
import scala.concurrent.Future
import de.sciss.model.{Change, Model}
import de.sciss.file.File
import de.sciss.scalainterpreter.CodePane

object TextView {
  def apply(intp: Future[Interpreter], config: CodePane.Config): TextView = Impl(intp, config)

  sealed trait Update // { def view: TextView }
  case class DirtyChange(status: Boolean) extends Update
  case class FileChange (change: Change[Option[File]]) extends Update
}
trait TextView extends Model[TextView.Update] {
  def editor: CodePane
  def component: Component
  // def undoManager: UndoManager
  var file: Option[File]
  def dirty: Boolean

  def clearUndoBuffer(): Unit
}
