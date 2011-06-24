package de.sciss.synth.swing

import j.JServerStatusPanel
import swing.Component
import de.sciss.synth.{Server, ServerConnection}

class ServerStatusPanel extends Component {
   override lazy val peer: JServerStatusPanel = new JServerStatusPanel with SuperMixin

   def server : Option[ Server ]                         = peer.server
   def server_=( s: Option[ Server ]) : Unit             = peer.server_=( s )
   def booting : Option[ ServerConnection ]              = peer.booting
   def booting_=( b: Option[ ServerConnection ]) : Unit  = peer.booting_=( b )
   def bootAction : Option[ () => Unit ]                 = peer.bootAction
   def bootAction_=( a: Option[ () => Unit ]) : Unit     = peer.bootAction_=( a )
}