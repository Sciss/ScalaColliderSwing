/*
 *  ScalaColliderSwing.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2012 Hanns Holger Rutz. All rights reserved.
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

package de.sciss.synth
package swing

import scala.swing.Swing
import de.sciss.osc.TCP

object ScalaColliderSwing extends App {
   // ---- constructor ----
   Swing.onEDT( buildGUI() )

   class REPLSupport( ssp: ServerStatusPanel, ntp: NodeTreePanel ) {
     override def toString = "repl-support"

      var s : Server = null
      val sCfg = Server.Config()
      sCfg.transport = TCP
      private val sync = new AnyRef
      private var booting: ServerConnection = null

      // ---- constructor ----
      sys.addShutdownHook( shutDown() )
      ssp.bootAction = Some( () => boot() )

      def boot() { sync.synchronized {
         shutDown()
         booting = Server.boot( config = sCfg ) {
            case ServerConnection.Preparing( srv ) => {
//               ntp.server = Some( srv )
               ntp.group = Some( srv.rootNode )
            }
            case ServerConnection.Running( srv ) => {
               sync.synchronized {
                  booting = null
                  s = srv
               }
            }
         }
         ssp.booting = Some( booting )
      }}

      private def shutDown() { sync.synchronized {
         if( (s != null) && (s.condition != Server.Offline) ) {
            s.quit()
            s = null
         }
         if( booting != null ) {
            booting.abort()
            booting = null
         }
      }}
   }

   def buildGUI() {
      val ssp  = new ServerStatusPanel()
      val sspw = ssp.peer.makeWindow
      val ntp  = new NodeTreePanel()
      ntp.nodeActionMenu            = true
      ntp.confirmDestructiveActions = true
      val ntpw = ntp.peer.makeWindow()
      val repl = new REPLSupport( ssp, ntp )
      val sif  = new ScalaInterpreterFrame( repl )
      ntpw.setLocation( sspw.getX, sspw.getY + sspw.getHeight + 32 )
      sspw.setVisible( true )
      ntpw.setVisible( true )
      sif.setLocation( sspw.getX + sspw.getWidth + 32, sif.getY )
      sif.setVisible( true )
   }
}
