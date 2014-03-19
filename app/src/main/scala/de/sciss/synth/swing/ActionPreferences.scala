package de.sciss.synth
package swing

import java.awt.event.KeyEvent
import de.sciss.desktop.{FileDialog, Preferences, OptionPane, KeyStrokes}
import scalaswingcontrib.group.GroupPanel
import de.sciss.swingplus.{Separator, Spinner}
import javax.swing.{JPanel, SpinnerNumberModel, UIManager}
import de.sciss.file._
import scala.swing.{Action, Label, Alignment, Component, Swing, TextField, Button, FlowPanel, ComboBox}
import scala.swing.event.{EditDone, SelectionChanged, ValueChanged}
import Swing.EmptyIcon

object ActionPreferences extends Action("Preferences...") {
  import KeyStrokes._

  accelerator = Some(menu1 + KeyEvent.VK_COMMA)

  def apply(): Unit = {
    import language.reflectiveCalls

    def label(text: String) = new Label(text + ":", EmptyIcon, Alignment.Right)

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

    val box = new GroupPanel {
      val lbLookAndFeel   = label("Look-and-Feel")
      val ggLookAndFeel   = combo(Prefs.lookAndFeel, Prefs.defaultLookAndFeel,
        UIManager.getInstalledLookAndFeels)(_.getName)

      val lbSuperCollider = label("SuperCollider (scsynth)")
      val ggSuperCollider = pathField(Prefs.superCollider, Prefs.defaultSuperCollider,
        title = "SuperCollider Server Location (scsynth)")

      val lbAudioDevice   = label("Audio Device")
      val ggAudioDevice   = textField(Prefs.audioDevice   , Prefs.defaultAudioDevice    )
      val lbNumOutputs    = label("Output Channels")
      val ggNumOutputs    = intField(Prefs.audioNumOutputs, Prefs.defaultAudioNumOutputs)

      val lbHeadphones    = label("Headphones Bus")
      val ggHeadphones    = intField(Prefs.headphonesBus  , Prefs.defaultHeadphonesBus  )

      val sep1 = Separator()

      // val lbValue = new Label("Value:", EmptyIcon, Alignment.Right)
      theHorizontalLayout is Parallel(sep1, Sequential(
        Parallel(lbLookAndFeel, lbSuperCollider, lbAudioDevice, lbNumOutputs, lbHeadphones),
        Parallel(ggLookAndFeel, ggSuperCollider, ggAudioDevice, ggNumOutputs, ggHeadphones)
      ))
      theVerticalLayout is Sequential(
        Parallel(Baseline)(lbLookAndFeel  , ggLookAndFeel  ),
        sep1,
        Parallel(Baseline)(lbSuperCollider, ggSuperCollider),
        Parallel(Baseline)(lbAudioDevice  , ggAudioDevice  ),
        Parallel(Baseline)(lbNumOutputs   , ggNumOutputs   ),
        Parallel(Baseline)(lbHeadphones   , ggHeadphones   )
      )
    }

    val opt   = OptionPane.message(message = box, messageType = OptionPane.Message.Plain)
    opt.title = "Preferences"
    opt.show(None)
  }
}