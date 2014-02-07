/*
 *  Implicits.scala
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

package de.sciss.synth
package swing

import language.implicitConversions

object Implicits {
  implicit def enableGUI(group : Group   ): GUI.Factory[GUI.Group   ]  = new GUI.Factory(new GUI.Group   (group ))
  implicit def enableGUI(server: Server  ): GUI.Factory[GUI.Server  ]  = new GUI.Factory(new GUI.Server  (server))
  implicit def enableGUI(bus   : AudioBus): GUI.Factory[GUI.AudioBus]  = new GUI.Factory(new GUI.AudioBus(bus   ))

  object gui {
    def apply[T: GraphFunction.Result.In](thunk: => T): GUI.GraphFunction[T] = apply()(thunk)

    def apply[T: GraphFunction.Result.In](target: Node = Server.default, outBus: Int = 0,
                                          fadeTime: Optional[Double] = None, addAction: AddAction = addToHead)
                                         (thunk: => T): GUI.GraphFunction[T] =
      new GUI.GraphFunction(target, outBus, fadeTime.option, addAction, Nil, thunk)
  }
}