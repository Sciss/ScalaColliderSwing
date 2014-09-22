/*
 *  TextViewImpl.scala
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

import de.sciss.scalainterpreter.InterpreterPane

import scala.swing.{Swing, Action, Component}
import de.sciss.{scalainterpreter => si}
import de.sciss.swingplus.Implicits._
import scala.concurrent.{ExecutionContext, Future}
import java.awt.EventQueue
import de.sciss.syntaxpane.SyntaxDocument
import java.beans.{PropertyChangeEvent, PropertyChangeListener}
import javax.swing.{KeyStroke, AbstractAction, JComponent}
import java.awt.event.{ActionEvent, InputEvent, KeyEvent}
import de.sciss.model.impl.ModelImpl
import de.sciss.model.Change
import de.sciss.file.File

object TextViewImpl {
  def apply(intp: Future[si.Interpreter], config: si.CodePane.Config): TextView = {
    val res = new Impl(intp, config)
    res.guiInit()
    res
  }

  private final class Impl(interpreter: Future[si.Interpreter], codeConfig: si.CodePane.Config)
    extends TextView with ModelImpl[TextView.Update] {

    private var _file = Option.empty[File]
    def file = _file
    def file_=(value: Option[File]): Unit = if (_file != value) {
      val old = _file
      _file = value
      dispatch(TextView.FileChange(Change(old, value)))
    }

    private var _dirty = false
    def dirty = _dirty
    def dirty_=(value: Boolean): Unit = if (_dirty != value) {
      _dirty = value
      dispatch(TextView.DirtyChange(value))
    }

    private var codePane: si.CodePane = _
    // private var futCompile = Option.empty[Future[Any]]
    // private var actionApply: Action = _

    def editor: si.CodePane = codePane

    protected def currentText: String = codePane.editor.text

    //    private def addEditAndClear(edit: UndoableEdit): Unit = {
    //      requireEDT()
    //      undoManager.add(edit)
    //      // this doesn't work properly
    //      // component.setDirty(value = false) // do not erase undo history
    //
    //      // so let's clear the undo history now...
    //      codePane.editor.getDocument.asInstanceOf[SyntaxDocument].clearUndos()
    //    }

    //    def undoManager: UndoManager = new UndoManagerImpl {
    //      override lazy val peer = {
    //        val doc = codePane.editor.getDocument.asInstanceOf[SyntaxDocument]
    //        val f = doc.getClass.getDeclaredField("undo")   // XXX TODO: not cool
    //        f.setAccessible(true)
    //        f.get(doc).asInstanceOf[javax.swing.undo.UndoManager]
    //      }
    //
    //      protected var dirty: Boolean = peer.canUndo
    //    }

    def clearUndoBuffer(): Unit = {
      // chasing an odd problem where the runtime classes for SyntaxDocument do not match...
      codePane.editor.peer.getDocument match {
        case doc: SyntaxDocument => doc.clearUndos()
        case other => Console.err.println(s"Expected SyntaxDocument but found ${other.getClass.getName}")
      }
      dirty = false
    }

    //    private def requireEDT(): Unit =
    //      if (!EventQueue.isDispatchThread) throw new IllegalStateException("Must be executed on the EDT")

    private def defer(body: => Unit): Unit =
      if (EventQueue.isDispatchThread) body else Swing.onEDT(body)

    //    def isCompiling: Boolean = {
    //      requireEDT()
    //      futCompile.isDefined
    //    }

    def undoAction: Action = Action.wrap(codePane.editor.peer.getActionMap.get("undo"))
    def redoAction: Action = Action.wrap(codePane.editor.peer.getActionMap.get("redo"))

    private var _comp: Component =_

    def component: Component = {
      if (_comp == null) throw new IllegalStateException("GUI not yet initialized")
      _comp
    }

    private def installExecutionAction(intp: si.Interpreter): Unit = {
      val ed          = codePane.editor
      val edJ         = ed.peer
      val iMap        = edJ.getInputMap(JComponent.WHEN_FOCUSED)
      val aMap        = edJ.getActionMap
      val executeKey  = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK)
      iMap.put(executeKey, "de.sciss.exec")
      aMap.put("de.sciss.exec", new AbstractAction {
        def actionPerformed(e: ActionEvent): Unit =
          InterpreterPane.bang(codePane, intp) match {
            case Some(si.Interpreter.Incomplete) =>
              println("Interpreter: Code incomplete!")
              // case si.Interpreter.Success(_, _) =>
              // case si.Interpreter.Error(message) =>
              //   println(s"Interpreter error: $message!")
            case _ =>
          }
      })
    }

    def guiInit(): Unit = {
      codePane = si.CodePane(codeConfig)
      //      locally {
      //        val ch = codePane.getClass.getClassLoader.hashCode().toHexString
      //        val dh = codePane.editor.getDocument.getClass.getClassLoader.hashCode().toHexString
      //        val th = Thread.currentThread().hashCode().toHexString
      //        val xh = Thread.currentThread().getContextClassLoader.hashCode().toHexString
      //        println(s"codePane.classLoader = $ch; doc.classLoader = $dh; context = $xh; EDT = $th")
      //      }

      import ExecutionContext.Implicits.global
      interpreter.onSuccess { case intp =>
        defer {
          codePane.installAutoCompletion(intp)
          installExecutionAction(intp)
        }
      }

      //      val progressPane = new OverlayPanel {
      //        contents += ggProgress
      //        contents += ggProgressInvisible
      //      }
      //
      //      actionApply = Action("Apply")(save())
      //      actionApply.enabled = false

      codePane.editor.peer.getDocument match {
        // chasing an odd problem where the runtime classes for SyntaxDocument do not match...
        case doc: SyntaxDocument =>
          //      doc.addUndoableEditListener(
          //        new UndoableEditListener {
          //          def undoableEditHappened(e: UndoableEditEvent): Unit =
          //            if (clearGreen) {
          //              clearGreen = false
          //              ggCompile.icon = compileIcon(None)
          //            }
          //        }
          //      )

          doc.addPropertyChangeListener(SyntaxDocument.CAN_UNDO, new PropertyChangeListener {
            def propertyChange(e: PropertyChangeEvent): Unit = dirty = doc.canUndo
          })

        case other => Console.err.println(s"Expected SyntaxDocument but found ${other.getClass.getName}")
      }
      // lazy val ggApply  : Button = GUI.toolButton(actionApply  , raphael.Shapes.Check , tooltip = "Save text changes")

      //      val panelBottom = new FlowPanel(FlowPanel.Alignment.Trailing)(
      //        HGlue, ggApply, ggCompile, progressPane) // HStrut(16))

      _comp = codePane.component
    }
  }
}