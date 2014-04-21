/*
 *  AppFunctions.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.swing

import java.io.RandomAccessFile
import de.sciss.file._
import de.sciss.desktop.FileDialog
import de.sciss.synth.swing.{Main => App}

object AppFunctions {
  // def plot(data: Plot.Source, title: String = "title"): Unit = Plot(data)

  implicit class EndingOps(val in: java.io.DataInput) extends AnyVal {
    def readIntLE(): Int = {
      val i0 = in.readInt()
      val b0 = (i0 >> 24) & 0xFF
      val b1 = (i0 >> 16) & 0xFF
      val b2 = (i0 >>  8) & 0xFF
      val b3 =  i0        & 0xFF
      val i1 = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0
      i1
    }

    def readFloatLE(): Float = java.lang.Float.intBitsToFloat(readIntLE())
  }

  def readSAC(file: File): Vector[Float] = {
    val raf = new RandomAccessFile(file, "r")
    try {
      val sz  = raf.length()
      val hdr = 158 * 4
      require(sz >= hdr, s"File $file is too short. Should be at least $hdr bytes long.")
      raf.seek(79 * 4)    // NPTS
      val numSamples = raf.readIntLE()
      val szE = numSamples * 4 + hdr
      require(szE == sz, s"With $numSamples samples, file should be $szE bytes long.")
      raf.seek(hdr)
      Vector.fill(numSamples)(raf.readFloatLE())
    } finally {
      raf.close()
    }
  }

  def openURL(url: String): Unit = Main.openURL(url)

  def openFileDialog(init: Option[File] = None): File = {
    val dlg = FileDialog.open(init)
    dlg.show(Some(App.mainWindow)).getOrElse(file("<undefined>"))
  }
}