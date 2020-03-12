/*
 *  TextViewDockable.scala
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

import bibliothek.gui.dock.common.{CLocation, EmptyMultipleCDockableFactory, MultipleCDockableLayout, MultipleCDockable, MultipleCDockableFactory, DefaultMultipleCDockable}
import java.awt.geom.AffineTransform
import de.sciss.file._
import de.sciss.scalainterpreter.CodePane
import Main.{dockControl, documentHandler, interpreter}

object TextViewDockable {
  private lazy val docFact: MultipleCDockableFactory[MultipleCDockable, MultipleCDockableLayout] =
    new EmptyMultipleCDockableFactory[MultipleCDockable] {
      def createDockable(): MultipleCDockable = empty()
    }

  def factory: MultipleCDockableFactory[MultipleCDockable, MultipleCDockableLayout] = docFact

  private var newFileCount = 0

  def empty(): TextViewDockable = {
    newFileCount += 1
    apply(text0 = "", file = None)
  }

  def apply(text0: String, file: Option[File]): TextViewDockable = {
    val cnt   = newFileCount
    val cc    = CodePane.Config()
    cc.text   = text0
    val cn    = Prefs.colorScheme.getOrElse(Prefs.ColorSchemeNames.default)
    cc.style  = Prefs.ColorSchemeNames(cn)
    val text  = TextView(interpreter, cc)
    text.file = file
    // val sid = docFact.create() new DefaultSingleCDockable("interpreter", "Interpreter", sip.component.peer)
    text.editor.editor.caret.position = 0  // XXX TODO: that should be done by the codePane itself automatically

    def mkTitle() = {
      val name = text.file.fold {
        if (cnt == 1) "Untitled" else s"Untitled $cnt"
      } { f =>
        f.base
      }
      if (text.dirty) s"*$name" else name
    }

    // val content   = text.component.peer
    val dock      = new TextViewDockable(text)
    dock.setLocation(CLocation.base().normalWest(0.6))

    def updateTitle(): Unit = dock.setTitleText(mkTitle())

    updateTitle()

    text.addListener {
      case TextView.DirtyChange(_) => updateTitle()
      case TextView.FileChange (_) => updateTitle()
    }
    dockControl.addDockable(dock)
    dock.setVisible(true)
    documentHandler.addDocument(dock)

    // tricky business to ensure initial focus
    dock.setFocusComponent(text.editor.editor.peer)
    dockControl.getController.setFocusedDockable(dock.intern(), true)
    dock
  }
}
class TextViewDockable(val view: TextView)
  extends DefaultMultipleCDockable(TextViewDockable.factory, view.component.peer) {

  private var fntSizeAmt = 0

  override def toString: String = s"TextViewDockable($view)"

  private def updateFontSize(): Unit = {
    val ed    = view.editor.editor
    val fnt   = ed.font
    val scale = math.pow(1.08334, fntSizeAmt)
    // note: deriveFont _replaces_ the affine transform, does not concatenate it
    ed.font   = fnt.deriveFont(AffineTransform.getScaleInstance(scale, scale))
  }

  private def fontSizeReset(): Unit = {
    fntSizeAmt = 0
    updateFontSize()
  }

  def fontSizeChange(rel: Int): Unit =
    if (rel == 0) fontSizeReset() else {
      fntSizeAmt += rel
      updateFontSize()
    }

  def close(): Unit = {
    dockControl    .removeDockable(this)
    documentHandler.removeDocument(this)
  }
}
