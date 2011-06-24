package de.sciss.synth.swing

import j.JNodeTreePanel
import scala.swing.Component
import de.sciss.synth.Server

class NodeTreePanel extends Component {
   override lazy val peer: JNodeTreePanel = new JNodeTreePanel with SuperMixin

   def server: Option[ Server ]        = peer.server
   def server_=( s: Option[ Server ])  = peer.server_=( s )
}