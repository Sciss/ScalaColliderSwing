package de.sciss.synth.swing

import de.sciss.desktop.impl.{WindowHandlerImpl, SwingApplicationImpl, WindowImpl}
import de.sciss.desktop.{OptionPane, FileDialog, RecentFiles, KeyStrokes, LogPane, Desktop, Window, WindowHandler, Menu}
import javax.swing.{UIManager, SwingUtilities}
import bibliothek.gui.dock.common.{CLocation, DefaultSingleCDockable, CControl}
import scala.swing.Swing._
import java.awt.GraphicsEnvironment
import scala.swing.{Swing, Orientation, BoxPanel, Component, BorderPanel, ScrollPane, EditorPane}
import org.fit.cssbox.swingbox.{BrowserPane, SwingBoxEditorKit}
import org.fusesource.scalamd.Markdown
import bibliothek.gui.dock.common.theme.ThemeMap
import de.sciss.scalainterpreter.{Style, InterpreterPane, Interpreter, CodePane}
import scala.tools.nsc.interpreter.NamedParam
import java.awt.event.KeyEvent
import java.awt.geom.AffineTransform
import scala.util.control.NonFatal
import de.sciss.file._
import de.sciss.synth.Server

object Main extends SwingApplicationImpl("ScalaCollider") {
  type Document = Unit

  override lazy val windowHandler: WindowHandler = new WindowHandlerImpl(this, menuFactory) {
    override def usesInternalFrames: Boolean = false
  }

  private val bodyUrl = "https://raw2.github.com/Sciss/ScalaCollider/master/README.md"
  private val cssUrl  = "https://gist.github.com/andyferra/2554919/raw/2e66cabdafe1c9a7f354aa2ebf5bc38265e638e5/github.css"

  // is this the best way?
  private def readURL(url: String): String = io.Source.fromURL(url, "UTF-8").getLines().mkString("\n")

  private lazy val sp = new ServerStatusPanel

  private lazy val lg = LogPane(rows = 12)

  private lazy val codePane = sip.codePane

  private lazy val repl: REPLSupport = new REPLSupport(sp, null)

  private lazy val sip: InterpreterPane = {
    //      val paneCfg = InterpreterPane.Config()
    // note: for the auto-completion in the pane to work, we must
    // import de.sciss.synth.ugen._ instead of ugen._
    // ; also name aliasing seems to be broken, thus the stuff
    // in de.sciss.osc is hidden

    val codeCfg = CodePane.Config()
    codeCfg.style = Style.Light

    val intpCfg = Interpreter.Config()
    intpCfg.imports = List(
      //         "Predef.{any2stringadd => _}",
      "scala.math._",                     // functions such as cos(), random, constants such as Pi
      "de.sciss.file._",                  // simple file path construction, constants such as userHome
      // "scalax.chart.api._",               // simple plotting
    // "scalax.chart._","scalax.chart.Charting._",  // for version 0.3.0
      "de.sciss.osc",                     // import osc namespace, e.g. osc.Message
      "de.sciss.osc.{TCP, UDP}",
      "de.sciss.osc.Dump.{Off, Both, Text}",
      "de.sciss.osc.Implicits._",
      "de.sciss.synth._",                     // main ScalaCollider stuff
      "de.sciss.synth.Ops._",                 // imperative resource commands
      "de.sciss.synth.swing.SynthGraphPanel._",
      "de.sciss.synth.swing.Implicits._",     // ScalaCollider swing extensions
      "de.sciss.synth.swing.AppFunctions._",  // ScalaCollider swing app extensions
      "de.sciss.synth.swing.Plotting._",      // ScalaCollider swing app extensions
      "de.sciss.synth.ugen._",                // UGens
      "replSupport._"                         // REPL bindings
    )
    // intpCfg.quietImports = false

    intpCfg.bindings = List(NamedParam("replSupport", repl))
    // intpCfg.out = Some(lp.writer)

    InterpreterPane(interpreterConfig = intpCfg, codePaneConfig = codeCfg)
  }

  private def initPrefs(): Unit = {
    def updateProgramPath(): Unit = {
      val file = Prefs.superCollider.getOrElse(Prefs.defaultSuperCollider)
      val path = if (file == Prefs.defaultSuperCollider) Server.defaultProgramPath else file.path
      repl.config.programPath = path
    }

    def updateAudioDevice(): Unit = {
      val audioDevice = Prefs.audioDevice.getOrElse(Prefs.defaultAudioDevice)
      val opt = if (audioDevice == Prefs.defaultAudioDevice) None else Some(audioDevice)
      repl.config.deviceName = opt
    }

    def updateNumOutputs(): Unit =
      repl.config.outputBusChannels = Prefs.audioNumOutputs.getOrElse(Prefs.defaultAudioNumOutputs)

    Prefs.superCollider   .addListener { case _ => updateProgramPath() }
    Prefs.audioDevice     .addListener { case _ => updateAudioDevice() }
    Prefs.audioNumOutputs .addListener { case _ => updateNumOutputs () }

    updateProgramPath()
    updateAudioDevice()
    updateNumOutputs ()
  }

