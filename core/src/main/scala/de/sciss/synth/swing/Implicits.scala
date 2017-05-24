/*
 *  Implicits.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth
package swing

import scala.language.implicitConversions

object Implicits {
  implicit def enableGUI(group : Group   ): GUI.Factory[GUI.Group   ]  = new GUI.Factory(new GUI.Group   (group ))
  implicit def enableGUI(server: Server  ): GUI.Factory[GUI.Server  ]  = new GUI.Factory(new GUI.Server  (server))
  implicit def enableGUI(bus   : AudioBus): GUI.Factory[GUI.AudioBus]  = new GUI.Factory(new GUI.AudioBus(bus   ))
  implicit def enableGUI(sd    : SynthDef): GUI.Factory[GUI.SynthDef]  = new GUI.Factory(new GUI.SynthDef(sd    ))

  implicit def enableGUI[A](fun: GraphFunction[A]): GUI.Factory[GUI.GraphFunction[A]] =
    new GUI.Factory(new GUI.GraphFunction(fun))

//  object gui {
//    def apply[A: GraphFunction.Result.In](thunk: => A): GUI.GraphFunction[A] = apply()(thunk)
//
//    def apply[A: GraphFunction.Result.In](target: Node = Server.default, outBus: Int = 0,
//                                          fadeTime: Optional[Double] = None, addAction: AddAction = addToHead)
//                                         (thunk: => A): GUI.GraphFunction[A] = {
//      val data = new GUI.GraphFunctionData(target = target, outBus = outBus, fadeTime = fadeTime,
//        addAction = addAction, args = Nil, thunk = thunk)
//      new GUI.GraphFunction(data)
//    }
//  }
}