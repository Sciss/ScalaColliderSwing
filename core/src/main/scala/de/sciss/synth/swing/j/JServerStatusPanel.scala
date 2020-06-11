/*
 *  JServerStatusPanel.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.swing
package j

import java.awt.event.ActionEvent
import java.awt.{Color, Component, Dimension, EventQueue, Graphics, Image, Toolkit}

import javax.swing.event.{AncestorEvent, AncestorListener}
import javax.swing.{AbstractAction, BorderFactory, Box, BoxLayout, Icon, JButton, JComponent, JFrame, JLabel, JPanel, JProgressBar, OverlayLayout, UIManager, WindowConstants}
import de.sciss.model.Model
import de.sciss.synth.{Server, ServerConnection, message}

object JServerStatusPanel {
  final val COUNTS      = 0x01
  final val BOOT_BUTTON = 0x02

  private val Connecting = "Connecting"

  private class CountLabel extends JLabel() {
    putClientProperty("JComponent.sizeVariant", "small")

    override def getPreferredSize: Dimension = {
      val dim = super.getPreferredSize
      dim.width = 40
      dim
    }

    override def getMinimumSize: Dimension = getPreferredSize()
    override def getMaximumSize: Dimension = getPreferredSize()
  }

  private class CPUIndicator extends JComponent {
    //		private var avgCPU  = 0f
    //		private var peakCPU = 0f
    //		private var avgW    = 0
    //		private var peakX   = 0
    private var peakCPU = 0 // 0...17

    private def getImageResource(name: String): Image =
      Toolkit.getDefaultToolkit.createImage(GUI.getClass.getResource(name))

    private val imgGaugeEmpty = getImageResource("gauge_empty.png")
    private val imgGaugeFull  = getImageResource("gauge_full.png")

    // ---- constructor ----
    {
      addAncestorListener(new AncestorListener {
        def ancestorAdded(e: AncestorEvent): Unit = ()
        def ancestorMoved(e: AncestorEvent): Unit = ()

        def ancestorRemoved(e: AncestorEvent): Unit = {
          imgGaugeEmpty.flush()
          imgGaugeFull.flush()
        }
      })
      //          setPreferredSize( new Dimension( 73, 23 ))
      val dim = new Dimension(56, 22)
      setPreferredSize(dim)
      setMaximumSize(dim)
    }

    def update(newAvgCPU: Float, newPeakCPU: Float): Unit = {
      val newPeakPix = math.max(0, math.min(54, (newPeakCPU * 18 + 0.5f).toInt * 3))

      if (newPeakPix != peakCPU) {
        peakCPU = newPeakPix
        repaint() // could use dirty rec
      }
    }

    //        private val colrBorder = new Color( 0, 0, 0, 0xB4 )
    //        private val colrEdge   = new Color( 0, 0, 0, 0x7F )
    private val colrBorder = new Color(0, 0, 0, 0x35)

    override def paintComponent(g: Graphics): Unit = {
      //			g.setColor( Color.black )
      //			val w = getWidth
      //			val h = getHeight
      //			g.fillRect(  0, 0, w, h )
      //			updateScreenCoords
      //			g.setColor( Color.yellow /* Color.blue */)
      //			g.fillRect( 1, 1, avgW, h - 2 )
      //			g.drawLine( peakX, 1, peakX, h - 2 )
      g.setColor(colrBorder)
      g.drawRect(0, 0, 55, 21)
      g.drawRect(1, 0, 53, 21)
      g.drawRect(0, 1, 55, 19)
      g.drawImage(imgGaugeFull, 1, 1,
        peakCPU + 1, 21, 0, 0, peakCPU, 20,
        Color.black, this)
      g.drawImage(imgGaugeEmpty, peakCPU + 1, 1,
        55, 21, peakCPU, 0, 54, 20,
        Color.black, this)
    }
  }
}

class JServerStatusPanel(flags: Int) extends JPanel {
  import JServerStatusPanel._

  def this(s: Server, flags: Int) = {
    this(flags)
    server = Some(s)
  }

  def this(s: Server) =
    this(s, 0x03) // XXX weird scala bug... does not see COUNTS and BOOT_BUTTON

  def this() =
    this(0x03)

  private[this] val actionBoot  = new ActionBoot()
  private[this] val ggBoot      = new JButton(actionBoot)
  private[this] val ggBusy      = new JProgressBar()

  // subclasses may override this
  protected def txtBoot    = "Boot"  // XXX getResource
  protected def txtStop    = "Stop"  // XXX getResource
  protected def frameTitle = "Server Status"

