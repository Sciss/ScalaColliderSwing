/*
 *  DynamicTreeLayout.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.swing
package impl

import java.awt.geom.Point2D

import prefuse.action.layout.Layout
import prefuse.visual.NodeItem

import scala.annotation.switch

object DynamicTreeLayout {
  /** Creates a new dynamic tree layout.
    *
    * @param group            the data group to layout. Must resolve to a Graph instance.
    * @param orientation      the orientation of the tree layout. One of `prefuse.Constants.ORIENT_LEFT_RIGHT`,
    *                         `ORIENT_RIGHT_LEFT`, `ORIENT_TOP_BOTTOM`, or `ORIENT_BOTTOM_TOP`.
    * @param depthSpacing     the spacing to maintain between depth levels of the tree
    * @param breadthSpacing   the spacing to maintain between sibling nodes
    * @param subtreeSpacing   the spacing to maintain between neighboring subtrees
    */
  def apply( group: String, orientation: Int = prefuse.Constants.ORIENT_LEFT_RIGHT,
             depthSpacing: Double = 50, breadthSpacing: Double = 5,
             subtreeSpacing: Double = 25): DynamicTreeLayout =
    new DynamicTreeLayout(group, orientation, depthSpacing = depthSpacing, breadthSpacing = breadthSpacing,
      subtreeSpacing = subtreeSpacing)

  final class NodeInfo {
    var parent: prefuse.data.Node = _
    var pred  : prefuse.data.Node = _
    var succ  : prefuse.data.Node = _
    var head  : prefuse.data.Node = _
    var tail  : prefuse.data.Node = _
  }

  final val INFO = "info"

  // ------------------------------------------------------------------------
  // Params Schema

  /** The data field in which the parameters used by this layout are stored. */
  private final val PARAMS = "_reingoldTilfordParams"

  /** The schema for the parameters used by this layout. */
  private final val PARAMS_SCHEMA = new prefuse.data.Schema()
  PARAMS_SCHEMA.addColumn(PARAMS, classOf[Params])

  /** Wrapper class holding parameters used for each node in this layout. */
  private final class Params extends Cloneable {
    var prelim  : Double    = 0.0
    var mod     : Double    = 0.0
    var shift   : Double    = 0.0
    var change  : Double    = 0.0
    var number  : Int       = -2
    var ancestor: NodeItem  = _
    var thread  : NodeItem  = _

    def init(item: NodeItem): Unit = {
      ancestor  = item
      number    = -1
    }

    def clear(): Unit = {
      number    = -2
      prelim    = 0.0
      mod       = 0.0
      shift     = 0.0
      change    = 0.0
      ancestor  = null
      thread    = null
    }
  }
}

