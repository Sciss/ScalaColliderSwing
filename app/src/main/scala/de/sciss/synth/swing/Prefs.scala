/*
 *  Prefs.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.swing

import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel

import de.sciss.desktop.Preferences
import de.sciss.desktop.Preferences.Entry
import de.sciss.file._
import de.sciss.scalainterpreter.{Style => ColorScheme}
import de.sciss.submin.Submin
import de.sciss.synth.swing.{Main => App}

object Prefs {
  import App.userPrefs

  object LookAndFeel {
    implicit object Type extends Preferences.Type[LookAndFeel] {
      def toString(value: LookAndFeel): String = value.id
      def valueOf(string: String): Option[LookAndFeel] = all.find(_.id == string)
    }

    case object Native extends LookAndFeel {
      val id          = "native"
      val description = "Native"

      def install(): Unit = UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName)
    }

    case object Metal extends LookAndFeel {
      val id          = "metal"
      val description = "Metal"

      def install(): Unit = UIManager.setLookAndFeel(classOf[MetalLookAndFeel].getName)
    }

    case object Light extends LookAndFeel {
      val id          = "light"
      val description = "Submin Light"

      def install(): Unit = Submin.install(false)
    }

    case object Dark extends LookAndFeel {
      val id          = "dark"
      val description = "Submin Dark"

      def install(): Unit = Submin.install(true)
    }

    def all: Seq[LookAndFeel] = Seq(Native, Metal, Light, Dark)

    def default: LookAndFeel = Light
  }

  sealed trait LookAndFeel {
    def install(): Unit
    def id: String
    def description: String
  }

  def lookAndFeel: Entry[LookAndFeel] = userPrefs("look-and-feel")

  object ColorSchemeNames {
    private val blueForest  = "blue-forest"
    private val light       = "light"

    def all: Seq[String] = Seq(blueForest, light)

    def default: String = light // blueForest

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

  final val defaultSuperCollider  : File    = file("<SC_HOME>")
  final val defaultAudioDevice    : String  = "<default>"
  final val defaultAudioNumInputs : Int     = 8
  final val defaultAudioNumOutputs: Int     = 8
  final val defaultHeadphonesBus  : Int     = 0

  def superCollider  : Entry[File  ] = userPrefs("supercollider"    )
  def audioDevice    : Entry[String] = userPrefs("audio-device"     )
  def audioNumInputs : Entry[Int   ] = userPrefs("audio-num-inputs ")
  def audioNumOutputs: Entry[Int   ] = userPrefs("audio-num-outputs")
  def headphonesBus  : Entry[Int   ] = userPrefs("headphones-bus"   )
}