  private[this] val lbCPU       = new CPUIndicator
  private[this] val lbNumUGens  = new CountLabel
  private[this] val lbNumSynths = new CountLabel
  private[this] val lbNumGroups = new CountLabel
  private[this] val lbNumDefs   = new CountLabel

  private[this] val sync = new AnyRef

  private[this] val bootingUpdate: ServerConnection.Listener = {
    case ServerConnection.Running(srv) =>
      server_=(Some(srv))
      updateCounts(srv.counts)

    case ServerConnection.Aborted =>
      clearCounts()
      booting_=(None)
      actionBoot.serverUpdate(Server.Offline)

    //      case msg => actionBoot.serverUpdate( msg )
  }

  private[this] val serverUpdate: Model.Listener[Any] = {
    case Server.Counts(cnt) => if (isShowing) updateCounts(cnt)

    case msg @ Server.Offline =>
      clearCounts()
      server_=(None)
      actionBoot.serverUpdate(msg)

    case msg => actionBoot.serverUpdate(msg)
  }

  private[this] var _server = Option.empty[Server]

  def server: Option[Server] = sync.synchronized {
    _server
  }
  def server_=(s: Option[Server]): Unit =
    sync.synchronized {
      if (_server != s) {
        val wasListening = listening
        if (wasListening) stopListening()
        _server = s
        _booting = None
        updateFrameTitle()
        if (wasListening) startListening()
      }
    }

  private[this] var _booting = Option.empty[ServerConnection]

  def booting: Option[ServerConnection] = sync.synchronized {
    _booting
  }
  def booting_=(b: Option[ServerConnection]): Unit =
    sync.synchronized {
      if (_booting != b) {
        val wasListening = listening
        if (wasListening) stopListening()
        _server   = None
        _booting  = b
        updateFrameTitle()
        if (wasListening) startListening()
      }
    }

  private[this] var _bootAction = Option.empty[() => Unit]

  def bootAction: Option[() => Unit] = sync.synchronized {
    _bootAction
  }
  def bootAction_=(a: Option[() => Unit]): Unit =
    sync.synchronized {
      val wasListening = listening
      if (wasListening) stopListening()
      _bootAction = a
      if (wasListening) startListening()
    }

