/*
 *  AudioBusMeter.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
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