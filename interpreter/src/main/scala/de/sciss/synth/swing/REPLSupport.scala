/*
 *  REPLSupport.scala
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

import de.sciss.synth.{ServerConnection, Server}
import de.sciss.osc.TCP
import scala.util.control.NonFatal
import scala.concurrent.ExecutionContext
import java.awt.EventQueue
import scala.swing.Swing

class REPLSupport(ssp: ServerStatusPanel, ntp: NodeTreePanel) {
  override def toString = "repl-support"

  // var s : Server = null
  def s: Server       = Server.default

  val config: Server.ConfigBuilder = Server.Config()
  config.transport    = TCP
  config.port         = 0
  private val sync    = new AnyRef
  private var booting = null: ServerConnection

  // ---- constructor ----
  sys.addShutdownHook(shutDown())
  ssp.bootAction = Some(() => boot())

  def boot(): Unit =
    sync.synchronized {
      shutDown()
      val pick = config.port == 0
      if (pick) config.pickPort()
      val c = config.build
      if (pick) config.port = 0   // XXX TODO horrible

      booting = Server.boot(config = c) {
        case ServerConnection.Preparing(srv) =>
          if (ntp != null) ntp.group = Some(srv.rootNode)

        case ServerConnection.Running(_) =>
          sync.synchronized {
            booting = null
          }
      }
      ssp.booting = Some(booting)
    }

  def defer(body: => Unit): Unit =
    if (EventQueue.isDispatchThread) body else Swing.onEDT(body)

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

  implicit def executionContext: ExecutionContext = try {
    s.clientConfig.executionContext
  } catch {
    case NonFatal(_) =>  // no server created yet
      ExecutionContext.global
  }
}