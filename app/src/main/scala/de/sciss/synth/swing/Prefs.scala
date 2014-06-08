/*
 *  Prefs.scala
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

import de.sciss.desktop.Preferences
import Preferences.{Entry, Type}
import de.sciss.file._
import javax.swing.UIManager
import UIManager.LookAndFeelInfo
import de.sciss.synth.swing.{Main => App}
import de.sciss.scalainterpreter.{Style => ColorScheme}

object Prefs {
  import App.userPrefs

  implicit object LookAndFeelType extends Type[LookAndFeelInfo] {
    def toString(value: LookAndFeelInfo): String = value.getClassName
    def valueOf(string: String): Option[LookAndFeelInfo] =
      UIManager.getInstalledLookAndFeels.find(_.getClassName == string)
  }

  // ---- gui ----

  def defaultLookAndFeel: LookAndFeelInfo = {
    //    val clazzName = UIManager.getSystemLookAndFeelClassName
    //    LookAndFeelType.valueOf(clazzName)
    //      .getOrElse(new LookAndFeelInfo("<system>", clazzName))
    new LookAndFeelInfo("Web Look And Feel", "com.alee.laf.WebLookAndFeel")
  }

  def lookAndFeel: Entry[LookAndFeelInfo] = userPrefs("look-and-feel")

  object ColorSchemeNames {
    private val blueForest  = "blue-forest"
    private val light       = "light"

    def all = Seq(blueForest, light)

    def default = light // blueForest

    def apply(name: String): ColorScheme = name match {
      case `blueForest` => ColorScheme.BlueForest
      case `light`      => ColorScheme.Light
    }

    def apply(scheme: ColorScheme): String = scheme match {
      case ColorScheme.BlueForest => blueForest
      case ColorScheme.Light      => light
    }
  }

  def colorScheme: Entry[String] = userPrefs("color-scheme")

  // ---- audio ----

  final val defaultSuperCollider    = file("<SC_HOME>")
  final val defaultAudioDevice      = "<default>"
  final val defaultAudioNumInputs   = 8
  final val defaultAudioNumOutputs  = 8
  final val defaultHeadphonesBus    = 0

  def superCollider  : Entry[File  ] = userPrefs("supercollider"    )
  def audioDevice    : Entry[String] = userPrefs("audio-device"     )
  def audioNumInputs : Entry[Int   ] = userPrefs("audio-num-inputs ")
  def audioNumOutputs: Entry[Int   ] = userPrefs("audio-num-outputs")
  def headphonesBus  : Entry[Int   ] = userPrefs("headphones-bus"   )
}