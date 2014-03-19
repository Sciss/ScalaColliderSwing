//package de.sciss.synth.swing
//
//object Plot {
//  object Source {
//    implicit def fromIntSeq1D   (seq: Seq[Int   ]): Source = new Dummy
//    implicit def fromFloatSeq1D (seq: Seq[Float ]): Source = new Dummy
//    implicit def fromDoubleSeq1D(seq: Seq[Double]): Source = new Dummy
//
//    private class Dummy extends Source
//  }
//  sealed trait Source
//
//  def apply(source: Source): Unit = println("Ok")
//}
