/*
 *  Main.scala
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

import java.awt.event._
import java.awt.{Color, Font, GraphicsEnvironment, KeyboardFocusManager}
import java.io.{FileOutputStream, OutputStreamWriter}
import java.net.URL
import javax.swing.event.{HyperlinkEvent, HyperlinkListener}
import javax.swing.{KeyStroke, SwingUtilities}

import bibliothek.gui.dock.common.event.CFocusListener
import bibliothek.gui.dock.common.intern.CDockable
import bibliothek.gui.dock.common.mode.ExtendedMode
import bibliothek.gui.dock.common.theme.ThemeMap
import bibliothek.gui.dock.common.{CControl, CLocation, DefaultSingleCDockable, SingleCDockable}
import bibliothek.gui.dock.dockable.IconHandling
import bibliothek.gui.dock.util.Priority
import de.sciss.audiowidgets.Util
import de.sciss.desktop.impl.{SwingApplicationImpl, WindowHandlerImpl, WindowImpl}
import de.sciss.desktop.{Desktop, DialogSource, FileDialog, KeyStrokes, LogPane, Menu, OptionPane, RecentFiles, Window, WindowHandler}
import de.sciss.file._
import de.sciss.swingplus.PopupMenu
import de.sciss.syntaxpane.TokenType
import de.sciss.synth.{Server, UGenSpec, UndefinedRate}
import de.sciss.{scalainterpreter => si}
import org.pegdown.PegDownProcessor

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.swing.event.{Key, MouseButtonEvent, MouseClicked}
import scala.swing.{Action, BorderPanel, BoxPanel, Component, EditorPane, Label, MenuItem, Orientation, ScrollPane, Swing}
import scala.tools.nsc.interpreter.NamedParam
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object Main extends SwingApplicationImpl[TextViewDockable]("ScalaCollider") {
  type Document = TextViewDockable

  override lazy val windowHandler: WindowHandler = new WindowHandlerImpl(this, menuFactory) {
    override def usesInternalFrames: Boolean = false
  }

  private lazy val sp = new ServerStatusPanel

  // XXX TODO -- should be made public in ScalaInterpreterPane
  private def defaultFonts = Seq[(String, Int)](
    "Menlo"                     -> 12,
    "DejaVu Sans Mono"          -> 12,
    "Bitstream Vera Sans Mono"  -> 12,
    "Monaco"                    -> 12,
    "Anonymous Pro"             -> 12
  )

  private def logFont(): Font = {
    val list          = defaultFonts
    val allFontNames  = GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames
    val (fontName, fontSize) = list.find(spec => allFontNames.contains(spec._1))
      .getOrElse("Monospaced" -> 12)

    new Font(fontName, Font.PLAIN, /*if( isMac )*/ fontSize /*else fontSize * 3/4*/)
  }

  private lazy val lg = {
    val cn          = Prefs.colorScheme.getOrElse(Prefs.ColorSchemeNames.default)
    val style       = Prefs.ColorSchemeNames(cn)
    val res         = LogPane(rows = 12)
    res.background  = style.background
    res.foreground  = style.foreground
    res.font        = logFont() // new Font("Monospaced", Font.PLAIN, 12)  // XXX TODO: should be preferences setting
    res
  }

  private lazy val repl: REPLSupport = new REPLSupport(sp, null)

  private lazy val intpFut: Future[Interpreter] = {
    val intpCfg = si.Interpreter.Config()
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
      "de.sciss.kollflitz.Ops._",
      "de.sciss.kollflitz.RandomOps._",
      "de.sciss.synth._",                     // main ScalaCollider stuff
      "de.sciss.synth.Ops._",                 // imperative resource commands
//      "de.sciss.synth.swing.SynthGraphPanel._",
      "de.sciss.synth.swing.Implicits._",     // ScalaCollider swing extensions
      "de.sciss.synth.swing.AppFunctions._",  // ScalaCollider swing app extensions
      "de.sciss.synth.swing.Plotting.Implicits._", // ScalaCollider swing plotting extensions
      "de.sciss.synth.ugen._",                // UGens
      "replSupport._"                         // REPL bindings
    )
    // intpCfg.quietImports = false

    intpCfg.bindings = List(NamedParam("replSupport", repl))
    // intpCfg.out = Some(lp.writer)

    val res = Interpreter(intpCfg)
    import ExecutionContext.Implicits.global
    res.onComplete {
      case Success(_) => println("Interpreter ready.")
      case Failure(e) =>
        System.err.println("Interpreter could not be initialized:")
        e.printStackTrace()
    }
    res
  }

  def interpreter: Future[Interpreter] = intpFut

  private def initPrefs(): Unit = {
    def updateProgramPath(): Unit = {
      val file = Prefs.superCollider.getOrElse(Prefs.defaultSuperCollider)
      val path = if (file == Prefs.defaultSuperCollider) Server.defaultProgram else file.path
      repl.config.program = path
    }

    def updateAudioDevice(): Unit = {
      val audioDevice = Prefs.audioDevice.getOrElse(Prefs.defaultAudioDevice)
      val opt = if (audioDevice == Prefs.defaultAudioDevice) None else Some(audioDevice)
      repl.config.deviceName = opt
    }

    def updateNumInputs(): Unit =
      repl.config.inputBusChannels  = Prefs.audioNumInputs .getOrElse(Prefs.defaultAudioNumInputs )

    def updateNumOutputs(): Unit =
      repl.config.outputBusChannels = Prefs.audioNumOutputs.getOrElse(Prefs.defaultAudioNumOutputs)

    Prefs.superCollider   .addListener { case _ => updateProgramPath() }
    Prefs.audioDevice     .addListener { case _ => updateAudioDevice() }
    Prefs.audioNumInputs  .addListener { case _ => updateNumInputs  () }
    Prefs.audioNumOutputs .addListener { case _ => updateNumOutputs () }

    updateProgramPath()
    updateAudioDevice()
    updateNumInputs  ()
    updateNumOutputs ()
  }

  private class MainWindow extends WindowImpl {
    def handler: WindowHandler = Main.windowHandler
    title     = "ScalaCollider"
    // contents  = bp
    closeOperation = Window.CloseIgnore

    reactions += {
      case Window.Closing(_) =>
        import ExecutionContext.Implicits.global
        Desktop.mayQuit().foreach(_ => quit())
    }

    def init(c: Component): Unit = {
      contents = c
      delegate.component.peer match {
        case jf: java.awt.Frame =>
          // if we do not set a size, on Debian 9 / GNOME, moving the window
          // out of maximized state results in zero width frame
          size = delegate.component.preferredSize
          jf.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH)
        case _ =>
          bounds = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
      }
      front()
    }
  }

  private lazy val frame = new MainWindow

  def mainWindow: Window = frame

  def dockControl: CControl = dockCtrl

  def isDarkSkin: Boolean = Util.isDarkSkin

  private lazy val dockCtrl: CControl = {
    val jf  = SwingUtilities.getWindowAncestor(frame.component.peer.getRootPane).asInstanceOf[javax.swing.JFrame]
    val res = new CControl(jf)
    val th  = res.getThemes
    th.select(ThemeMap.KEY_FLAT_THEME)
    val controller = res.getController
    import Priority.CLIENT

    if (isDarkSkin) {
      val colors = controller.getColors
      val c0 = new Color(50, 56, 62)
      colors.put(CLIENT, "title.active.left", c0)
      colors.put(CLIENT, "title.flap.active", c0)
      colors.put(CLIENT, "title.flap.active.knob.highlight", c0)
      colors.put(CLIENT, "title.flap.active.knob.shadow", c0)
      // val c1 = new Color(48, 48, 48)
      val c2 = new Color(64, 64, 64)
      colors.put(CLIENT, "title.active.right", c2)
      val c3 = new Color(220, 220, 200)
      colors.put(CLIENT, "title.active.text", c3)
      colors.put(CLIENT, "title.flap.active.text", c3)
      val c4 = Color.gray
      colors.put(CLIENT, "title.inactive.text", c4)
      colors.put(CLIENT, "title.flap.inactive.text", c4)
      val c5 = c2 // Color.darkGray
      colors.put(CLIENT, "title.flap.selected", c5)
      colors.put(CLIENT, "title.flap.selected.knob.highlight", c5)
      colors.put(CLIENT, "title.flap.selected.knob.shadow", c5)
      val c6 = new Color(64, 64, 64, 64)
      colors.put(CLIENT, "title.inactive.left", c6)
      colors.put(CLIENT, "title.inactive.right", c6)
      colors.put(CLIENT, "title.flap.inactive", c6)
      colors.put(CLIENT, "title.flap.inactive.knob.highlight", c6)
      colors.put(CLIENT, "stack.tab.background.top", c6)
      colors.put(CLIENT, "stack.tab.background.bottom", c6)
      colors.put(CLIENT, "stack.tab.background.top.focused", c0)
      colors.put(CLIENT, "stack.tab.background.bottom.focused", c2)

      colors.put(CLIENT, "stack.tab.background.top.selected", c6)  // unfocused
      colors.put(CLIENT, "stack.tab.background.bottom.selected", c2)
      // c.put(CLIENT, "stack.tab.background.top.disabled", ...)
      // c.put(CLIENT, "stack.tab.background.bottom.disabled", ...)

      // c.put(CLIENT, "paint", ...)
      // c.put(CLIENT, "paint.insertion.area", ...)
      // c.put(CLIENT, "paint.removal", ...)

      val c8 = new Color(16, 16, 16)
      colors.put(CLIENT, "stack.tab.border", c8)
      colors.put(CLIENT, "stack.tab.border.center.focused", c8)
      colors.put(CLIENT, "stack.tab.border.center.selected", c8)
      colors.put(CLIENT, "stack.tab.border.center.disabled", c8)
      colors.put(CLIENT, "stack.tab.border.center", c8)
      colors.put(CLIENT, "stack.tab.border.out", c8)
      colors.put(CLIENT, "stack.tab.border.out.focused", c8)
      colors.put(CLIENT, "stack.tab.border.out.selected", c8)
      // c.put(CLIENT, "stack.tab.foreground", ...)
    }
    val icons         = controller.getIcons
    val colrIcon      = if (isDarkSkin) Color.lightGray /* new Color(220, 220, 200) */ else Color.darkGray
    val iconDock      = Shapes.Icon(fill = colrIcon, extent = 16)(Shapes.Document  )
    val iconMin       = Shapes.Icon(fill = colrIcon, extent = 16)(Shapes.Minimize  )
    val iconMax       = Shapes.Icon(fill = colrIcon, extent = 16)(Shapes.Maximize  )
    val iconNorm      = Shapes.Icon(fill = colrIcon, extent = 16)(Shapes.Normalized)
    val iconExt       = Shapes.Icon(fill = colrIcon, extent = 16)(Shapes.Layered   )
    val iconPinned    = Shapes.Icon(fill = colrIcon, extent = 16)(Shapes.Pinned    )
    val iconNotPinned = Shapes.Icon(fill = colrIcon, extent = 16)(Shapes.NotPinned )
    icons.put(CLIENT, "dockable.default"                        , iconDock      )
    icons.put(CLIENT, "locationmanager.minimize"                , iconMin       )
    icons.put(CLIENT, "locationmanager.maximize"                , iconMax       )
    icons.put(CLIENT, "locationmanager.normalize"               , iconNorm      )
    icons.put(CLIENT, "locationmanager.externalize"             , iconExt       )
    icons.put(CLIENT, "locationmanager.unexternalize"           , iconNorm      )
    icons.put(CLIENT, "locationmanager.unmaximize_externalized" , iconExt       )
    icons.put(CLIENT, "flap.hold"                               , iconPinned    )
    icons.put(CLIENT, "flap.free"                               , iconNotPinned )

    // res.getController.getFonts
    // borders?

    res.addMultipleDockableFactory("de.sciss.synth.swing.TextView", TextViewDockable.factory)

    res.addFocusListener(new CFocusListener {
      def focusLost(dockable: CDockable): Unit = dockable match {
        case _: TextViewDockable =>
          fileActions.foreach(_.view = None)
        case _ =>
      }

      def focusGained(dockable: CDockable): Unit = dockable match {
        case tvd: TextViewDockable =>
          val viewOpt = Some(tvd)
          fileActions.foreach(_.view = viewOpt)
          documentHandler.activeDocument = viewOpt
        case _ =>
      }
    })

    res
  }

  private var helpHistory     = Vector.empty[String]
  private var helpHistoryIdx  = 0

  private lazy val helpEditor: EditorPane = {
    val res = new EditorPane("text/html", "") {
      editable = false
      border = Swing.EmptyBorder(8)

      peer.addHyperlinkListener(new HyperlinkListener {
        def hyperlinkUpdate(e: HyperlinkEvent): Unit = {
          if (e.getEventType == HyperlinkEvent.EventType.ACTIVATED) {
            // println(s"description: ${e.getDescription}")
            // println(s"source elem: ${e.getSourceElement}")
            // println(s"url        : ${e.getURL}")
            val link = e.getDescription
            val ident = if (link.startsWith("ugen.")) link.substring(5) else link
            lookUpHelp(ident)
          }
        }
      })

      listenTo(mouse.clicks)
      reactions += {
        case e: MouseButtonEvent if e.triggersPopup && helpHistory.nonEmpty =>
          val pop = new PopupMenu {
            if (helpHistoryIdx > 0) contents += new MenuItem(Action("Back") {
              val idx = helpHistoryIdx - 1
              if (idx >= 0 && idx < helpHistory.size) {
                helpHistoryIdx = idx
                lookUpHelp(helpHistory(idx), addToHistory = false)
              }
            })
            if (helpHistoryIdx < helpHistory.size - 1) contents += new MenuItem(Action("Forward") {
              val idx = helpHistoryIdx + 1
              if (idx < helpHistory.size) {
                helpHistoryIdx = idx
                lookUpHelp(helpHistory(idx), addToHistory = false)
              }
            })
          }
          pop.show(this, e.point.x - 4, e.point.y - 4)
      }
    }
    res
  }

  private lazy val helpDockable: SingleCDockable = {
    val scroll  = new ScrollPane(helpEditor)
    scroll.peer.putClientProperty("styleId", "undecorated")
    val res     = new DefaultSingleCDockable("help", "Help", scroll.peer)
    res.setLocation(CLocation.base().normalEast(0.0333).north(0.75)) // .east(0.333)
    res.setTitleIconHandling(IconHandling.KEEP_NULL_ICON) // this must be called before setTitleIcon
    res.setTitleIcon(null)
    dockCtrl.addDockable[SingleCDockable](res)
    res
  }

  override protected def init(): Unit = {
    GUI.windowOnTop = true

    try {
      val lafInfo = Prefs.lookAndFeel.getOrElse {
        val res = Prefs.LookAndFeel.default
        Prefs.lookAndFeel.put(res)
        res
      }
      lafInfo.install()
    } catch {
      case NonFatal(e) => e.printStackTrace()
    }

    super.init()

    val bot = new BoxPanel(Orientation.Horizontal)
    bot.contents += Swing.HGlue
    bot.contents += sp

    val bp = new BorderPanel {
      add(Component.wrap(dockCtrl.getContentArea), BorderPanel.Position.Center)
      add(bot, BorderPanel.Position.South)
    }

    lg.makeDefault()
    // no longer needed (was perhaps Scala 2.10):
//    Console.setOut(lg.outputStream)
//    Console.setErr(lg.outputStream)

    val lgd = new DefaultSingleCDockable("log", "Log", lg.component.peer)
    lgd.setLocation(CLocation.base().normalSouth(0.25).east(0.333))
    lgd.setTitleIconHandling(IconHandling.KEEP_NULL_ICON) // this must be called before setTitleIcon
    lgd.setTitleIcon(null)

    dockCtrl.addDockable[SingleCDockable](lgd)
    lgd.setVisible(true)

    helpDockable  // init

//    lgd.addCDockableStateListener(new CDockableStateListener {
//      def extendedModeChanged(dockable: CDockable, mode: ExtendedMode): Unit = {
//        if (mode == ExtendedMode.EXTERNALIZED) {
//          println("Externalized")
//        }
//      }
//
//      def visibilityChanged(dockable: CDockable): Unit = ()
//    })

    frame.init(bp)

    initPrefs()
    TextViewDockable.empty()
  }

  private sealed trait UnsavedResult
  private case object UnsavedDiscard extends UnsavedResult
  private case object UnsavedCancel  extends UnsavedResult
  private case object UnsavedSave    extends UnsavedResult

  private def checkUnsaved(dock: TextViewDockable): Boolean = {
    if (!dock.view.dirty) return true

    warnUnsaved(dock) match {
      case UnsavedDiscard => true
      case UnsavedCancel  => false
      case UnsavedSave    => saveOrSaveAs(dock) match {
        case SaveResult(Success(_)) => true
        case _ => false
      }
    }
  }

  private def closeAll(): Boolean = documentHandler.documents.forall(checkUnsaved)

  private def warnUnsaved(dock: TextViewDockable): UnsavedResult = {
    val name = dock.view.file.fold {
      val s = dock.getTitleText
      if (s.startsWith("*")) s.substring(1) else s
    } (_.name)
    val msg = s"""<html><body><b>Save changes to document "$name" """ +
      "before closing?</b><p>If you don't save, changes will be permanently lost."
    val saveText    = if (dock.view.file.isDefined) "Save" else "Save As"
    val options     = Seq(saveText, "Cancel", "Close without Saving")
    val dlg = OptionPane(message = msg, optionType = OptionPane.Options.YesNoCancel,
      messageType = OptionPane.Message.Warning, entries = options, initial = Some(saveText))
    dock.setExtendedMode(ExtendedMode.NORMALIZED)

    val jDlg  = dlg.peer.createDialog(null, dlg.title)
    // cheesy work around to reset focus after asynchronous dock expansion
    jDlg.addWindowListener(new WindowAdapter {
      override def windowOpened(e: WindowEvent): Unit = {
        val t = new javax.swing.Timer(500, new ActionListener {
          def actionPerformed(e: ActionEvent): Unit = {
            val kbm   = KeyboardFocusManager.getCurrentKeyboardFocusManager
            if (jDlg.isVisible && kbm.getFocusOwner == null) {
              jDlg.transferFocus()
            }
          }
        })
        t.setRepeats(false)
        t.start()
      }
    })
    jDlg.setVisible(true)
    val idx = dlg.result.id

    if (idx == 0) UnsavedSave else if (idx == 2) UnsavedDiscard else UnsavedCancel
  }

  private def queryOpenFile(): Unit = {
    val dlg = FileDialog.open()
    dlg.show(Some(frame)).foreach(checkOpenFile)
  }

  private def checkOpenFile(file: File): Unit = {
    val fileOpt = Some(file)
    val dockOpt: Option[Document] = documentHandler.documents.find(_.view.file == fileOpt)
    dockOpt.fold(openFile(file)) { dock =>
      dock.toFront()
    }
  }

  private def openFile(file: File): Unit =
    try {
      // val file = file0.getCanonicalFile
      val src = scala.io.Source.fromFile(file)
      try {
        val text0 = src.mkString
        // If the current window is empty and untitled, simply close it first
        documentHandler.activeDocument.foreach { doc0 =>
          val doc1  = doc0: Document // IntelliJ...
          val view0 = doc1.view
          // println(s"FOUND: ${view0.file} / ${view0.dirty}")
          if (!view0.dirty && view0.file.isEmpty) {
            doc1.close()
          }
        }
        TextViewDockable.apply(text0, Some(file))
        _recent.add(file)
      } finally {
        src.close()
      }
    } catch {
      case NonFatal(e) => DialogSource.Exception(e -> s"Open '${file.name}'").show(Some(frame))
    }

  private def bootServer(): Unit =
    if (sp.booting.isEmpty && sp.server.isEmpty) rebootServer()

  private def rebootServer() :Unit =
    sp.peer.boot()

  private def serverMeters(): Unit = {
    import de.sciss.synth.swing.Implicits._
    sp.server.foreach { s =>
      val f = s.gui.meter()
      f.peer.setAlwaysOnTop(true)
    }
  }

  private def showNodes(): Unit = {
    import de.sciss.synth.swing.Implicits._
    sp.server.foreach { s =>
      val f = s.gui.tree()
      f.peer.setAlwaysOnTop(true)
    }
  }

  private def stopSynths(): Unit = {
    import de.sciss.synth.Ops._
    sp.server.foreach(_.defaultGroup.freeAll())
  }

  private def clearLog(): Unit = lg.clear()

  private def dumpNodes(controls: Boolean): Unit =
    sp.server.foreach(_.dumpTree(controls = controls))

  private lazy val _recent = RecentFiles(Main.userPrefs("recent-docs"))(checkOpenFile)

  def openURL(url: String): Unit = Desktop.browseURI(new URL(url).toURI)

  def lookUpHelp(ident: String, addToHistory: Boolean = true): Unit = {
    val opt = UGenSpec.standardUGens.get(ident).orElse(UGenSpec.thirdPartyUGens.get(ident))
    opt.fold[Unit] {
      println(s"No documentation for $ident")
    } { spec =>
      spec.doc.fold[Unit] {
        println(s"No documentation for UGen $ident")
      } { doc =>
        val source = ugenDocToMarkdown(spec, doc)
        browseMarkdown(source = source)
        if (addToHistory) {
          helpHistory     = helpHistory.take(helpHistoryIdx + 1).takeRight(32) :+ ident
          helpHistoryIdx  = helpHistory.size - 1
        }
      }
    }
  }

  private def ugenDocToMarkdown(spec: UGenSpec, doc: UGenSpec.Doc): String = {
    val sb = new StringBuilder

    @tailrec def convert(in: String, findL: String, findR: String, replL: String, replR: String,
                         midF: String => String = identity): String = {
      val i = in.indexOf(findL)
      if (i >= 0) {
        val j = i + findL.length
        val k = in.indexOf(findR, j)
        if (k >= 0) {
          val m    = k + findR.length
          val pre  = in.substring(0, i)
          val mid  = in.substring(j, k)
          val post = in.substring(m)
          val out  = s"$pre$replL${midF(mid)}$replR$post"
          convert(in = out, findL = findL, findR = findR, replL = replL, replR = replR, midF = midF)
        } else in
      } else in
    }

    // bold, italics, pre
    def convertAll(in: String) = {
      val a = in
      val b = convert(a, "'''", "'''", "__"   , "__"     )
      val c = convert(b, "''" , "''" , "_"    , "_"      )
      val d = convert(c, "{{{", "}}}", "\n", "\n", mid => mid.split("\n").map(ln => s"    $ln").mkString("\n"))
      d
    }

    if (doc.links.nonEmpty) {
      val links = doc.links.map { link =>
        val name = link.substring(link.lastIndexOf('.') + 1)
        s"[$name]($link)"
      }
      sb.append(links.mkString("See also: ", ", ", "\n\n"))
    }

    sb.append(s"# ${spec.name}\n\n")

    doc.body.foreach { in =>
      sb.append(convertAll(in))
      sb.append("\n\n")
    }

    if (doc.args.nonEmpty) {
      sb.append("## Arguments\n\n")
      if (doc.warnPos) {
        sb.append("__Warning:__ The argument order differs from sc-lang!\n\n")
      }
      spec.args.foreach { arg =>
        doc.args.get(arg.name).foreach { argDoc =>
          var pre = s"- __${arg.name}__: "
          argDoc.foreach { par =>
            sb.append(pre)
            sb.append(convertAll(par))
            pre = "<br><br>"
          }
          if (arg.defaults.nonEmpty) {
            val df = arg.defaults.map { case (rate, value) =>
              if (rate == UndefinedRate) value.toString else s"$value for ${rate.name} rate"
            } .mkString(" _(default: ", ", ", ")_")
            sb.append(df)
          }
          sb.append('\n')
        }
      }
      sb.append('\n')
    }
    if (doc.examples.nonEmpty) {
      sb.append("## Examples\n\n")
      doc.examples.foreach { ex =>
        // Simple: Example specifies UGens which can be wrapped in a `play { ... }` block */
        // Full: A full scale example which should be executed by itself without wrapping. */
        sb.append(s"### ${ex.name}\n\n")
        val isSimple = ex.tpe == UGenSpec.Example.Simple
        if (isSimple) sb.append("    play {\n")
        val indent = if (isSimple) "      " else "    "
        ex.code.foreach { ln =>
          sb.append(indent)
          sb.append(ln)
          sb.append('\n')
        }
        if (isSimple) sb.append("    }\n")
        sb.append('\n')
      }
    }

    sb.result()
  }

  def browseMarkdown(source: String): Unit = {
    val mdp  = new PegDownProcessor
    val html = mdp.markdownToHtml(source)
    browseHTML(source = html)
  }

  def browseHTML(source: String): Unit = {
    helpEditor.text = source
    helpEditor.peer.setCaretPosition(0)
    helpDockable.setVisible(true) // setExtendedMode(ExtendedMode.NORMALIZED)
  }

  private trait FileAction {
    _: Action =>

    protected var _view: Option[TextViewDockable] = None

    def view: Option[TextViewDockable] = _view
    def view_=(value: Option[TextViewDockable]): Unit = if (_view != value) {
      _view   = value
      enabled = value.isDefined
      viewChanged()
    }

    def apply(): Unit = _view.foreach(perform)

    protected def viewChanged(): Unit = ()

    protected def perform(dock: TextViewDockable): Unit
  }

  private object ActionFileClose extends Action("Close") with FileAction {
    accelerator = Some(KeyStrokes.menu1 + Key.W)

    protected def perform(dock: TextViewDockable): Unit =
      if (checkUnsaved(dock)) dock.close()
  }

  private def save(text: TextView, file: File): SaveResult = {
    try {
      val fw = new FileOutputStream(file)
      val w  = new OutputStreamWriter(fw, "UTF-8")
      try {
        w.write(text.editor.editor.text)
        w.flush()
      } finally {
        w.close()
      }
      _recent.add(file)
      text.file = Some(file)
      text.clearUndoBuffer()
      SaveResult(Success(file))
    } catch {
      case NonFatal(e) =>
        DialogSource.Exception(e -> s"Save ${file.name}").show(Some(frame))
        SaveResult(Failure(e))
    }
  }

  private sealed trait SaveOrCancelResult
  private case object SaveCancel extends SaveOrCancelResult
  private case class SaveResult(result: Try[File]) extends SaveOrCancelResult

  private def saveOrSaveAs(dock: TextViewDockable): SaveOrCancelResult =
    dock.view.file.fold(saveAs(dock)) { f =>
      save(dock.view, f)
    }

  private def saveAs(dock: TextViewDockable): SaveOrCancelResult = {
    val dlg = FileDialog.save(init = dock.view.file)
    dlg.show(Some(frame)).fold(SaveCancel: SaveOrCancelResult) { f =>
    //        val ok = !f.exists() || {
    //          val msg  = s"""<html><body><b>A file named "${f.name}" already exists. Do you want to replace it?</b><p>""" +
    //           f.parentOption.fold("")(p => s"""The file already exists in "${p.name}". """) +
    //           "Replacing it will overwrite its contents.</body></html>"
    //          val dlg1 = OptionPane.confirmation(message = msg, optionType = OptionPane.Options.OkCancel,
    //            messageType = OptionPane.Message.Warning)
    //          dlg1.show(Some(frame)) == OptionPane.Result.Ok
    //        }

    // the "sure-to-replace" check is already performed by the file dialog
      save(dock.view, f)
    }
  }

  private object ActionFileSave extends Action("Save") with FileAction {
    accelerator = Some(KeyStrokes.menu1 + Key.S)

    protected def perform(dock: TextViewDockable): Unit = saveOrSaveAs(dock)
  }

  private object ActionFileSaveAs extends Action("Save As...") with FileAction {
    accelerator = Some(KeyStrokes.menu1 + KeyStrokes.shift + Key.S)

    protected def perform(dock: TextViewDockable): Unit = saveAs(dock)
  }

  private object ActionLookUpHelpCursor extends Action("Look up Documentation for Cursor") with FileAction {
    accelerator = Some(KeyStrokes.menu1 + Key.D)
    protected def perform(dock: TextViewDockable): Unit = {
      val ed = dock.view.editor
      ed.activeToken.foreach { token =>
        if (token.`type` == TokenType.IDENTIFIER) {
          val ident = token.getString(ed.editor.peer.getDocument)
          lookUpHelp(ident)
        }
      }
    }
  }

  private object ActionLookUpHelpQuery extends Action("Look up Documentation...") {
    accelerator = Some(KeyStrokes.menu1 + KeyStrokes.shift + Key.D)


    def apply(): Unit = {
      val pane = OptionPane.textInput(message = "Symbol:", initial = "")
      val res  = pane.show(title = "Look up Documentation")
      res.foreach { ident =>
        lookUpHelp(ident)
      }
    }
  }

  import KeyStrokes.menu1

  private lazy val actionEnlargeFont = new ActionFontSize("Enlarge Font"    , menu1 + Key.Equals, 1)  // Plus doesn't work
  private lazy val actionShrinkFont  = new ActionFontSize("Shrink Font"     , menu1 + Key.Minus, -1)
  private lazy val actionResetFont   = new ActionFontSize("Reset Font Size" , menu1 + Key.Key0 ,  0)

  private class ActionFontSize(text: String, shortcut: KeyStroke, amount: Int )
    extends Action(text) with FileAction {

    accelerator = Some(shortcut)

    protected def perform(dock: TextViewDockable): Unit = dock.fontSizeChange(amount)
  }

  private lazy val fileActions = List(ActionFileClose, ActionFileSave, ActionFileSaveAs,
    actionEnlargeFont, actionShrinkFont, actionResetFont, ActionLookUpHelpCursor)

  private[this] val baseURL = "https://www.sciss.de/scalaCollider"

  private def showAbout(): Unit = {
    val jreInfo: String = {
      val name    = sys.props.getOrElse("java.runtime.name"   , "?")
      val version = sys.props.getOrElse("java.runtime.version", "?")
      s"$name (build $version)"
    }
    val scVersion = Server.version(repl.config).toOption.fold {
      "Unknown SuperCollider version"
    } { case (v, b) =>
      val bs = if (b.isEmpty) b else s" ($b)"
      s"SuperCollider v$v$bs"
    }
    val url   = baseURL
    val addr  = url // url.substring(math.min(url.length, url.indexOf("//") + 2))
    val html =
      s"""<html><center>
          |<font size=+1><b>About $name</b></font><p>
          |Copyright (c) 2008&ndash;2018 Hanns Holger Rutz. All rights reserved.<p>
          |This software is published under the GNU General Public License v3+
          |<p>&nbsp;<p><i>
          |ScalaCollider v${de.sciss.synth.BuildInfo.version}<br>
          |ScalaCollider-Swing v${de.sciss.synth.swing.BuildInfo.version}<br>
          |Scala v${de.sciss.synth.swing.BuildInfo.scalaVersion}<br>
          |$jreInfo<br>
          |$scVersion
          |</i>
          |<p>&nbsp;<p>
          |<a href="$url">$addr</a>
          |<p>&nbsp;
          |""".stripMargin

    val lb = new Label(html) {
      // cf. http://stackoverflow.com/questions/527719/how-to-add-hyperlink-in-jlabel
      // There is no way to directly register a HyperlinkListener, despite hyper links
      // being rendered... A simple solution is to accept any mouse click on the label
      // to open the corresponding website.
      cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
      listenTo(mouse.clicks)
      reactions += {
        case MouseClicked(_, _, _, 1, false) => openURL(url)
      }
    }

    OptionPane.message(message = lb.peer /*, icon = Logo.icon(128) */).show(Some(frame), title = "About")
  }

  protected lazy val menuFactory: Menu.Root = {
    import KeyStrokes._
    import Menu._
    import de.sciss.synth.swing.{Main => App}

    val itAbout = Item.About(App)(showAbout())
    val itPrefs = Item.Preferences(App)(ActionPreferences())
    val itQuit  = Item.Quit(App)

    Desktop.addQuitAcceptor {
      if (closeAll()) Future.successful(()) else Future.failed(new Exception("Aborted"))
    }

    val gFile = Group("file", "File")
      .add(Item("new" )("New"     -> (menu1 + Key.N))(TextViewDockable.empty()))
      .add(Item("open")("Open..." -> (menu1 + Key.O))(queryOpenFile()))
      .add(_recent.menu)
      .addLine()
      .add(Item("close"  , ActionFileClose ))
      .add(Item("close-all")("Close All" -> (menu1 + shift + Key.W))(closeAll()))
      .add(Item("save"   , ActionFileSave  ))
      .add(Item("save-as", ActionFileSaveAs))

    if (itQuit.visible) gFile.addLine().add(itQuit)

    val gEdit = Group("edit", "Edit")
      .add(Item("undo", proxy("Undo" -> (menu1 + Key.Z))))
      .add(Item("redo", proxy("Redo" -> (menu1 + shift + Key.Z))))

    if (itPrefs.visible /* && Desktop.isLinux */) gEdit.addLine().add(itPrefs)

    val gView = Group("view", "View")
      .add(Item("inc-font",   actionEnlargeFont))
      .add(Item("dec-font",   actionShrinkFont ))
      .add(Item("reset-font", actionResetFont  ))

    val gActions = Group("actions", "Actions")
      .add(Item("boot-server"   )("Boot Server"       -> (menu1 + Key.B))(bootServer()))
      .add(Item("reboot-server" )("Reboot Server"                       )(rebootServer()))
      .add(Item("server-meters" )("Show Server Meter" -> (menu1 + Key.M))(serverMeters()))
      .add(Item("show-tree"     )("Show Node Tree")(showNodes()))
      .add(Item("dump-tree"     )("Dump Node Tree"               -> (menu1         + Key.T))(dumpNodes(controls = false)))
      .add(Item("dump-tree-ctrl")("Dump Node Tree with Controls" -> (menu1 + shift + Key.T))(dumpNodes(controls = true )))
      .addLine()
      .add(Item("stop-synths")("Stop Synths" -> (menu1 + Key.Period))(stopSynths()))
      .addLine()
      .add(Item("clear-log")("Clear Log Window" -> (menu1 + shift + Key.P))(clearLog()))

    val gHelp = Group("help", "Help")
      .add(Item("help-for-cursor", ActionLookUpHelpCursor))
      .add(Item("help-query"     , ActionLookUpHelpQuery ))
      .add(Item("help-api")("API Documentation")(openURL(s"$baseURL/latest/api/de/sciss/synth/index.html")))

    if (itAbout.visible) gHelp.addLine().add(itAbout)

    Root().add(gFile).add(gEdit).add(gView).add(gActions).add(gHelp)
  }
}