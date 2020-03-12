/*
 *  ScalaColliderSwing.scala
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

import scala.swing.{Frame, SimpleSwingApplication, Swing}
import Swing._

object ScalaColliderSwing extends SimpleSwingApplication {
  lazy val top: Frame = {
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
    sif.location = (sspw.getX + sspw.getWidth + 32, sif.peer.getY)
    sif.open()
    sif
  }
}
