/*
 *  ActionPreferences.scala
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

package de.sciss.synth
package swing

import de.sciss.desktop.{FileDialog, Preferences, OptionPane, KeyStrokes}
import de.sciss.swingplus.{Separator, Spinner}
import javax.swing.{JPanel, SpinnerNumberModel, UIManager}
import de.sciss.file._
import scala.swing.{GridBagPanel, Action, Label, Alignment, Component, Swing, TextField, Button, FlowPanel, ComboBox}
import scala.swing.event.{Key, EditDone, SelectionChanged, ValueChanged}
import Swing.EmptyIcon
import java.awt.Insets

object ActionPreferences extends Action("Preferences...") {
  import KeyStrokes._

  accelerator = Some(menu1 + Key.Comma)

  def apply(): Unit = {
    import language.reflectiveCalls

    def label(text: String) = new Label(s"$text:", EmptyIcon, Alignment.Right)

    def intField(prefs: Preferences.Entry[Int], default: => Int, min: Int = 0, max: Int = 65536,
                 step: Int = 1): Component = {
      val m  = new SpinnerNumberModel(prefs.getOrElse(default), min, max, step)
      val gg = new Spinner(m)
      gg.listenTo(gg)
      gg.reactions += {
        case ValueChanged(_) => gg.value match{
          case i: Int => prefs.put(i)
          case _ => println(s"Unexpected value ${gg.value}")
        }
      }
      gg
    }

    def pathField(prefs: Preferences.Entry[File], default: => File, title: String): Component = {
      def fixDefault: File = default  // XXX TODO: Scalac bug?
      val tx = new TextField(prefs.getOrElse(default).path, 16)
      tx.listenTo(tx)
      tx.reactions += {
        case EditDone(_) =>
          if (tx.text.isEmpty) tx.text = fixDefault.path
          prefs.put(file(tx.text))
      }
      val bt = Button("…") {
        val dlg = FileDialog.open(init = prefs.get, title = title)
        dlg.show(None).foreach { f =>
          tx.text = f.path
          prefs.put(f)
        }
      }
      bt.peer.putClientProperty("JButton.buttonType", "square")
      val gg = new FlowPanel(tx, bt) {
        override lazy val peer: JPanel =
          new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 0, 0)) with SuperMixin {
            override def getBaseline(width: Int, height: Int): Int = {
              val res = tx.peer.getBaseline(width, height)
              res + tx.peer.getY
            }
          }
      }
      gg
    }

    def textField(prefs: Preferences.Entry[String], default: => String): Component = {
      def fixDefault: String = default  // XXX TODO: Scalac bug?
      val gg = new TextField(prefs.getOrElse(default), 16)
      gg.listenTo(gg)
      gg.reactions += {
        case EditDone(_) =>
          if (gg.text.isEmpty) gg.text = fixDefault
          prefs.put(gg.text)
      }
      gg
    }

    def combo[A](prefs: Preferences.Entry[A], default: => A, values: Seq[A])(implicit view: A => String): Component = {
      val gg = new ComboBox[A](values)
      gg.renderer = scala.swing.ListView.Renderer(view)
      gg.peer.putClientProperty("JComboBox.isSquare", true)
      val idx0 = values.indexOf(prefs.getOrElse(default))
      if (idx0 >= 0) gg.selection.index = idx0
      gg.listenTo(gg.selection)
      gg.reactions += {
        case SelectionChanged(_) =>
          val it = gg.selection.item
          // println(s"put($it)")
          prefs.put(it)
      }
      gg
    }

    val box = new GridBagPanel {
      import GridBagPanel.{Anchor, Fill}
      val cLb = new Constraints()
      cLb.gridx = 0; cLb.gridy = 0; cLb.anchor = Anchor.LineStart
      cLb.ipadx = 2; /* cLb.ipady = 2; */ cLb.insets = new Insets(2, 2, 2, 2)
      val cGG = new Constraints()
      cGG.gridx = 1; cGG.gridy = 0; cGG.anchor = Anchor.LineStart; cGG.fill = Fill.Horizontal
      cGG.ipadx = 2; /* cGG.ipady = 2; */ cGG.insets = new Insets(2, 2, 2, 2)

      val lbLookAndFeel   = label("Look-and-Feel")
      val ggLookAndFeel   = combo(Prefs.lookAndFeel, Prefs.defaultLookAndFeel,
        UIManager.getInstalledLookAndFeels)(_.getName)

      val lbColorScheme   = label("Color Scheme")
      val ggColorScheme   = combo(Prefs.colorScheme, Prefs.ColorSchemeNames.default, Prefs.ColorSchemeNames.all)

      val lbSuperCollider = label("SuperCollider (scsynth)")
      val ggSuperCollider = pathField(Prefs.superCollider, Prefs.defaultSuperCollider,
        title = "SuperCollider Server Location (scsynth)")

      val lbAudioDevice   = label("Audio Device")
      val ggAudioDevice   = textField(Prefs.audioDevice   , Prefs.defaultAudioDevice    )
      val lbNumInputs     = label("Input Channels")
      val ggNumInputs     = intField(Prefs.audioNumInputs , Prefs.defaultAudioNumInputs )
      val lbNumOutputs    = label("Output Channels")
      val ggNumOutputs    = intField(Prefs.audioNumOutputs, Prefs.defaultAudioNumOutputs)

      // val lbHeadphones    = label("Headphones Bus")
      // val ggHeadphones    = intField(Prefs.headphonesBus  , Prefs.defaultHeadphonesBus  )

      val sep1 = Separator()

      def add(lb: Component, gg: Component): Unit = {
        layout(lb) = cLb
        layout(gg) = cGG
        cLb.gridy += 1
        cGG.gridy += 1
      }

      add(lbLookAndFeel  , ggLookAndFeel  )
      add(lbColorScheme  , ggColorScheme  )
      cLb.gridwidth = 2
      layout(sep1) = cLb
      cLb.gridy += 1; cGG.gridy += 1; cLb.gridwidth = 1
      add(lbSuperCollider, ggSuperCollider)
      add(lbAudioDevice  , ggAudioDevice  )
      add(lbNumInputs    , ggNumInputs    )
      add(lbNumOutputs   , ggNumOutputs   )
      // add(lbHeadphones   , ggHeadphones   )
    }

    val opt   = OptionPane.message(message = box, messageType = OptionPane.Message.Plain)
    opt.title = "Preferences"
    opt.show(None)
  }
}