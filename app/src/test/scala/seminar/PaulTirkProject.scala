package seminar

import de.sciss.file._
import de.sciss.synth._
import ugen._
import Ops._
import de.sciss.kollflitz.Ops._
import de.sciss.synth.swing.Plotting._

object PaulTirkProjectRun extends App {
  Server.run(_ => PaulTirkProject)
}

object PaulTirkProject {
  import de.sciss.synth.swing.Plot

  // val p = "/media/Daten/Uni/Elektrotechnik - Toningenieur/Sonifikation - Sound of Science, SE/embo_PROTON002.fid"
  val p0 = userHome / "IEM" / "Unterricht" / "SE_Sonifikation_SoSe14" / "session09" / "embo_PROTON002.fid"
  require(p0.isFile)
  val p = p0.path

  def mag(tup: (Int, Int)): Double = {
    val (re, im) = tup  // pattern match equi. val re = tup._1; val im = tup._2
    math.sqrt(re.toDouble * re.toDouble + im.toDouble * im.toDouble)
  }

  // ----------------- FFT
  // the JTransforms library erwartet ein "flaches" Array[Double],
  // d.h. mit abwechselnden re und im werten.
  // die hilfsfunktion fftPrepare konvertiert den Vector[(Int, Int)] entsprechend

  def fftPrepare(in: Vector[(Int, Int)]): Array[Double] =
    in.flatMap { case (re, im) => Array(re.toDouble, im.toDouble) } .toArray

  def findPeaks(in: Vector[Double], winSize: Int = 16): Vector[(Int, Double)] = {
    val xi = in.zipWithIndex

    def isMax(x0: Double, x1: Double, x2: Double): Boolean = x0 < x1 && x1 > x2

    val alleMaxima: Vector[(Int, Double)] = xi.sliding(3, 1).collect {
      case Seq((x0, x0i), (x1, x1i), (x2, x2i)) if isMax(x0, x1, x2) => (x1i, x1)
    } .toVector

    // an dieser stelle enthaelt alleMaxima alle paare von indices und magnituden,
    // die lokale maxima darstellen.

    val filter = alleMaxima.foldLeft(Vector.empty[(Int, Double)]) { case (res, (yi, y)) =>
      val (out, in) = res.span { case (xi, x) => yi - xi >= winSize }
      val in1 = (in :+ (yi, y)).maxBy(_._2)
      out :+ in1
    }

    filter.toVector
  }

  def flatSignal(in: Vector[(Int, Double)], size: Int): Vector[Double] = {
    val m = in.toMap withDefaultValue 0.0
    Vector.tabulate(size)(m)
  }

  // idee: absoluter threshold
  def thresh(in: Vector[(Int, Double)], x: Double) = in.filter(_._2 > x)


  def findPeak(sigfmpt:Vector[(Int, Double)],px:Double):(Int,Double) = sigfmpt.minBy {
    case (f,a) => f absdif px
  }

