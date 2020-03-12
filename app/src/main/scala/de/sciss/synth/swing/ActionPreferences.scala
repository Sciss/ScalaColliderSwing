/*
 *  ActionPreferences.scala
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

package de.sciss.synth
package swing

import java.awt.Insets

import de.sciss.desktop._
import de.sciss.swingplus.Separator

import scala.swing.event.Key
import scala.swing.{Action, Component, GridBagPanel}

object ActionPreferences extends Action("Preferences...") {
  import KeyStrokes._

  accelerator = Some(menu1 + Key.Comma)

  def apply(): Unit = {
    import PrefsGUI._

    val lbLookAndFeel   = label("Look-and-Feel")
    val ggLookAndFeel   = combo(Prefs.lookAndFeel, Prefs.LookAndFeel.default,
      Prefs.LookAndFeel.all)(_.description)

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

    val box = new GridBagPanel {
      import GridBagPanel.{Anchor, Fill}
      val cLb = new Constraints()
      cLb.gridx = 0; cLb.gridy = 0; cLb.anchor = Anchor.LineStart
      cLb.ipadx = 2; /* cLb.ipady = 2; */ cLb.insets = new Insets(2, 2, 2, 2)
      val cGG = new Constraints()
      cGG.gridx = 1; cGG.gridy = 0; cGG.anchor = Anchor.LineStart; cGG.fill = Fill.Horizontal
      cGG.ipadx = 2; /* cGG.ipady = 2; */ cGG.insets = new Insets(2, 2, 2, 2)

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