/*
 *  Main.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2016 Hanns Holger Rutz. All rights reserved.
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
import javax.swing.{KeyStroke, SwingUtilities, UIManager}

import bibliothek.gui.dock.common.event.CFocusListener
import bibliothek.gui.dock.common.intern.CDockable
import bibliothek.gui.dock.common.mode.ExtendedMode
import bibliothek.gui.dock.common.theme.ThemeMap
import bibliothek.gui.dock.common.{CControl, CLocation, DefaultSingleCDockable}
import bibliothek.gui.dock.dockable.IconHandling
import bibliothek.gui.dock.util.Priority
import de.sciss.desktop.impl.{SwingApplicationImpl, WindowHandlerImpl, WindowImpl}
import de.sciss.desktop.{Desktop, DialogSource, FileDialog, KeyStrokes, LogPane, Menu, OptionPane, RecentFiles, Window, WindowHandler}
import de.sciss.file._
import de.sciss.syntaxpane.TokenType
import de.sciss.synth.Server
import de.sciss.{scalainterpreter => si}

import scala.concurrent.{ExecutionContext, Future}
import scala.swing.event.Key
import scala.swing.{Action, BorderPanel, BoxPanel, Component, Orientation, Swing}
import scala.tools.nsc.interpreter.NamedParam
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object Main extends SwingApplicationImpl("ScalaCollider") {
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
      "de.sciss.synth.swing.SynthGraphPanel._",
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
      case Window.Closing(_) => if (Desktop.mayQuit()) quit()
    }

    def init(c: Component): Unit = {
      contents  = c
      bounds    = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
      front()
    }
  }

  private lazy val frame = new MainWindow

  def mainWindow: Window = frame

  def dockControl: CControl = dockCtrl

  def isDarkSkin: Boolean = UIManager.getBoolean("dark-skin")

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
        case tvd: TextViewDockable =>
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
    Console.setOut(lg.outputStream)
    Console.setErr(lg.outputStream)

    val lgd = new DefaultSingleCDockable("log", "Log", lg.component.peer)
    lgd.setLocation(CLocation.base().normalSouth(0.25))
    lgd.setTitleIconHandling(IconHandling.KEEP_NULL_ICON) // this must be called before setTitleIcon
    lgd.setTitleIcon(null)

    dockCtrl.addDockable(lgd)
    lgd.setVisible(true)

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
      val src = io.Source.fromFile(file)
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

  private def lookUpHelp(dock: TextViewDockable): Unit = {
    val ed = dock.view.editor
    ed.activeToken.foreach { token =>
      if (token.`type` == TokenType.IDENTIFIER) {
        val ident = token.getString(ed.editor.peer.getDocument)
        println(s"TODO: lookUpHelp - $ident")
      }
    }
  }

  private trait FileAction {
    _: Action =>

    protected var _view: Option[TextViewDockable] = None
    def view = _view
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

  private object ActionLookUpHelp extends Action("Look up Documentation for Cursor") with FileAction {
    accelerator = Some(KeyStrokes.menu1 + Key.D)
    protected def perform(dock: TextViewDockable): Unit = lookUpHelp(dock)
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
    actionEnlargeFont, actionShrinkFont, actionResetFont, ActionLookUpHelp)

  protected lazy val menuFactory: Menu.Root = {
    import KeyStrokes._
    import Menu._
    import de.sciss.synth.swing.{Main => App}

    val itAbout = Item.About(App) {
      val html =
        s"""<html><center>
           |<font size=+1><b>About $name</b></font><p>
           |Copyright (c) 2008&ndash;2015 Hanns Holger Rutz. All rights reserved.<p>
           |This software is published under the GNU General Public License v3+
           |<p>&nbsp;<p><i>
           |ScalaCollider v${de.sciss.synth.BuildInfo.version}<br>
           |ScalaCollider-Swing v${de.sciss.synth.swing.BuildInfo.version}<br>
           |Scala v${de.sciss.synth.swing.BuildInfo.scalaVersion}
           |</i>""".stripMargin
      OptionPane.message(message = new javax.swing.JLabel(html)).show(Some(frame))
    }
    val itPrefs = Item.Preferences(App)(ActionPreferences())
    val itQuit  = Item.Quit(App)

    Desktop.addQuitAcceptor(closeAll())

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
      .add(Item("help-for-cursor", ActionLookUpHelp))

    if (itAbout.visible) gHelp.addLine().add(itAbout)

    Root().add(gFile).add(gEdit).add(gView).add(gActions).add(gHelp)
  }
}