  def loadFile(filename:String): (Plot,Vector[(Double,Double)],Vector[(Int,Double)]) = {
    val source = scala.io.Source.fromFile(filename)
    val lines = source.getLines.toVector
    val sig = lines.map { x =>  // x ist ein string
      // split zerlegt einen string in array von strings getrennt durch das
      // argument; filter(_.nonEmpty) entfernt die leeren zwischenteile
      // map(_.toInt) versucht strings in int zu konvertieren
      // Array(re, im) = ... ist pattern match, um die beiden elemente zu extrahieren
      val Array(re, im) = x.split(" ").filter(_.nonEmpty).map(_.toInt)
      // (a, b) = Tuple2 mit elementen a und b
      (re, im)
    }

    val sig1 = sig.map(mag)

    val siga = fftPrepare(sig)

    // die FFT algorithmen ersetzen den array inhalt; daher machen wir vorher eine kopie;
    // die FFT wird mit der logischen vector groesse initialisiert (array length / 2)
    val fft = new edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D(siga.size / 2)
    val sigf = siga.clone()
    fft.complexForward(sigf)

    // wiederum magnituden berechnen
    val sigfm = sigf.toVector.mapPairs { (re, im) => math.sqrt(re*re + im*im) }
    //  sigfm.plot()

    // TODO: sigfm.size ist sigf.size - 1; wo bleibt der eine wert??

    //////////////////////////////////////////////
    // peak suche
    // form von glaettung; z.b. moving average, moving local max

    // sliding(<window-size>, <step-size>), produziert einen iterator der
    // einem moving-window entspricht. fuer jede iteration erhaelt man
    // <window-size> elemente, und der pointer bewegt sich um <step-size> vorwaerts

    // lokales maximum an der stelle x: a(x-1) < a(x) > a(x+1)

    // da das naechste fenster lok. maxima des vorherigen fenster ersetzen koennen muss,
    // fuegen wir den laufenden index der sequenz hinzu

    // an sich funktioniert die suche, es tauchen aber noch viele kleine peaks auf
    val sigfmp = findPeaks(sigfm, winSize = 16)
    // sigfm.plot()
    // flatSignal(sigfmp, sigfm.size).plot()

    // 4e6 guenstiger threshold in diesem fall
    val sigfmpt = thresh(sigfmp, 4e6)
    val peakplot = flatSignal(sigfmpt, sigfm.size).plot(frame = false)

    // peakplot.frame.dispose()

    def mapFreqAmp(in: Vector[(Int, Double)], size: Int): Vector[(Double, Double)] = {
      val maxAmp = in.map(_._2).max
      in.map { case (xi, x) =>
        val freq = xi.toDouble.linexp(0, size, 100, 4000)
        val amp  = x / maxAmp
        (freq, amp)
      }
    }

    val fa = mapFreqAmp(sigfmpt, size = sigfm.size)

    (peakplot,fa,sigfmpt)
  }

  /*---
  val (testplot,fa,sigfmpt) = loadFile(p)

  val x = play {
    val lo = "lo".kr(0)
    val hi = "hi".kr(0)
    val sins = fa.zipWithIndex.map {
      case ((freq, amp), i) =>
        val inRange = i >= lo & i < hi
        val amp2 = inRange.linexp(0,1,-20.dbamp,-6.dbamp)
        SinOsc.ar(freq) * amp * amp2
    }
    Pan2.ar(Mix(sins) / fa.size)
  }

  x.set("lo" -> 1, "hi" -> 2)
  */

  // GUI

  import scala.swing._
  import Swing._

  val mainFrame = new Frame {
    title = "NMR Sonification"
    var synth = Option.empty[Synth]
    val filenameText = new TextField(p)
    val boxPanel: BoxPanel = new BoxPanel(Orientation.Vertical) {
      contents += new GridPanel(0,3) {
        contents += new Label("Filename:")
        contents += new BoxPanel(Orientation.Horizontal) {
          contents += filenameText
          contents += new Button("Choose...") {
            listenTo(this)
            reactions += {
              case event.ButtonClicked(_) =>
                val filedlg = new FileChooser()
                val result = filedlg.showOpenDialog(null)
                if (result == FileChooser.Result.Approve) {
                  println("Selected: " + filedlg.selectedFile)
                  filenameText.text = filedlg.selectedFile.toString()
                }
            }
          }
        }
        contents += new Button("Load File") {
          listenTo(this)
          reactions += {
            case event.ButtonClicked(_) =>
              val (plot,fa,sigfmpt) = loadFile(filenameText.text)
              boxPanel.contents += plot.component
              boxPanel.revalidate()
              pack()

              synth = Some(play {
                val lo = "lo".kr(0)
                val hi = "hi".kr(0)
                val sins = fa.zipWithIndex.map {
                  case ((freq, amp), i) =>
                    val inRange = i >= lo & i < hi
                    val amp2 = inRange.linexp(0,1,-20.dbamp,-6.dbamp)
                    SinOsc.ar(freq) * amp * amp2
                }
                Pan2.ar(Mix(sins) / fa.size)
              })

              // plot.listenTo(plot)
              plot.reactions += {
                case Plot.Moved(_, _, pt) =>
                  val peak = findPeak(sigfmpt,pt.getX)
                  val i = sigfmpt.indexOf(peak)
                  val y = pt.getY
                  println(s"$i ${pt.getX}")
                  synth.foreach(x => x.set("lo" -> i, "hi" -> (i+1)))
                // case other => println("Huh? " + other.getClass)
              }
          }
        }
      }
    }
    contents = boxPanel
    size = (1000,750)
    listenTo(this)
    reactions += {
      case event.WindowClosing(_) =>
        synth.foreach(_.free())
    }
    open()
  }
}
