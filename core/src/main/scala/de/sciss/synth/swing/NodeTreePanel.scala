/*
 *  NodeTreePanel.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
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
    require(EventQueue.isDispatchThread)
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