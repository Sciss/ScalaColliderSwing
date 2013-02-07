/*
 *  Implicits.scala
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

import language.implicitConversions

object Implicits {
   implicit def enableGUI( group: Group )    = new GUI.Factory( new GUI.Group( group ))
   implicit def enableGUI( server: Server )  = new GUI.Factory( new GUI.Server( server ))
   implicit def enableGUI( bus: AudioBus )   = new GUI.Factory( new GUI.AudioBus( bus ))

   object gui {
      def apply[ T : GraphFunction.Result.In ]( thunk: => T ) : GUI.GraphFunction[ T ] = apply()( thunk )
      def apply[ T : GraphFunction.Result.In ]( target: Node = Server.default, outBus: Int = 0,
                fadeTime: Optional[ Double ] = None, addAction: AddAction = addToHead )
                                              ( thunk: => T ) : GUI.GraphFunction[ T ] = {
         new GUI.GraphFunction( target, outBus, fadeTime.option, addAction, Nil, thunk )
      }
   }

//   def any2stringadd( x: Any ) {}
}