/*
 *  ServerStatusPanel.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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