  private class MainWindow extends WindowImpl {
    def handler: WindowHandler = Main.windowHandler
    title     = "ScalaCollider"
    // contents  = bp
    closeOperation = Window.CloseExit

    def init(c: Component): Unit = {
      contents  = c
      bounds    = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
      front()
    }
  }

  private lazy val frame = new MainWindow

  private lazy val hp: EditorPane = {
    val md    = readURL(bodyUrl)
    val css   = readURL(cssUrl )
    val html  = Markdown(md)

    val html1 = s"<html><head><style>$css</style></head><body>$html</body></html>"
    new EditorPane("text/html", "") {
      editable  = false
      editorKit = new SwingBoxEditorKit()
      text      = html1
    }

    //    new EditorPane("text/html", "") {
    //      override lazy val peer: javax.swing.JEditorPane = new BrowserPane with SuperMixin
    //      text = html1
    //    }
  }

  override protected def init(): Unit = {
    // val _test = getClass.getResourceAsStream("""/de/sciss/synth/Server.html""")
    // println(s"stream: '${_test}'")

    //    if (Desktop.isLinux) {
    //      // UIManager.getInstalledLookAndFeels.foreach(println)
    //      UIManager.getInstalledLookAndFeels.find(_.getName.contains("GTK+")).foreach { info =>
    //        UIManager.setLookAndFeel(info.getClassName)
    //      }
    //    }
    try {
      UIManager.setLookAndFeel(Prefs.lookAndFeel.getOrElse(Prefs.defaultLookAndFeel).getClassName)
    } catch {
      case NonFatal(e) => e.printStackTrace()
    }

    super.init()

    val jf  = SwingUtilities.getWindowAncestor(frame.component.peer.getRootPane).asInstanceOf[javax.swing.JFrame]
    val ctl = new CControl(jf)
    val th  = ctl.getThemes
    th.select(ThemeMap.KEY_FLAT_THEME)

    val bot   = new BoxPanel(Orientation.Horizontal)
    bot.contents += Swing.HGlue
    bot.contents += sp

    val bp = new BorderPanel {
      add(Component.wrap(ctl.getContentArea), BorderPanel.Position.Center)
      add(bot, BorderPanel.Position.South)
    }

    //    val spd   = new DefaultSingleCDockable("server-status", "Server Status", sp.peer)
    //    spd.setLocation(CLocation.base().normalSouth(0.05))
    //    spd.setResizeLockedVertically(true)

    // val sif   = new ScalaInterpreterFrame(repl)

    val sid = new DefaultSingleCDockable("interpreter", "Interpreter", sip.component)
    sid.setLocation(CLocation.base().normalWest(0.6))

    lg.makeDefault()
    Console.setOut(lg.outputStream)
    Console.setErr(lg.outputStream)

    // lg.background = sif.pane.codePane.component.getBackground
    // lg.foreground = sif.pane.codePane.component.getForeground
    val lgd   = new DefaultSingleCDockable("log", "Log", lg.component.peer)
    lgd.setLocation(CLocation.base().normalSouth(0.25))

    // hp.peer.setPage("http://www.sciss.de/scalaCollider")
    val hps   = new ScrollPane(hp)
    val hpd   = new DefaultSingleCDockable("help", "Help", hps.peer)
    hpd.setLocation(CLocation.base().normalNorth(0.65))

    ctl.addDockable(lgd)
    // ctl.addDockable(spd)
    ctl.addDockable(hpd)
    ctl.addDockable(sid)
    lgd.setVisible(true)
    // spd.setVisible(true)
    hpd.setVisible(true)
    sid.setVisible(true)

    frame.init(bp)

    initPrefs()
  }

  private def newFile(): Unit = {
    println("TODO: New")
  }

  private def openFile(): Unit = {
    val dlg = FileDialog.open()
    dlg.show(Some(frame)).foreach { file =>
      println("TODO: Open")
    }
  }

  private def bootServer(): Unit =
    if (sp.booting.isEmpty && sp.server.isEmpty) rebootServer()

  private def rebootServer() :Unit =
    sp.peer.boot()

  private def serverMeters() :Unit = {
    import de.sciss.synth.swing.Implicits._
    sp.server.foreach { s =>
      val f = s.gui.meter()
      f.peer.setAlwaysOnTop(true)
    }
  }

  private def stop(): Unit = {
    import de.sciss.synth.Ops._
    sp.server.foreach(_.defaultGroup.freeAll())
  }

  private def clearLog(): Unit = lg.clear()

