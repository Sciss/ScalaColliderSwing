package de.sciss.synth.swing

import java.awt.{Color, Graphics, Component}
import javax.swing.Icon

import scala.swing.{Swing, Alignment, GridPanel, Label, MainFrame, Frame, SimpleSwingApplication}

object IconTest extends SimpleSwingApplication {
  lazy val top: Frame = new MainFrame {
    val xs = Seq(
      "Group"    -> Shapes.Group    _,
      "Synth"    -> Shapes.Synth    _,
      "UGen"     -> Shapes.UGen     _,
      "SynthDef" -> Shapes.SynthDef _
    )

    contents = new GridPanel(0, 1) {
      border = Swing.EmptyBorder(8)
      contents ++= xs.map { case (name, fun) =>
        new Label(name) {
          horizontalAlignment = Alignment.Leading
          val inner = Shapes.Icon()(fun)
          icon = new Icon {
            def paintIcon(c: Component, g: Graphics, x: Int, y: Int): Unit = {
              inner.paintIcon(c, g, x + 1, y + 1)
              g.setColor(Color.red)
              g.drawRect(x, y, getIconWidth - 1, getIconHeight - 1)
            }

            def getIconWidth : Int = inner.getIconWidth  + 2
            def getIconHeight: Int = inner.getIconHeight + 2
          }
        }
      }
    }
  }
}