final class DynamicTreeLayout private(group: String, private var orientationVar: Int, var depthSpacing: Double,
                                      var breadthSpacing: Double, var subtreeSpacing: Double)
  extends Layout(group) {

  import DynamicTreeLayout.{INFO, NodeInfo, PARAMS, PARAMS_SCHEMA, Params}
  import prefuse.Constants.{ORIENTATION_COUNT, ORIENT_BOTTOM_TOP, ORIENT_CENTER, ORIENT_LEFT_RIGHT, ORIENT_RIGHT_LEFT, ORIENT_TOP_BOTTOM}

  var rootNodeOffset: Double = 50

  def orientation: Int = orientationVar
  def orientation_=(value: Int): Unit = {
    if (value < 0 || value >= ORIENTATION_COUNT || value == ORIENT_CENTER) {
      throw new IllegalArgumentException("Unsupported orientation value: " + value)
    }
    orientationVar = value
  }

  private var depths = new Array[Double](10)
  private var maxDepth = 0

  private var rootVar: NodeItem = _
  private var anchorX = 0.0
  private var anchorY = 0.0

  /** @see prefuse.action.layout.Layout#getLayoutAnchor() */
  override def getLayoutAnchor: Point2D = {
    if (m_anchor != null) return m_anchor

    m_tmpa.setLocation(0, 0)
    if (m_vis != null) {
      val d = m_vis.getDisplay(0)
      val b = getLayoutBounds
      (orientationVar: @switch) match {
        case ORIENT_LEFT_RIGHT =>
          m_tmpa.setLocation(rootNodeOffset, d.getHeight / 2.0)
        case ORIENT_RIGHT_LEFT =>
          m_tmpa.setLocation(b.getMaxX - rootNodeOffset, d.getHeight / 2.0)
        case ORIENT_TOP_BOTTOM =>
          m_tmpa.setLocation(d.getWidth / 2.0, rootNodeOffset)
        case ORIENT_BOTTOM_TOP =>
          m_tmpa.setLocation(d.getWidth / 2.0, b.getMaxY - rootNodeOffset)
      }
      d.getInverseTransform.transform(m_tmpa, m_tmpa)
    }
    m_tmpa
  }

  private def spacing(left: NodeItem, right: NodeItem, siblings: Boolean): Double = {
    val vert = orientationVar == ORIENT_TOP_BOTTOM || orientationVar == ORIENT_BOTTOM_TOP
    val spc1 = if (siblings) breadthSpacing else subtreeSpacing
    val spc2 = if (vert) {
      left.getBounds.getWidth + right.getBounds.getWidth
    } else {
      left.getBounds.getHeight + right.getBounds.getHeight
    }
    spc1 + 0.5 * spc2
  }

  private def updateDepths(depth: Int, item: NodeItem): Unit = {
    val vert  = orientationVar == ORIENT_TOP_BOTTOM || orientationVar == ORIENT_BOTTOM_TOP
    val b     = item.getBounds
    val d     = if (vert) b.getHeight else b.getWidth
    if (depths.length <= depth) depths = prefuse.util.ArrayLib.resize(depths, 3 * depth / 2)
    depths(depth) = math.max(depths(depth), d)
    maxDepth      = math.max(maxDepth, depth)
  }

  private def determineDepths(): Unit = {
    var i = 1
    while (i < maxDepth) {
      depths(i) += depths(i - 1) + depthSpacing
      i += 1
    }
  }

  // ------------------------------------------------------------------------

   /** Explicitly sets the node to use as the layout root.
     * @param value the node to use as the root. If the node is not a member of this layout's
     * data group, an exception will be thrown.
     * @throws IllegalArgumentException if the provided root is not a member of
     * this layout's data group.
     */
   def layoutRoot_=(value: NodeItem): Unit = {
     if (!value.isInGroup(m_group)) {
       throw new IllegalArgumentException("Input node is not a member of this layout's data group")
     }
     rootVar = value
   }

  /**  Returns the NodeItem to use as the root for this tree layout.
    * @return the root node to use for this tree layout.
    * @throws IllegalStateException if the action's data group does not
    *                               resolve to a `prefuse.data.Graph` instance.
    */
  def layoutRoot: NodeItem =
    if (rootVar != null) rootVar else
      throw new IllegalStateException("The layout requires that a layout root is explicitly set.")

  /** @see prefuse.action.Action#run(double) */
  def run(frac: Double): Unit = {
    val root = rootVar
    if( root == null ) return  // graceful fail

    val g = m_vis.getGroup(m_group).asInstanceOf[prefuse.data.Graph]
    initSchema(g.getNodes)

    java.util.Arrays.fill(depths, 0)
    maxDepth = 0

    val a = getLayoutAnchor()
    anchorX = a.getX
    anchorY = a.getY

    val rp = getParams(root)

    // do first pass - compute breadth information, collect depth info
    firstWalk(root, 0, 1)

    // sum up the depth info
    determineDepths()

    // do second pass - assign layout positions
    secondWalk(root, null, -rp.prelim, 0)
  }

  private def getParent(node: NodeItem): NodeItem = {
    val i = node.get(INFO).asInstanceOf[NodeInfo]
    if (i == null) null else getNodeItem(node, i.parent)
  }

  private def getFirstChild(node: NodeItem): NodeItem = {
    val i = node.get(INFO).asInstanceOf[NodeInfo]
    if (i == null) null else getNodeItem(node, i.head)
  }

  private def getLastChild(node: NodeItem): NodeItem = {
    val i = node.get(INFO).asInstanceOf[NodeInfo]
    if (i == null) null else getNodeItem(node, i.tail)
  }

  private def getPreviousSibling(node: NodeItem): NodeItem = {
    val i = node.get(INFO).asInstanceOf[NodeInfo]
    if (i == null) null else getNodeItem(node, i.pred)
  }

  private def getNextSibling(node: NodeItem): NodeItem = {
    val i = node.get(INFO).asInstanceOf[NodeInfo]
    if (i == null) null else getNodeItem(node, i.succ)
  }

  private def hasChildren(node: NodeItem): Boolean = {
    val i = node.get(INFO).asInstanceOf[NodeInfo]
    if (i == null) false else i.head != null
  }

  private def getNodeItem(ref: NodeItem, tup: prefuse.data.Node): NodeItem =
    if (tup == null) null else ref.getVisualization.getVisualItem(ref.getGroup, tup).asInstanceOf[NodeItem]

  private def firstWalk(n: NodeItem, num: Int, depth: Int): Unit = {
    val np = getParams(n)
    np.number = num
    updateDepths(depth, n)

    val expanded = n.isExpanded
    //    if ( n.getChildCount() == 0 || !expanded ) // is leaf
    if (!hasChildren(n) || !expanded) {
      // is leaf
      //       NodeItem l = (NodeItem)n.getPreviousSibling();
      val left = getPreviousSibling(n)
      np.prelim = if (left == null) 0 else getParams(left).prelim + spacing(left, n, siblings = true)

    } else if (expanded) {
      //       NodeItem leftMost = (NodeItem)n.getFirstChild();
      val leftMost        = getFirstChild(n)
      //       NodeItem rightMost = (NodeItem)n.getLastChild();
      val rightMost       = getLastChild(n)
      var defaultAncestor = leftMost
      var c = leftMost
      //       for ( int i=0; c != null; ++i, c = (NodeItem)c.getNextSibling() )
      var i = 0
      while (c != null) {
        firstWalk(c, i, depth + 1)
        defaultAncestor = apportion(c, defaultAncestor)
        i += 1
        c = getNextSibling(c)
      }

      executeShifts(n)

      val midpoint = 0.5 * (getParams(leftMost).prelim + getParams(rightMost).prelim)

      //       NodeItem left = (NodeItem)n.getPreviousSibling();
      val left = getPreviousSibling(n)
      if( left != null ) {
        np.prelim = getParams(left).prelim + spacing(left, n, siblings = true)
        np.mod = np.prelim - midpoint
      } else {
        np.prelim = midpoint
      }
    }
  }

  private def apportion(v: NodeItem, a: NodeItem): NodeItem = {
    //    NodeItem w = (NodeItem)v.getPreviousSibling();
    val w = getPreviousSibling(v)
    var res = a
    if (w != null) {
      var vip = v
      var vop = v
      var vim = w
      //       vom = (NodeItem)vip.getParent().getFirstChild();
      var vom = getFirstChild(getParent(vip))

      var sip = getParams(vip).mod
      var sop = getParams(vop).mod
      var sim = getParams(vim).mod
      var som = getParams(vom).mod

      var nr = nextRight(vim)
      var nl = nextLeft(vip)
      while (nr != null && nl != null) {
        vim = nr
        vip = nl
        vom = nextLeft(vom)
        vop = nextRight(vop)
        getParams(vop).ancestor = v
        val shift = (getParams( vim ).prelim + sim) - (getParams( vip ).prelim + sip) +
          spacing(vim, vip, siblings = false)
        if (shift > 0) {
          moveSubtree(ancestor(vim, v, a), v, shift)
          sip += shift
          sop += shift
        }
        sim += getParams(vim).mod
        sip += getParams(vip).mod
        som += getParams(vom).mod
        sop += getParams(vop).mod

        nr = nextRight(vim)
        nl = nextLeft(vip)
      }
      if (nr != null && nextRight(vop) == null) {
        val vopp    = getParams(vop)
        vopp.thread = nr
        vopp.mod   += sim - sop
      }
      if (nl != null && nextLeft(vom) == null) {
        val vomp    = getParams(vom)
        vomp.thread = nl
        vomp.mod   += sip - som
        res         = v
      }
    }
    res
  }

  private def nextLeft(n: NodeItem): NodeItem = {
    val c: NodeItem = if (n.isExpanded) getFirstChild(n) else null
    //    if ( n.isExpanded() ) c = (NodeItem)n.getFirstChild();
    if (c != null) c else getParams(n).thread
  }

  private def nextRight(n: NodeItem): NodeItem = {
    //    if ( n.isExpanded() ) c = (NodeItem)n.getLastChild();
    val c: NodeItem = if (n.isExpanded) getLastChild(n) else null
    if (c != null) c else getParams(n).thread
  }

  private def moveSubtree(wm: NodeItem, wp: NodeItem, shift: Double): Unit = {
    val wmp       = getParams(wm)
    val wpp       = getParams(wp)
    val subtrees  = wpp.number - wmp.number
    val shiftP    = shift / subtrees
    wpp.change   -= shiftP
    wpp.shift    += shift
    wmp.change   += shiftP
    wpp.prelim   += shift
    wpp.mod      += shift
  }

  private def executeShifts(n: NodeItem): Unit = {
    var shift   = 0.0
    var change  = 0.0
    //        for ( NodeItem c = (NodeItem)n.getLastChild();
    //              c != null; c = (NodeItem)c.getPreviousSibling() )

    var c: NodeItem = getLastChild(n)
    while (c != null) {
      val cp = getParams(c)
      cp.prelim += shift
      cp.mod += shift
      change += cp.change
      shift += cp.shift + change
      c = getPreviousSibling(c)
    }
  }

  private def ancestor(vim: NodeItem, v: NodeItem, a: NodeItem): NodeItem = {
    //    NodeItem p = (NodeItem)v.getParent();
    val p: NodeItem = getParent(v)
    val vimp = getParams(vim)
    //    if ( vimp.ancestor.getParent() == p )
    if (getParent(vimp.ancestor) == p) vimp.ancestor else a
  }

  private def secondWalk(n: NodeItem, p: NodeItem, m: Double, depth: Int): Unit = {
    val np = getParams(n)
    setBreadth(n, p, np.prelim + m)
    setDepth  (n, p, depths(depth))

    if (n.isExpanded) {
      val depth1 = depth + 1
      //            for ( NodeItem c = (NodeItem)n.getFirstChild();
      //                  c != null; c = (NodeItem)c.getNextSibling() )
      var c = getFirstChild(n)
      while (c != null) {
        secondWalk(c, n, m + np.mod, depth1)
        c = getNextSibling(c)
      }
    }

    np.clear()
  }

  private def setBreadth(n: NodeItem, p: NodeItem, b: Double): Unit =
    (orientationVar: @switch) match {
      case ORIENT_LEFT_RIGHT => setY(n, p, anchorY + b)
      case ORIENT_RIGHT_LEFT => setY(n, p, anchorY + b)
      case ORIENT_TOP_BOTTOM => setX(n, p, anchorX + b)
      case ORIENT_BOTTOM_TOP => setX(n, p, anchorX + b)
      case _ => throw new IllegalStateException()
    }

  private def setDepth(n: NodeItem, p: NodeItem, d: Double): Unit =
    (orientationVar: @switch) match {
      case ORIENT_LEFT_RIGHT => setX(n, p, anchorX + d)
      case ORIENT_RIGHT_LEFT => setX(n, p, anchorX - d)
      case ORIENT_TOP_BOTTOM => setY(n, p, anchorY + d)
      case ORIENT_BOTTOM_TOP => setY(n, p, anchorY - d)
      case _ => throw new IllegalStateException()
    }

  // ------------------------------------------------------------------------
  // Params Schema

  protected def initSchema(ts: prefuse.data.tuple.TupleSet): Unit = ts.addColumns(PARAMS_SCHEMA)

  private def getParams(item: NodeItem): Params = {
    var rp = item.get(PARAMS).asInstanceOf[Params]
    if (rp == null) {
      rp = new Params()
      item.set(PARAMS, rp)
    }
    if (rp.number == -2) rp.init(item)
    rp
  }
}