  private def dumpNodes(controls: Boolean): Unit = {
    import de.sciss.synth.Ops._
    sp.server.foreach(_.dumpTree(controls = controls))
  }

  private var fntSizeAmt = 0

  private def fontSizeChange(rel: Int): Unit = {
    fntSizeAmt += rel
    updateFontSize()
  }

  private def updateFontSize(): Unit = {
    val ed    = codePane.editor
    val fnt   = ed.getFont
    val scale = math.pow(1.08334, fntSizeAmt)
    // note: deriveFont _replaces_ the affine transform, does not concatenate it
    ed.setFont(fnt.deriveFont(AffineTransform.getScaleInstance(scale, scale)))
  }

  private def fontSizeReset(): Unit = {
    fntSizeAmt = 0
    updateFontSize()
  }

  private lazy val _recent = RecentFiles(Main.userPrefs("recent-docs")) { folder =>
    println("TODO: Open")
    // perform(folder)
  }

  private def lookUpHelp(): Unit = {
    println("(TODO: lookUpHelp()")
    val url = getClass.getResource("""/de/sciss/synth/Server.html""")
    if (url != null) {
      // --- version 1: shows as plain text ---
      // hp.peer.setPage(url)

      // --- version 2: kind of works (doesn't find css and scripts though) ---
      // hp.text = io.Source.fromURL(url, "UTF-8").mkString

      // --- version 3: creates new document (again doesn't find css and scripts) ---
      val doc = hp.editorKit.createDefaultDocument()
      hp.editorKit.read(url.openStream(), doc, 0)
      hp.peer.setDocument(doc)
    }
    else println("!Help sources not found!")
  }

  protected lazy val menuFactory: Menu.Root = {
    import Menu._
    import KeyStrokes._
    import KeyEvent._
    import de.sciss.synth.swing.{Main => App}

    val itAbout = Item.About(App) {
      val html =
        s"""<html><center>
           |<font size=+1><b>About $name</b></font><p>
           |Copyright (c) 2008&ndash;2014 Hanns Holger Rutz. All rights reserved.<p>
           |This software is published under the GNU General Public License v2+
           |""".stripMargin
      OptionPane.message(message = new javax.swing.JLabel(html)).show(Some(frame))
    }
    val itPrefs = Item.Preferences(App)(ActionPreferences())
    val itQuit  = Item.Quit(App)

    val gFile = Group("file", "File")
      .add(Item("new" )("New"     -> (menu1 + VK_N))(newFile ()))
      .add(Item("open")("Open..." -> (menu1 + VK_O))(openFile()))
      .add(_recent.menu)
      .addLine()
      .add(Item("close", proxy("Close" -> (menu1 + VK_W))))
      // .add(Item("close-all", actionCloseAll))
      .add(Item("save", proxy("Save" -> (menu1 + VK_S))))

    if (itQuit.visible) gFile.addLine().add(itQuit)

    val gEdit = Group("edit", "Edit")
      .add(Item("undo", proxy("Undo" -> (menu1 + VK_Z))))
      .add(Item("redo", proxy("Redo" -> (menu1 + shift + VK_Z))))

    if (itPrefs.visible /* && Desktop.isLinux */) gEdit.addLine().add(itPrefs)

    val gView = Group("view", "View")
      .add(Item("inc-font"    )("Enlarge Font"    -> (menu1 + VK_PLUS ))(fontSizeChange( 1)))
      .add(Item("dec-font"    )("Shrink Font"     -> (menu1 + VK_MINUS))(fontSizeChange(-1)))
      .add(Item("reset-font"  )("Reset Font Size" -> (menu1 + VK_0    ))(fontSizeReset()))

    val gActions = Group("actions", "Actions")
      .add(Item("boot-server"   )("Boot Server"       -> (menu1 + VK_B))(bootServer()))
      .add(Item("reboot-server" )("Reboot Server"                      )(rebootServer()))
      .add(Item("server-meters" )("Show Server Meter" -> (menu1 + VK_M))(serverMeters()))
      .add(Item("dump-tree"     )("Dump Node Tree"               -> (menu1         + VK_T))(dumpNodes(controls = false)))
      .add(Item("dump-tree-ctrl")("Dump Node Tree with Controls" -> (menu1 + shift + VK_T))(dumpNodes(controls = true )))
      .addLine()
      .add(Item("stop")("Stop" -> (menu1 + VK_PERIOD))(stop()))
      .addLine()
      .add(Item("clear-log")("Clear Log Window" -> (menu1 + shift + VK_P))(clearLog()))

    val gHelp = Group("help", "Help")
      .add(Item("help-for-cursor")("Look up Documentation for Cursor" -> (menu1 + VK_D))(lookUpHelp()))

    if (itAbout.visible) gHelp.addLine().add(itAbout)

    Root().add(gFile).add(gEdit).add(gView).add(gActions).add(gHelp)
  }
}
