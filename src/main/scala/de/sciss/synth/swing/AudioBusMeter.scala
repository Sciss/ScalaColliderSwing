/*
 *  AudioBusMeter.scala
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

package de.sciss.synth
package swing

import scala.swing.Component
import collection.immutable.{Seq => ISeq}
import impl.{AudioBusMeterImpl => Impl}

object AudioBusMeter {
  /** Specification of a meter strip.
    *
    * @param bus        the audio bus to meter
    * @param target     the target point at which the meter synth will sit
    * @param addAction  the relation of the meter synth with respect to its target node.
    */
  final case class Strip(bus: AudioBus, target: Group, addAction: AddAction)

  /** Creates a new audio bus meter for a given list of strips.
    *
    * @param strips the buses and targets to meter. It is possible to mix servers.
    */
  def apply(strips: ISeq[Strip]): AudioBusMeter = new Impl(strips)
}
trait AudioBusMeter {
  /** The buses and targets to meter. */
  def strips: ISeq[AudioBusMeter.Strip]

  /** The swing component showing the meter. */
  def component: Component

  /** Disposes the meter. This must be called on the event dispatch thread.
    * It is safe to call this multiple times. */
  def dispose(): Unit
}