/*
 *  NodeTreePanel.scala
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

import j.{NodeTreePanelLike, JNodeTreePanel}
import swing.{Frame, Component}
import de.sciss.synth.Group
import java.awt.EventQueue
import javax.swing.WindowConstants

class NodeTreePanel extends Component with NodeTreePanelLike {
   treePanel =>

  override lazy val peer: JNodeTreePanel = new JNodeTreePanel with SuperMixin

  private var frame = Option.empty[Frame]

  def group        : Option[Group]        = peer.group
  def group_=(value: Option[Group]): Unit = peer.group = value

  def makeWindow(disposeOnClose: Boolean = true): Frame = {
    require( EventQueue.isDispatchThread )
    frame getOrElse {
      val fr = new Frame() {
        override def toString() = "NodeTreeFrame@" + hashCode().toHexString
      }
      fr.peer.setDefaultCloseOperation(
        if (disposeOnClose) WindowConstants.DISPOSE_ON_CLOSE else WindowConstants.DO_NOTHING_ON_CLOSE
      )
      fr.peer.getRootPane.putClientProperty("Window.style", "small")
      fr.contents = treePanel
      fr.pack()
      fr.centerOnScreen()
      peer.setFrame(fr.peer)
      frame = Some(fr)
      fr
    }
  }

  def nodeActionMenu               : Boolean        = peer.nodeActionMenu
  def nodeActionMenu_=           (b: Boolean): Unit = peer.nodeActionMenu = b

  def confirmDestructiveActions    : Boolean        = peer.confirmDestructiveActions
  def confirmDestructiveActions_=(b: Boolean): Unit = peer.confirmDestructiveActions = b
}