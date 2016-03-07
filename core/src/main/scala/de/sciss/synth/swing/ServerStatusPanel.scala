/*
 *  ServerStatusPanel.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.swing

import j.JServerStatusPanel
import swing.Component
import de.sciss.synth.{Server, ServerConnection}

class ServerStatusPanel extends Component {
  override lazy val peer: JServerStatusPanel = new JServerStatusPanel with SuperMixin

  def server    : Option[Server]        = peer.server
  def server_=(s: Option[Server]): Unit = peer.server = s

  def booting    : Option[ServerConnection]         = peer.booting
  def booting_=(b: Option[ServerConnection]): Unit  = peer.booting = b

  def bootAction    : Option[() => Unit]        = peer.bootAction
  def bootAction_=(a: Option[() => Unit]): Unit = peer.bootAction = a
}