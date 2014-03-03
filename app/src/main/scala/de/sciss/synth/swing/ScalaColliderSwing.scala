/*
 *  ScalaColliderSwing.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.swing

import scala.swing.Swing
import de.sciss.osc.TCP
import util.control.NonFatal
import de.sciss.synth.{ServerConnection, Server}

object ScalaColliderSwing extends App {
  // ---- constructor ----
  Swing.onEDT(buildGUI())

  def buildGUI(): Unit = {
    val ssp   = new ServerStatusPanel()
    val sspw  = ssp.peer.makeWindow
    val ntp   = new NodeTreePanel()
    ntp.nodeActionMenu = true
    ntp.confirmDestructiveActions = true
    val ntpw  = ntp.peer.makeWindow()
    val repl  = new REPLSupport(ssp, ntp)
    val sif   = new ScalaInterpreterFrame(repl)
    ntpw.setLocation(sspw.getX, sspw.getY + sspw.getHeight + 32)
    sspw.setVisible(true)
    ntpw.setVisible(true)
    sif.setLocation(sspw.getX + sspw.getWidth + 32, sif.getY)
    sif.setVisible(true)
  }
}
