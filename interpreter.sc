// these imports and definitions are provided by ScalaCollider-Swing.
// you do not have to execute them when using the Swing IDE.

import de.sciss.synth._
import de.sciss.osc
import ugen._
import Ops._
import de.sciss.synth.swing.Implicits._

def s = Server.default
val config = Server.Config()
def boot(): Unit = Server.boot(config = config)

////////

/*---
  You are supposed to execute the examples _step by step_.
  If you are not familiar with the workings of SuperCollider,
  as a visual indicator, whenever you see a blank line, this
  is where a new execution block begins. For example,
  execute the block `val x1 = play ... ` up to `.. }` as one
  (it will start playing a sound), then in a second step execute
  the block `x1 release 10` (it will stop that sound).

  You can execute each bloc by selecting it and pressing shift-return.
*/

// boot

config.program     = "scsynth"  // valid path here
config.transport   = osc.TCP
boot()

// analog bubbles
val x1 = play {
  val f = LFSaw.kr(0.4).mulAdd(24, LFSaw.kr(List(8, 7.23)).mulAdd(3, 80)).midiCps // glissando function
  CombN.ar(SinOsc.ar(f)*0.04, 0.2, 0.2, 4) // echoing sine wave
}

// bus plotter
val b1 = AudioBus(s, s.config.outputBusChannels, 1) // microphone
b1.gui.waveform(duration = 2)

x1 release 10

// function plotter
val x2 = graph {
  val f = LFSaw.kr(0.4).mulAdd(24, LFSaw.kr(List(8, 7.23)).mulAdd(3, 80)).midiCps // glissando function
  CombN.ar(SinOsc.ar(f)*0.04, 0.2, 0.2, 4) // echoing sine wave
}

x2.gui.waveform(duration = 2)

// node tree
s.defaultGroup.gui.tree()

// meters
s.gui.meter()
b1.gui.meter()

val df3 = SynthDef("AnalogBubbles") {
  val f1 = "freq1".kr(0.4)
  val f2 = "freq2".kr(8.0)
  val d  = "detune".kr(0.90375)
  val f = LFSaw.ar(f1).mulAdd(24, LFSaw.ar(List(f2, f2*d)).mulAdd(3, 80)).midiCps // glissando function
  val x = CombN.ar(SinOsc.ar(f)*0.04, 0.2, 0.2, 4) // echoing sine wave
  Out.ar(0, x)
}
val x3 = df3.play(args = List("freq2" -> 222.2))

x3.set("freq1" -> 0.1)

x3.set("detune" -> 0.44)

x3 run false

x3 run true

val y = playWith(target = x3, addAction = addAfter) {
  ReplaceOut.ar(0, In.ar(0, 2) * SinOsc.ar("freq".kr(1.0), math.Pi/4))
}

y.set("freq" -> 10)

s.freeAll()

df3.gui.diagram()