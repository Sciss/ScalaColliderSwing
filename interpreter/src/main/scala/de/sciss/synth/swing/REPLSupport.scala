package de.sciss.synth.swing

import de.sciss.synth.{ServerConnection, Server}
import de.sciss.osc.TCP
import scala.util.control.NonFatal

class REPLSupport(ssp: ServerStatusPanel, ntp: NodeTreePanel) {
  override def toString = "repl-support"

  // var s : Server = null
  def s: Server       = Server.default

  val config          = Server.Config()
  config.transport    = TCP
  private val sync    = new AnyRef
  private var booting = null: ServerConnection

  // ---- constructor ----
  sys.addShutdownHook(shutDown())
  ssp.bootAction = Some(() => boot())

  def boot(): Unit =
    sync.synchronized {
      shutDown()
      booting = Server.boot(config = config) {
        case ServerConnection.Preparing(srv) => {
          ntp.group = Some(srv.rootNode)
        }
        case ServerConnection.Running(srv) => {
          sync.synchronized {
            booting = null
          }
        }
      }
      ssp.booting = Some(booting)
    }

  private def shutDown(): Unit =
    sync.synchronized {
      val srv = try { s } catch { case NonFatal(_) => null }
      if ((srv != null) && (srv.condition != Server.Offline)) {
        srv.quit()
      }
      if (booting != null) {
        booting.abort()
        booting = null
      }
    }
}