  // ---- constructor ----
  {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS))

    val colr      = {
      val res = UIManager.getColor("Label.foreground")
      if (res == null) Color.black else res
      // getForeground
    }
    val icnGroup  = Shapes.Icon(extent = 16, fill = colr)(Shapes.Group   )
    val icnSynth  = Shapes.Icon(extent = 16, fill = colr)(Shapes.Synth   )
    val icnUGen   = Shapes.Icon(extent = 16, fill = colr)(Shapes.UGen    )
    val icnDef    = Shapes.Icon(extent = 16, fill = colr)(Shapes.SynthDef)

    def addS(c: Component, gap: Int = 4): Unit = {
      add(c)
      add(Box.createHorizontalStrut(gap))
    }

    if ((flags & BOOT_BUTTON) != 0) {
      ggBoot.setFocusable(false) // prevent user from accidentally starting/stopping server
      ggBoot.putClientProperty("JButton.buttonType", "bevel")
      ggBoot.putClientProperty("JComponent.sizeVariant", "small")
      ggBoot.setText(txtStop)
      val d1 = ggBoot.getPreferredSize
      ggBoot.setText(txtBoot)
      val d2 = ggBoot.getPreferredSize
      ggBoot.setPreferredSize(new Dimension(math.max(d1.width, d2.width), math.max(d1.height, d2.height)))

      ggBusy.setIndeterminate(true)
      val busyDim = new Dimension(24, 24)
      ggBusy.setPreferredSize(busyDim)
      ggBusy.setMaximumSize  (busyDim)
      ggBusy.putClientProperty("JProgressBar.style", "circular")

      addS(ggBoot, 2)
      val busyBox = new JPanel()
      busyBox.setLayout(new OverlayLayout(busyBox))
      busyBox.add(Box.createRigidArea(busyDim))
      busyBox.add(ggBusy)
      addS(busyBox, 6)

      setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2))
    } else {
      setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2))
    }

    if ((flags & COUNTS) != 0) {
      addS(lbCPU, 8)
      def addCount(icn: Icon, lb: JLabel, tooltip: String, s: Int = 4): Unit = {
        val lb2 = new JLabel(icn)
        lb2.putClientProperty("JComponent.sizeVariant", "small")
        lb .setToolTipText(tooltip)
        lb2.setToolTipText(tooltip)
        addS(lb2)
        addS(lb, s)
      }
      addCount(icnGroup, lbNumGroups, "Groups")
      addCount(icnSynth, lbNumSynths, "Synths")
      addCount(icnUGen , lbNumUGens , "UGens" )
      addCount(icnDef  , lbNumDefs  , "SynthDefs", 0)
    }

    addAncestorListener(new AncestorListener {
      def ancestorAdded(e: AncestorEvent): Unit =
        startListening()

      def ancestorRemoved(e: AncestorEvent): Unit =
        stopListening()

      def ancestorMoved(e: AncestorEvent): Unit = ()
    })
  }

  protected def couldBoot: Boolean = sync.synchronized {
    bootAction.isDefined
  }

  private[this] var frame: Option[JFrame] = None

  private def updateFrameTitle(): Unit =
    defer {
      sync.synchronized {
        val name = _server.getOrElse(_booting.orNull)
        frame.foreach(_.setTitle(frameTitle + (if (name == null) "" else " (" + name + ")")))
      }
    }

  def makeWindow: JFrame = makeWindow()

  def makeWindow(undecorated: Boolean = false): JFrame = {
    frame getOrElse {
      val fr = new JFrame()
      if (undecorated) fr.setUndecorated(true)
      val rp = fr.getRootPane
      rp.putClientProperty("Window.style", "small")
      rp.putClientProperty("apple.awt.brushMetalLook", true)
      fr.setResizable(false)
      fr.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE)
      fr.getContentPane.add(this)
      fr.pack()
      if (!undecorated) fr.setLocation(50, 50)
      frame = Some(fr)
      updateFrameTitle()
      fr
    }
  }

  private[this] var listening = false

  private def startListening(): Unit =
    sync.synchronized {
      if (!listening) {
        listening = true
        defer {
          val st = server.map(_.condition).getOrElse(
            if (_booting.isDefined) Connecting else Server.Offline)
          serverUpdate(st)
        }
        _booting.foreach(_.addListener(bootingUpdate))
        _server .foreach(_.addListener(serverUpdate ))
      }
    }

  private def stopListening(): Unit =
    sync.synchronized {
      if (listening) {
        listening = false
        _booting.foreach(_.removeListener(bootingUpdate))
        _server .foreach(_.removeListener(serverUpdate ))
        clearCounts()
      }
    }

  private def defer(code: => Unit): Unit = {
    if  (EventQueue.isDispatchThread) code
    else EventQueue.invokeLater(new Runnable { def run(): Unit = code })
  }

  private def updateCounts(cnt: message.StatusReply): Unit = {
    lbCPU.update(cnt.avgCPU / 100, cnt.peakCPU / 100)
    lbNumUGens  .setText(cnt.numUGens .toString)
    lbNumSynths .setText(cnt.numSynths.toString)
    lbNumGroups .setText(cnt.numGroups.toString)
    lbNumDefs   .setText(cnt.numDefs  .toString)
  }

  private def clearCounts(): Unit = {
    lbCPU.update(0, 0)
    lbNumUGens  .setText(null)
    lbNumSynths .setText(null)
    lbNumGroups .setText(null)
    lbNumDefs   .setText(null)
  }

  // subclasses may override this
  protected def bootServer(): Unit =
    sync.synchronized {
      bootAction.foreach(_.apply())
    }

  // subclasses may override this
  protected def stopServer(): Unit =
    sync.synchronized {
      server.foreach(_.quit())
    }

  private class ActionBoot extends AbstractAction {

    import Server._

    private[this] var cond: Any = Offline

    def actionPerformed(e: ActionEvent): Unit =
      if (cond == Offline) {
        bootServer()
      } else if (cond == Running) {
        stopServer()
      }

    def serverUpdate(msg: Any): Unit =
      defer {
        msg match {
          case Server.Running =>
            //println( "Running" )
            cond = msg
            ggBoot.setText(txtStop)
            ggBoot.setEnabled(true)
            ggBusy.setVisible(false)

          case Server.Offline =>
            //println( "Offline" )
            cond = msg
            ggBoot.setText(txtBoot)
            ggBoot.setEnabled(couldBoot)
            ggBusy.setVisible(false)

          case Connecting => // ServerConnection.Connecting
            //println( "Booting" )
            cond = msg
            ggBoot.setEnabled(false)
            ggBusy.setVisible(true)

          case _ =>
          //          case SuperColliderClient.ServerChanged( server ) => {
          //            serverPanel.server = server
          //          }
        }
      }
  }
  // class actionBootClass

  def boot(): Unit = bootServer()
}
