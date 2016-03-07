/*
 *  JNodeTreePanel.scala
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
package j

import java.awt.geom.Point2D
import collection.immutable.IntMap
import prefuse.action.{ ActionList, RepaintAction }
import prefuse.action.animate.{ColorAnimator, LocationAnimator, VisibilityAnimator}
import prefuse.render.{AbstractShapeRenderer, DefaultRendererFactory, EdgeRenderer}
import prefuse.util.ColorLib
import prefuse.visual.sort.TreeDepthItemSorter
import de.sciss.synth.{message, Node, Group, Synth, NodeManager, Ops}
import prefuse.{Visualization, Constants, Display}
import prefuse.visual.{NodeItem, VisualItem}
import de.sciss.synth.swing.impl.{IconLabelRenderer, DynamicTreeLayout}
import prefuse.data.expression.AbstractPredicate
import prefuse.data.{Tuple, Graph, Node => PNode}
import prefuse.visual.expression.InGroupPredicate
import prefuse.data.event.TupleSetListener
import prefuse.data.tuple.TupleSet
import java.awt.{BasicStroke, Color, BorderLayout, EventQueue}
import prefuse.action.assignment.{StrokeAction, ColorAction}
import javax.swing.{Icon, JMenuItem, JOptionPane, Action, AbstractAction, JPopupMenu, WindowConstants, JFrame, JPanel}
import java.awt.event.{MouseEvent, MouseAdapter, InputEvent, ActionEvent}
import prefuse.controls.{Control, FocusControl, PanControl, WheelZoomControl, ZoomToFitControl}
import javax.swing.event.{AncestorEvent, AncestorListener}
import de.sciss.osc
import annotation.tailrec

import DynamicTreeLayout.{INFO, NodeInfo}

trait NodeTreePanelLike {
  def nodeActionMenu: Boolean
  def nodeActionMenu_=(b: Boolean): Unit

  def confirmDestructiveActions: Boolean
  def confirmDestructiveActions_=(b: Boolean): Unit
}

object JNodeTreePanel {
  private val COL_LABEL     = "name"
  private val COL_PAUSED    = "paused"
  private val COL_NODE      = "node"

  private val orientation   = Constants.ORIENT_LEFT_RIGHT
  private val GROUP_TREE    = "tree"
  private val GROUP_NODES   = "tree.nodes"
  private val GROUP_EDGES   = "tree.edges"
  private val ACTION_ADD    = "add"

  private val ACTION_LAYOUT = "layout"
  private val ACTION_FOCUS  = "focus"
  private val ACTION_RUN    = "run"
  private val ACTION_ANIM   = "anim"
  private val FADE_TIME     = 333
  private val COL_ICON      = "icon"

  private val iconGroup = Shapes.Icon(extent = 16)(Shapes.Group)
  private val iconSynth = Shapes.Icon(extent = 16)(Shapes.Synth)

  private final val ICON_SYNTH = "synth"
  private final val ICON_GROUP = "group"

  private class NodeLabelRenderer(label: String) extends IconLabelRenderer(label) {
    override protected def getIcon(item: VisualItem): Icon =
      item.get(COL_ICON) match {
        case ICON_SYNTH => iconSynth
        case ICON_GROUP => iconGroup
        case _          => null
      }
  }

  private final val VERBOSE = false
}

class JNodeTreePanel extends JPanel(new BorderLayout()) with NodeTreePanelLike {
  treePanel =>

  import JNodeTreePanel._
  import NodeManager._

  protected def frameTitle = "Nodes"

  private val t = {
    val t     = new Graph
    val nodes = t.getNodeTable
    nodes.addColumn(COL_LABEL , classOf[String  ])
    nodes.addColumn(COL_ICON  , classOf[String  ])
    nodes.addColumn(COL_PAUSED, classOf[Boolean ])
    nodes.addColumn(COL_NODE  , classOf[Node    ])
    nodes.addColumn(INFO      , classOf[NodeInfo])
    t
  }
  private var map = IntMap.empty[PNode] // XXX TODO: use regular map not IntMap
  private val vis = {
    val res = new Visualization()
    res.add(GROUP_TREE, t)
    res
  }
  private val lay = {
    val res = DynamicTreeLayout(GROUP_TREE, orientation = orientation,
      depthSpacing = 32, breadthSpacing = 2, subtreeSpacing = 8)
    res.setLayoutAnchor(new Point2D.Double(25, 200))
    res
  }

  private val nodeListener: NodeManager.Listener = {
    case NodeGo   (synth: Synth, info)  => deferIfNeeded(nlAddSynth   (synth, info))
    case NodeGo   (group: Group, info)  => deferIfNeeded(nlAddGroup   (group, info))
    case NodeEnd  (node, info)          => deferIfNeeded(nlRemoveNode (node , info))
    case NodeMove (node, info)          => deferIfNeeded(nlMoveChild  (node , info))
    case NodeOn   (node, info)          => deferIfNeeded(nlPauseChild (node , paused = false))
    case NodeOff  (node, info)          => deferIfNeeded(nlPauseChild (node , paused = true ))
    case Cleared                        => deferIfNeeded(nlClear())
  }

  //   newRoot()

  private val display = new Display(vis)

  // ---- constructor ----
  {
    val nodeRenderer = new NodeLabelRenderer(COL_LABEL)
    nodeRenderer.setRenderType(AbstractShapeRenderer.RENDER_TYPE_DRAW_AND_FILL)
    nodeRenderer.horizontalAlignment = Constants.LEFT
    nodeRenderer.setRoundedCorner(8, 8)
    nodeRenderer.verticalPadding = 2
    val edgeRenderer = new EdgeRenderer(Constants.EDGE_TYPE_CURVE)

    val rf = new DefaultRendererFactory(nodeRenderer)
    rf.add(new InGroupPredicate(GROUP_EDGES), edgeRenderer)
    vis.setRendererFactory(rf)

    // colors
    val actionNodeFill = new ColorAction(GROUP_NODES, VisualItem.FILLCOLOR, ColorLib.rgb(200, 200, 200))
    val predFocused = new InGroupPredicate(Visualization.FOCUS_ITEMS)
    actionNodeFill.add(new AbstractPredicate {
      override def getBoolean(t: Tuple): Boolean = t.getBoolean(COL_PAUSED)
    }, ColorLib.rgb(200, 0, 0))

    val actionTextColor = new ColorAction(GROUP_NODES, VisualItem.TEXTCOLOR  , ColorLib.rgb(  0,   0,   0))
    val actionEdgeColor = new ColorAction(GROUP_EDGES, VisualItem.STROKECOLOR, ColorLib.rgb(200, 200, 200))
    val actionNodeDraw  = new ColorAction(GROUP_NODES, VisualItem.STROKECOLOR, ColorLib.rgb(100, 100, 255))
    val actionRepaint   = new RepaintAction()

    // the animator
    val animate = new ActionList(FADE_TIME)
    animate.add(new ColorAnimator     (GROUP_NODES))
    animate.add(new VisibilityAnimator(GROUP_TREE ))
    animate.add(new LocationAnimator  (GROUP_NODES))
    animate.add(actionRepaint)
    vis.putAction(ACTION_ANIM, animate)

    // actions taken when focus (selection) changes
    val actionFocus = new ActionList()
    val actionFocusStroke = new StrokeAction(GROUP_NODES, new BasicStroke(0f))
    actionFocusStroke.add(predFocused, new BasicStroke(2f))
    actionFocus.add(actionFocusStroke)
    actionFocus.add(actionRepaint)
    vis.putAction(ACTION_FOCUS, actionFocus)

    // actions taken when node pauses or resumes
    val actionRun = new ActionList()
    actionRun.add(actionNodeFill)
    vis.putAction(ACTION_RUN, actionRun)
    vis.alwaysRunAfter(ACTION_RUN, ACTION_ANIM)

    // actions taken when a child is added
    val actionAdd = new ActionList()
    actionAdd.add(lay)
    actionAdd.add(actionTextColor)
    actionAdd.add(actionNodeFill)
    actionAdd.add(actionNodeDraw)
    actionAdd.add(actionEdgeColor)
    actionAdd.add(actionFocusStroke)
    vis.putAction(ACTION_ADD, actionAdd)
    vis.alwaysRunAfter(ACTION_ADD, ACTION_ANIM)

    // actions taken when a child is moved or removed
    val actionLayout = new ActionList()
    actionLayout.add(lay)
    vis.putAction(ACTION_LAYOUT, actionLayout)
    vis.alwaysRunAfter(ACTION_LAYOUT, ACTION_ANIM)

    val focusGroup = vis.getGroup(Visualization.FOCUS_ITEMS)
    focusGroup.addTupleSetListener(new TupleSetListener {
      def tupleSetChanged(ts: TupleSet, add: Array[Tuple], remove: Array[Tuple]): Unit =
        nodeActionMenuComponent.foreach(_.selection_=(add.headOption.map(_.get(COL_NODE).asInstanceOf[Node])))
    })

    // ------------------------------------------------

    // initialize the display
    display.setSize(400, 400)
    display.setItemSorter(new TreeDepthItemSorter())
    display.addControlListener(new ZoomToFitControl(Control.LEFT_MOUSE_BUTTON) {
      override def mouseClicked(e: MouseEvent): Unit =
        if (e.getClickCount == 2) super.mouseClicked(e) // XXX ugly
    })
    display.addControlListener(new WheelZoomControl())
    display.addControlListener(new PanControl())
    display.addControlListener(new FocusControl(Visualization.FOCUS_ITEMS, 1, ACTION_FOCUS))
    display.setHighQuality(true)

      // ------------------------------------------------

    edgeRenderer.setHorizontalAlignment1(Constants.RIGHT )
    edgeRenderer.setHorizontalAlignment2(Constants.LEFT  )
    edgeRenderer.setVerticalAlignment1  (Constants.CENTER)
    edgeRenderer.setVerticalAlignment2  (Constants.CENTER)

    vis.run(ACTION_LAYOUT)

    display.setForeground(Color.WHITE)
    display.setBackground(Color.BLACK)

    add(display, BorderLayout.CENTER)

    addAncestorListener(new AncestorListener {
      def ancestorAdded   (e: AncestorEvent): Unit = startListening()
      def ancestorRemoved (e: AncestorEvent): Unit = stopListening()
      def ancestorMoved   (e: AncestorEvent): Unit = ()
    })
  }

  private var isListening = false

  private def startListening(): Unit = {
    if (isListening) return
    isListening = true

    swingGroupVar.foreach { g =>
      val queryMsg  = g.queryTreeMsg(postControls = false)
      val server    = g.server
      val fut       = server.!!(queryMsg) {
        case osc.Message("/g_queryTree.reply", 0, g.id, _numChildren: Int, rest @ _*) =>
          deferIfNeeded {
            visDo(ACTION_ADD) {
              newRoot(g)
              updateFrameTitle()
              val iter = rest.iterator

              @tailrec def loop(parentID: Int, predID: Int, numChildren: Int): Unit = {
                if (numChildren == 0) return // pop
                val nodeID = iter.next().asInstanceOf[Int]
                val subChildren = iter.next().asInstanceOf[Int]
                val (node, info, icon) = if (subChildren < 0) {
                  val _info = message.NodeInfo.SynthData(
                    parentID = parentID, predID = predID, succID = -1
                  )
                  /* val defName = */ iter.next().toString
                  val synth = Synth(server, nodeID)
                  (synth, _info, ICON_SYNTH)
                  // stupid way to set the defName: XXX TODO : synth.newMsg( defName )
                  //                        nlAddSynth( synth, info )

                } else {
                  val _info = message.NodeInfo.GroupData(
                    parentID = parentID, predID = predID, succID = -1, headID = -1, tailID = -1
                  )
                  val group = Group(server, nodeID)
                  (group, _info, ICON_GROUP)
                }
                map.get(parentID).foreach { p =>
                  addNode(node, info, p, icon)
                }

                if (subChildren > 0)
                  loop(parentID = nodeID, predID = -1, numChildren = subChildren) // push
                else
                  loop(parentID = parentID, predID = nodeID, numChildren = numChildren - 1) // iter

              } // def loop

              loop(parentID = g.id, predID = -1, numChildren = _numChildren)
              server.nodeManager.addListener(nodeListener)
            }
          }
      }
      val c = server.clientConfig
      import c.executionContext
      fut.onFailure {
        case message.Timeout() => println(s"${queryMsg.name} : timeout!")
      }
    }
  }

  private def stopListening(): Unit = {
    if (!isListening) return
    isListening = false
    swingGroupVar.foreach { g =>
      g.server.nodeManager.removeListener(nodeListener)
    }
    nlClear()
  }

  // ---- NodeTreePanelLike ----

  private var nodeActionMenuComponent = Option.empty[NodeActionsPopupMenu]

  def nodeActionMenu: Boolean = nodeActionMenuComponent.isDefined
  def nodeActionMenu_=(b: Boolean): Unit = {
    if (nodeActionMenuComponent.isDefined != b) {
      nodeActionMenuComponent.foreach(_.dispose())
      nodeActionMenuComponent = if (b) {
        Some(new NodeActionsPopupMenu(confirmDestructiveActions))
      } else None
    }
  }

  private var _confirmDestructiveActions = false
  def confirmDestructiveActions: Boolean = _confirmDestructiveActions
  def confirmDestructiveActions_=(b: Boolean): Unit = {
    if (_confirmDestructiveActions != b) {
      _confirmDestructiveActions = b
      nodeActionMenuComponent.foreach(_.confirmDestructiveActions_=(b))
    }
  }

  private def deferIfNeeded(code: => Unit): Unit =
    if (EventQueue.isDispatchThread) {
      code
    } else {
      EventQueue.invokeLater(new Runnable {
        def run(): Unit = code
      })
    }

  private def insertChild(pNode: PNode, pParent: PNode, info: message.NodeInfo.Data, iNode: NodeInfo): Unit = {
    val iParent = pParent.get(INFO).asInstanceOf[NodeInfo]
    val pPred = if (info.predID == -1) {
      iParent.head = pNode
      null
    } else {
      map.get(info.predID).orNull
    }
    if (pPred != null) {
      val iPred = pPred.get(INFO).asInstanceOf[NodeInfo]
      iPred.succ = pNode
      iNode.pred = pPred
    }
    val pSucc = if (info.succID == -1) {
      iParent.tail = pNode
      null
    } else {
      map.get(info.succID).orNull
    }
    if (pSucc != null) {
      val iSucc = pSucc.get(INFO).asInstanceOf[NodeInfo]
      iNode.succ = pSucc
      iSucc.pred = pNode
    }
    iNode.parent = pParent
  }

  private def deleteChild(node: Node, pNode: PNode): Unit = {
    removeChild(pNode)
    // note: we need to update the graph structure first,
    // because after calling Tree->removeChild, it is
    // not allowed to call get on the PNode any more.
    //      t.removeChild( pNode )
    t.removeNode(pNode)
    map -= node.id
  }

  private def removeChild(pNode: PNode): Unit = {
    val iInfo   = pNode.get(INFO).asInstanceOf[NodeInfo]
    val pPred   = iInfo.pred
    val pSucc   = iInfo.succ
    val pParent = iInfo.parent
    val iParent = if (pParent != null) {
      iInfo.parent = null
      pParent.get(INFO).asInstanceOf[NodeInfo]
    } else null
    if (pPred == null) {
      if (iParent != null) iParent.head = pSucc
    } else {
      val iPred = pPred.get(INFO).asInstanceOf[NodeInfo]
      iPred.succ = pSucc
      iInfo.pred = null
    }
    if (pSucc == null) {
      if (iParent != null) iParent.tail = pPred
    } else {
      val iSucc = pSucc.get(INFO).asInstanceOf[NodeInfo]
      iSucc.pred = pPred
      iInfo.succ = null
    }
  }

  private def createChild(node: Node, pParent: PNode, info: message.NodeInfo.Data): PNode = {
    val pNode = t.addNode()
    t.addEdge(pParent, pNode)
    val iNode = new NodeInfo
    pNode.set(INFO, iNode)
    pNode.set(COL_NODE, node)
    insertChild(pNode, pParent, info, iNode)
    map += node.id -> pNode
    pNode
  }

  private def nlAddSynth(synth: Synth, info: message.NodeInfo.Data): Unit = {
    val pNodeOpt = map.get(info.parentID)
    pNodeOpt.foreach(pParent => visDo(ACTION_ADD) {
      addNode(synth, info, pParent, ICON_SYNTH)
    })
  }

  private def nlAddGroup(group: Group, info: message.NodeInfo.Data): Unit = {
    val pNodeOpt = map.get(info.parentID)
    pNodeOpt.foreach(pParent => visDo(ACTION_ADD) {
      addNode(group, info, pParent, ICON_GROUP)
    })
  }

  private def addNode(n: Node, info: message.NodeInfo.Data, pParent: PNode, icon: String): Unit = {
    if (VERBOSE) println("add " + n + " ; " + info + " ; " + pParent)
    val pNode = createChild(n, pParent, info)
    pNode.set(COL_LABEL, n.id.toString)
    pNode.set(COL_ICON, icon)
    initPos(pNode)
  }

  private def nlRemoveNode(node: Node, info: message.NodeInfo.Data): Unit = {
    map.get(node.id).foreach(pNode => visDo(ACTION_LAYOUT) {
      deleteChild(node, pNode)
    })
  }

  private def nlMoveChild(node: Node, info: message.NodeInfo.Data): Unit = {
    map.get(node.id).foreach(pNode => visDo(ACTION_LAYOUT) {
      val iNode = pNode.get(INFO).asInstanceOf[NodeInfo]
      val oldEdge = t.getEdge(iNode.parent, pNode)
      removeChild(pNode)
      t.removeEdge(oldEdge)
      map.get(info.parentID).map { pParent =>
        insertChild(pNode, pParent, info, iNode)
        t.addEdge(pParent, pNode)
      } getOrElse {
        // disappeared from the radar
        t.removeNode(pNode)
        map -= node.id
      }
    })
  }

  private def nlPauseChild(node: Node, paused: Boolean): Unit = {
    map.get(node.id).foreach(pNode => visDo(ACTION_RUN) {
      pNode.setBoolean(COL_PAUSED, paused)
    })
  }

  private def nlClear(): Unit =
    visCancel {
      t.clear()
      map = IntMap.empty
    }

  private def newRoot(g: Group): Unit = {
    val r = t.addNode()
    val iNode = new NodeInfo
    r.set(INFO, iNode)
    r.set(COL_NODE, g)
    r.set(COL_ICON, ICON_GROUP)
    val vi = vis.getVisualItem(GROUP_TREE, r).asInstanceOf[NodeItem]
    val pt = lay.getLayoutAnchor
    vi.setX(pt.getX)
    vi.setY(pt.getY)
    lay.layoutRoot = vi
    map += g.id -> r
  }

  private final val sync    = new AnyRef
  private var groupVar      = Option.empty[Group]
  private var swingGroupVar = Option.empty[Group]

  def group: Option[Group] = groupVar

  /** This method is thread-safe. */
  def group_=(value: Option[Group]): Unit =
    sync.synchronized {
      groupVar = value

      deferIfNeeded {
        val active = isListening
        if (active) stopListening()
        swingGroupVar = value
        if (active) startListening()
      }
    }

  private def initPos(pNode: PNode): Unit = {
    val pParent = pNode.get(INFO).asInstanceOf[NodeInfo].parent
    if (pParent != null) {
      val vi = vis.getVisualItem(GROUP_TREE, pNode)
      val vip = vis.getVisualItem(GROUP_TREE, pParent)
      if (vi != null && vip != null) {
        vi.setX(vip.getX)
        vi.setY(vip.getY)
      }
    }
  }

  private def visDo(action: String)(code: => Unit): Unit =
    vis.synchronized {
      vis.cancel(ACTION_ANIM)
      try {
        code
      } finally {
        vis.run(action)
      }
    }

  private def visCancel(code: => Unit): Unit =
    vis.synchronized {
      vis.cancel(ACTION_ANIM)
      code
    }

  private var frame = Option.empty[JFrame]

  private def updateFrameTitle(): Unit =
    frame.foreach { fr =>
      val tit = swingGroupVar.fold(frameTitle) { g =>
        s"$frameTitle (${if (g.id == 0) g.server.toString() else g.toString})"
      }
      fr.setTitle(tit)
    }

  def makeWindow(disposeOnClose: Boolean = true): JFrame = {
    require(EventQueue.isDispatchThread)
    frame getOrElse {
      val fr = new JFrame()
      fr.getRootPane.putClientProperty("Window.style", "small")
      fr.setDefaultCloseOperation(
        if (disposeOnClose) WindowConstants.DISPOSE_ON_CLOSE else WindowConstants.DO_NOTHING_ON_CLOSE
      )
      fr.getContentPane.add(this)
      fr.pack()
      fr.setLocationRelativeTo(null)
      setFrame(fr)

      fr
    }
  }

  private[swing] def setFrame(fr: JFrame): Unit = {
    frame = Some(fr)
    updateFrameTitle()
  }

  private def isPaused(n: Node): Boolean = map.get(n.id).exists(_.getBoolean(COL_PAUSED))

  private class NodeActionsPopupMenu(private var _confirmDestructiveActions: Boolean) extends JPopupMenu {
    pop =>

    import Ops._

    private var selectionVar = Option.empty[Node]

    val actionNodeFree = new AbstractAction {
      def actionPerformed(e: ActionEvent): Unit =
        selectionVar.foreach(n => confirm(this, "Free node " + n.id + "?")(n.free()))
    }

    val actionNodeRun = new AbstractAction("Run") {
      def actionPerformed(e: ActionEvent): Unit =
        selectionVar.foreach(n => n.run(isPaused(n)))
    }

    val actionNodeTrace = new AbstractAction("Trace") {
      def actionPerformed(e: ActionEvent): Unit =
        selectionVar.foreach(_.trace())
    }

    val actionGroupFreeAll = new AbstractAction {
      def actionPerformed(e: ActionEvent): Unit =
        selectionVar match {
          case Some(g: Group) => confirm(this, "Free all nodes in group " + g.id + "?")(g.freeAll())
          case _ =>
        }
    }

    val actionGroupDeepFree = new AbstractAction {
      def actionPerformed(e: ActionEvent): Unit =
        selectionVar match {
          case Some(g: Group) => confirm(this, "Free all synths in group " + g.id + " and its sub-groups?")(g.deepFree())
          case _ =>
        }
    }

    val actionGroupDumpTree = new AbstractAction("Dump tree") {
      def actionPerformed(e: ActionEvent): Unit =
        selectionVar match {
          case Some(g: Group) => g.dumpTree((e.getModifiers & InputEvent.ALT_MASK) != 0)
          case _ =>
        }
    }

    val popupTrigger = new MouseAdapter {
      private def process(e: MouseEvent): Unit =
        if (e.isPopupTrigger) {
          pop.show(e.getComponent, e.getX, e.getY)
        }

      override def mousePressed (e: MouseEvent): Unit = process(e)
      override def mouseReleased(e: MouseEvent): Unit = process(e)
      override def mouseClicked (e: MouseEvent): Unit = process(e)
    }

    private def confirm(action: Action, message: String)(thunk: => Unit): Unit =
      if (!_confirmDestructiveActions || JOptionPane.showConfirmDialog(treePanel, message,
        action.getValue(Action.NAME).toString, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) ==
        JOptionPane.YES_OPTION) {

        thunk
      }

    private def item(action: Action): JMenuItem = {
      val b = new JMenuItem(action)
      pop.add(b)
      b
    }

    item(actionNodeFree)
    item(actionNodeRun)
    item(actionNodeTrace)
    item(actionGroupFreeAll)
    item(actionGroupDeepFree)
    item(actionGroupDumpTree).setToolTipText("Hold Alt key to dump controls")

    display.add(this)
    display.addMouseListener(popupTrigger)

    confirmDestructiveActions = false // initializes labels
    selection_=(None) // initializes enabled states

    def confirmDestructiveActions_=(b: Boolean): Unit = {
      _confirmDestructiveActions = b
      actionNodeFree      .putValue(Action.NAME, if (b) "Free..."       else "Free"     )
      actionGroupFreeAll  .putValue(Action.NAME, if (b) "Free all..."   else "Free all" )
      actionGroupDeepFree .putValue(Action.NAME, if (b) "Free deep..."  else "Free deep")
    }

    def selection_=(nodeOption: Option[Node]): Unit = {
      selectionVar = nodeOption
      val (n, g) = nodeOption match {
        case Some(g: Group) => (true , true )
        case Some(_)        => (true , false)
        case _              => (false, false)
      }
      actionNodeFree      .setEnabled(n)
      actionNodeRun       .setEnabled(n)
      actionNodeTrace     .setEnabled(n)
      actionGroupFreeAll  .setEnabled(g)
      actionGroupDeepFree .setEnabled(g)
      actionGroupDumpTree .setEnabled(g)
    }

    def dispose(): Unit = {
      display.remove(this)
      display.removeMouseListener(popupTrigger)
    }
  }
}