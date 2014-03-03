package de.sciss.synth.swing

import de.sciss.desktop.impl.{WindowHandlerImpl, SwingApplicationImpl, WindowImpl}
import de.sciss.desktop.{LogPane, Desktop, Window, WindowHandler, Menu}
import javax.swing.{UIManager, SwingUtilities}
import bibliothek.gui.dock.common.{CLocation, DefaultSingleCDockable, CControl}
import scala.swing.Swing._
import java.awt.GraphicsEnvironment

object Main extends SwingApplicationImpl("ScalaCollider") {
  type Document = Unit

  override lazy val windowHandler: WindowHandler = new WindowHandlerImpl(this, menuFactory) {
    override def usesInternalFrames: Boolean = false
  }

  override protected def init(): Unit = {
    if (Desktop.isLinux) {
      // UIManager.getInstalledLookAndFeels.foreach(println)
      UIManager.getInstalledLookAndFeels.find(_.getName.contains("GTK+")).foreach { info =>
        UIManager.setLookAndFeel(info.getClassName)
      }
    }

    super.init()
    val f: WindowImpl = new WindowImpl {
      def handler: WindowHandler = Main.windowHandler
      title   = "ScalaCollider"
      bounds  = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
      closeOperation = Window.CloseExit
    }

    val jf = SwingUtilities.getWindowAncestor(f.component.peer.getRootPane).asInstanceOf[javax.swing.JFrame]
    val ctl = new CControl(jf)
    jf.add(ctl.getContentArea)

    val sp    = new ServerStatusPanel
    val spd   = new DefaultSingleCDockable("server-status", "Server Status", sp.peer)
    spd.setLocation(CLocation.base().normalSouth(0.1))

    val repl  = new REPLSupport(sp, null)
    val sif   = new ScalaInterpreterFrame(repl)
    val sid   = new DefaultSingleCDockable("interpreter", "Interpreter", sif.pane.component)
    sid.setLocation(CLocation.base().normalWest(0.667))

    val lg    = LogPane(rows = 12)
    lg.makeDefault()
    // lg.background = sif.pane.codePane.component.getBackground
    // lg.foreground = sif.pane.codePane.component.getForeground
    val lgd   = new DefaultSingleCDockable("log", "Log", lg.component.peer)
    lgd.setLocation(CLocation.base().normalNorth(0.25))

    ctl.addDockable(spd)
    ctl.addDockable(lgd)
    ctl.addDockable(sid)
    spd.setVisible(true)
    lgd.setVisible(true)
    sid.setVisible(true)

    f.front()
  }

  protected lazy val menuFactory: Menu.Root = {
    import Menu._
    val gFile = Group("file", "File")
    val gEdit = Group("edit", "Edit")
    Root().add(gFile).add(gEdit)
  }
}
