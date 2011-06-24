package de.sciss.synth.swing

import de.sciss.synth.{ Group => SGroup }

object GUI {
   class Factory[ T ] private[swing] ( target: => T ) { def gui: T = target }

   class Group private[swing] ( val group: SGroup ) {
      def tree { println( "Jo chuck" )}
   }
}