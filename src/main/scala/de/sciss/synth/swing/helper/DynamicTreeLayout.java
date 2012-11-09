/*
  The original class NodeLinkTreeLayout, on which this class is
  based, was released under the following BSD style license:

  Copyright (c) 2004-2007 Regents of the University of California.
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions
  are met:

  1. Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.

  3.  Neither the name of the University nor the names of its contributors
  may be used to endorse or promote products derived from this software
  without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
  ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
  FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
  OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
  HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
  OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
  SUCH DAMAGE.
 */

package de.sciss.synth.swing.helper;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

import prefuse.Constants;
import prefuse.Display;
import prefuse.action.layout.Layout;
import prefuse.action.layout.graph.TreeLayout;
import prefuse.data.Graph;
import prefuse.data.Node;
import prefuse.data.Schema;
import prefuse.data.tuple.TupleSet;
import prefuse.util.ArrayLib;
import prefuse.visual.NodeItem;

/**
 *  A tuned version of the original Prefuse's NodeLinkTreeLayout by
 *  Jeffrey Heer, which handles dynamic trees, for example moving
 *  nodes around, and which supports arbitrary insertion order.
 *  To achieve this we must forget about Prefuse's static idea of
 *  data structures, in particular the Tree, and reside to a
 *  regular Graph with tree information stored in an information
 *  column.
 *
 *  @author <a href="http://jheer.org">jeffrey heer</a>
 *  @author Hanns Holger Rutz
 */
public class DynamicTreeLayout extends Layout {

    public static class NodeInfo {
        public Node parent;
        public Node pred;
        public Node succ;
        public Node head;
        public Node tail;
    }
    public static final String INFO  = "info";

    protected NodeItem m_root;

    private int    m_orientation;  // the orientation of the tree
    private double m_bspace = 5;   // the spacing between sibling nodes
    private double m_tspace = 25;  // the spacing between subtrees
    private double m_dspace = 50;  // the spacing between depth levels
    private double m_offset = 50;  // pixel offset for root node position

    private double[] m_depths = new double[10];
    private int      m_maxDepth = 0;

    private double m_ax, m_ay; // for holding anchor co-ordinates

    /**
     * Create a new NodeLinkTreeLayout. A left-to-right orientation is assumed.
     * @param group the data group to layout. Must resolve to a Graph instance.
     */
    public DynamicTreeLayout(String group) {
        super(group);
        m_orientation = Constants.ORIENT_LEFT_RIGHT;
    }

    /**
     * Create a new NodeLinkTreeLayout.
     * @param group the data group to layout. Must resolve to a Graph instance.
     * @param orientation the orientation of the tree layout. One of
     * {@link prefuse.Constants#ORIENT_LEFT_RIGHT},
     * {@link prefuse.Constants#ORIENT_RIGHT_LEFT},
     * {@link prefuse.Constants#ORIENT_TOP_BOTTOM}, or
     * {@link prefuse.Constants#ORIENT_BOTTOM_TOP}.
     * @param dspace the spacing to maintain between depth levels of the tree
     * @param bspace the spacing to maintain between sibling nodes
     * @param tspace the spacing to maintain between neighboring subtrees
     */
    public DynamicTreeLayout(String group, int orientation,
            double dspace, double bspace, double tspace)
    {
        super(group);
        m_orientation = orientation;
        m_dspace = dspace;
        m_bspace = bspace;
        m_tspace = tspace;
    }

    // ------------------------------------------------------------------------

    /**
     * Set the orientation of the tree layout.
     * @param orientation the orientation value. One of
     * {@link prefuse.Constants#ORIENT_LEFT_RIGHT},
     * {@link prefuse.Constants#ORIENT_RIGHT_LEFT},
     * {@link prefuse.Constants#ORIENT_TOP_BOTTOM}, or
     * {@link prefuse.Constants#ORIENT_BOTTOM_TOP}.
     */
    public void setOrientation(int orientation) {
        if ( orientation < 0 ||
             orientation >= Constants.ORIENTATION_COUNT ||
             orientation == Constants.ORIENT_CENTER )
        {
            throw new IllegalArgumentException(
                "Unsupported orientation value: "+orientation);
        }
        m_orientation = orientation;
    }

    /**
     * Get the orientation of the tree layout.
     * @return the orientation value. One of
     * {@link prefuse.Constants#ORIENT_LEFT_RIGHT},
     * {@link prefuse.Constants#ORIENT_RIGHT_LEFT},
     * {@link prefuse.Constants#ORIENT_TOP_BOTTOM}, or
     * {@link prefuse.Constants#ORIENT_BOTTOM_TOP}.
     */
    public int getOrientation() {
        return m_orientation;
    }

    /**
     * Set the spacing between depth levels.
     * @param d the depth spacing to use
     */
    public void setDepthSpacing(double d) {
        m_dspace = d;
    }

    /**
     * Get the spacing between depth levels.
     * @return the depth spacing
     */
    public double getDepthSpacing() {
        return m_dspace;
    }

    /**
     * Set the spacing between neighbor nodes.
     * @param b the breadth spacing to use
     */
    public void setBreadthSpacing(double b) {
        m_bspace = b;
    }

    /**
     * Get the spacing between neighbor nodes.
     * @return the breadth spacing
     */
    public double getBreadthSpacing() {
        return m_bspace;
    }

    /**
     * Set the spacing between neighboring subtrees.
     * @param s the subtree spacing to use
     */
    public void setSubtreeSpacing(double s) {
        m_tspace = s;
    }

    /**
     * Get the spacing between neighboring subtrees.
     * @return the subtree spacing
     */
    public double getSubtreeSpacing() {
        return m_tspace;
    }

    /**
     * Set the offset value for placing the root node of the tree. The
     * dimension in which this offset is applied is dependent upon the
     * orientation of the tree. For example, in a left-to-right orientation,
     * the offset will a horizontal offset from the left edge of the layout
     * bounds.
     * @param o the value by which to offset the root node of the tree
     */
    public void setRootNodeOffset(double o) {
        m_offset = o;
    }

    /**
     * Get the offset value for placing the root node of the tree.
     * @return the value by which the root node of the tree is offset
     */
    public double getRootNodeOffset() {
        return m_offset;
    }

    // ------------------------------------------------------------------------

    /**
     * @see prefuse.action.layout.Layout#getLayoutAnchor()
     */
    public Point2D getLayoutAnchor() {
        if ( m_anchor != null )
            return m_anchor;

        m_tmpa.setLocation(0,0);
        if ( m_vis != null ) {
            Display d = m_vis.getDisplay(0);
            Rectangle2D b = this.getLayoutBounds();
            switch ( m_orientation ) {
            case Constants.ORIENT_LEFT_RIGHT:
                m_tmpa.setLocation(m_offset, d.getHeight()/2.0);
                break;
            case Constants.ORIENT_RIGHT_LEFT:
                m_tmpa.setLocation(b.getMaxX()-m_offset, d.getHeight()/2.0);
                break;
            case Constants.ORIENT_TOP_BOTTOM:
                m_tmpa.setLocation(d.getWidth()/2.0, m_offset);
                break;
            case Constants.ORIENT_BOTTOM_TOP:
                m_tmpa.setLocation(d.getWidth()/2.0, b.getMaxY()-m_offset);
                break;
            }
            d.getInverseTransform().transform(m_tmpa, m_tmpa);
        }
        return m_tmpa;
    }

    private double spacing(NodeItem l, NodeItem r, boolean siblings) {
        boolean w = ( m_orientation == Constants.ORIENT_TOP_BOTTOM ||
                      m_orientation == Constants.ORIENT_BOTTOM_TOP );
        return (siblings ? m_bspace : m_tspace) + 0.5 *
            ( w ? l.getBounds().getWidth() + r.getBounds().getWidth()
                : l.getBounds().getHeight() + r.getBounds().getHeight() );
    }

    private void updateDepths(int depth, NodeItem item) {
        boolean v = ( m_orientation == Constants.ORIENT_TOP_BOTTOM ||
                      m_orientation == Constants.ORIENT_BOTTOM_TOP );
        double d = ( v ? item.getBounds().getHeight()
                       : item.getBounds().getWidth() );
        if ( m_depths.length <= depth )
            m_depths = ArrayLib.resize(m_depths, 3*depth/2);
        m_depths[depth] = Math.max(m_depths[depth], d);
        m_maxDepth = Math.max(m_maxDepth, depth);
    }

    private void determineDepths() {
        for ( int i=1; i<m_maxDepth; ++i )
            m_depths[i] += m_depths[i-1] + m_dspace;
    }

    // ------------------------------------------------------------------------

    /**
     * Explicitly set the node to use as the layout root.
     * @param root the node to use as the root. If the node is not a member of this layout's
     * data group, an exception will be thrown.
     * @throws IllegalArgumentException if the provided root is not a member of
     * this layout's data group.
     */
    public void setLayoutRoot(NodeItem root) {
        if ( !root.isInGroup(m_group) )
            throw new IllegalArgumentException("Input node is not a member "
                    + "of this layout's data group");
        m_root = root;
    }

    /**
     * Return the NodeItem to use as the root for this tree layout.
     * @return the root node to use for this tree layout.
     * @throws IllegalStateException if the action's data group does not
     * resolve to a {@link prefuse.data.Graph} instance.
     */
    public NodeItem getLayoutRoot() {
        if ( m_root != null )
            return m_root;

        throw new IllegalStateException("The layout requires that a layout root is explicitly set.");
    }

    /**
     * @see prefuse.action.Action#run(double)
     */
    public void run(double frac) {
        Graph g = (Graph)m_vis.getGroup(m_group);
        initSchema(g.getNodes());

        Arrays.fill(m_depths, 0);
        m_maxDepth = 0;

        Point2D a = getLayoutAnchor();
        m_ax = a.getX();
        m_ay = a.getY();

        NodeItem root = getLayoutRoot();
        Params rp = getParams(root);

        // do first pass - compute breadth information, collect depth info
        firstWalk(root, 0, 1);

        // sum up the depth info
        determineDepths();

        // do second pass - assign layout positions
        secondWalk(root, null, -rp.prelim, 0);
    }

    private static NodeItem getParent( NodeItem node ) {
        final NodeInfo i = (NodeInfo) node.get( INFO );
        return i == null ? null : getNodeItem( node, i.parent );
    }

    private static NodeItem getFirstChild( NodeItem node ) {
        final NodeInfo i = (NodeInfo) node.get( INFO );
        return i == null ? null : getNodeItem( node, i.head );
    }

    private static NodeItem getLastChild( NodeItem node ) {
        final NodeInfo i = (NodeInfo) node.get( INFO );
        return i == null ? null : getNodeItem( node, i.tail );
    }

    private static NodeItem getPreviousSibling( NodeItem node ) {
        final NodeInfo i = (NodeInfo) node.get( INFO );
        return i == null ? null : getNodeItem( node, i.pred );
    }

    private static NodeItem getNextSibling( NodeItem node ) {
        final NodeInfo i = (NodeInfo) node.get( INFO );
        return i == null ? null : getNodeItem( node, i.succ );
    }

    private static boolean hasChildren( NodeItem node ) {
        final NodeInfo i = (NodeInfo) node.get( INFO );
        return i == null ? false : (i.head != null);
    }

    private static NodeItem getNodeItem( NodeItem ref, Node tup ) {
        return tup == null ? null : (NodeItem) ref.getVisualization().getVisualItem( ref.getGroup(), tup );
    }

    private void firstWalk(NodeItem n, int num, int depth) {
        Params np = getParams(n);
        np.number = num;
        updateDepths(depth, n);

        boolean expanded = n.isExpanded();
//        if ( n.getChildCount() == 0 || !expanded ) // is leaf
        if( !hasChildren( n ) || !expanded ) // is leaf
        {
//            NodeItem l = (NodeItem)n.getPreviousSibling();
            NodeItem l = getPreviousSibling( n );
            if ( l == null ) {
                np.prelim = 0;
            } else {
                np.prelim = getParams(l).prelim + spacing(l,n,true);
            }
        }
        else if ( expanded )
        {
//            NodeItem leftMost = (NodeItem)n.getFirstChild();
            NodeItem leftMost = getFirstChild( n );
//            NodeItem rightMost = (NodeItem)n.getLastChild();
            NodeItem rightMost = getLastChild( n );
            NodeItem defaultAncestor = leftMost;
            NodeItem c = leftMost;
//            for ( int i=0; c != null; ++i, c = (NodeItem)c.getNextSibling() )
            for ( int i=0; c != null; ++i, c = getNextSibling( c ))
            {
                firstWalk(c, i, depth+1);
                defaultAncestor = apportion(c, defaultAncestor);
            }

            executeShifts(n);

            double midpoint = 0.5 *
                (getParams(leftMost).prelim + getParams(rightMost).prelim);

//            NodeItem left = (NodeItem)n.getPreviousSibling();
            NodeItem left = getPreviousSibling( n );
            if ( left != null ) {
                np.prelim = getParams(left).prelim + spacing(left, n, true);
                np.mod = np.prelim - midpoint;
            } else {
                np.prelim = midpoint;
            }
        }
    }

    private NodeItem apportion(NodeItem v, NodeItem a) {
//        NodeItem w = (NodeItem)v.getPreviousSibling();
        NodeItem w = getPreviousSibling( v );
        if ( w != null ) {
            NodeItem vip, vim, vop, vom;
            double   sip, sim, sop, som;

            vip = vop = v;
            vim = w;
//            vom = (NodeItem)vip.getParent().getFirstChild();
            vom = getFirstChild( getParent( vip ));

            sip = getParams(vip).mod;
            sop = getParams(vop).mod;
            sim = getParams(vim).mod;
            som = getParams(vom).mod;

            NodeItem nr = nextRight(vim);
            NodeItem nl = nextLeft(vip);
            while ( nr != null && nl != null ) {
                vim = nr;
                vip = nl;
                vom = nextLeft(vom);
                vop = nextRight(vop);
                getParams(vop).ancestor = v;
                double shift = (getParams(vim).prelim + sim) -
                    (getParams(vip).prelim + sip) + spacing(vim,vip,false);
                if ( shift > 0 ) {
                    moveSubtree(ancestor(vim,v,a), v, shift);
                    sip += shift;
                    sop += shift;
                }
                sim += getParams(vim).mod;
                sip += getParams(vip).mod;
                som += getParams(vom).mod;
                sop += getParams(vop).mod;

                nr = nextRight(vim);
                nl = nextLeft(vip);
            }
            if ( nr != null && nextRight(vop) == null ) {
                Params vopp = getParams(vop);
                vopp.thread = nr;
                vopp.mod += sim - sop;
            }
            if ( nl != null && nextLeft(vom) == null ) {
                Params vomp = getParams(vom);
                vomp.thread = nl;
                vomp.mod += sip - som;
                a = v;
            }
        }
        return a;
    }

    private NodeItem nextLeft(NodeItem n) {
        NodeItem c = null;
//        if ( n.isExpanded() ) c = (NodeItem)n.getFirstChild();
        if ( n.isExpanded() ) c = getFirstChild( n );
        return ( c != null ? c : getParams(n).thread );
    }

    private NodeItem nextRight(NodeItem n) {
        NodeItem c = null;
//        if ( n.isExpanded() ) c = (NodeItem)n.getLastChild();
        if ( n.isExpanded() ) c = getLastChild( n );
        return ( c != null ? c : getParams(n).thread );
    }

    private void moveSubtree(NodeItem wm, NodeItem wp, double shift) {
        Params wmp = getParams(wm);
        Params wpp = getParams(wp);
        double subtrees = wpp.number - wmp.number;
        wpp.change -= shift/subtrees;
        wpp.shift += shift;
        wmp.change += shift/subtrees;
        wpp.prelim += shift;
        wpp.mod += shift;
    }

    private void executeShifts(NodeItem n) {
        double shift = 0, change = 0;
//        for ( NodeItem c = (NodeItem)n.getLastChild();
//              c != null; c = (NodeItem)c.getPreviousSibling() )

        for ( NodeItem c = getLastChild( n );
              c != null; c = getPreviousSibling( c ) )
        {
            Params cp = getParams(c);
            cp.prelim += shift;
            cp.mod += shift;
            change += cp.change;
            shift += cp.shift + change;
        }
    }

    private NodeItem ancestor(NodeItem vim, NodeItem v, NodeItem a) {
//        NodeItem p = (NodeItem)v.getParent();
        NodeItem p = getParent( v );
        Params vimp = getParams(vim);
//        if ( vimp.ancestor.getParent() == p )
        if( getParent( vimp.ancestor ) == p ) {
            return vimp.ancestor;
        } else {
            return a;
        }
    }

    private void secondWalk(NodeItem n, NodeItem p, double m, int depth) {
        Params np = getParams(n);
        setBreadth(n, p, np.prelim + m);
        setDepth(n, p, m_depths[depth]);

        if ( n.isExpanded() ) {
            depth += 1;
//            for ( NodeItem c = (NodeItem)n.getFirstChild();
//                  c != null; c = (NodeItem)c.getNextSibling() )
            for ( NodeItem c = getFirstChild( n );
                  c != null; c = getNextSibling( c ))
            {
                secondWalk(c, n, m + np.mod, depth);
            }
        }

        np.clear();
    }

    private void setBreadth(NodeItem n, NodeItem p, double b) {
        switch ( m_orientation ) {
        case Constants.ORIENT_LEFT_RIGHT:
        case Constants.ORIENT_RIGHT_LEFT:
            setY(n, p, m_ay + b);
            break;
        case Constants.ORIENT_TOP_BOTTOM:
        case Constants.ORIENT_BOTTOM_TOP:
            setX(n, p, m_ax + b);
            break;
        default:
            throw new IllegalStateException();
        }
    }

    private void setDepth(NodeItem n, NodeItem p, double d) {
        switch ( m_orientation ) {
        case Constants.ORIENT_LEFT_RIGHT:
            setX(n, p, m_ax + d);
            break;
        case Constants.ORIENT_RIGHT_LEFT:
            setX(n, p, m_ax - d);
            break;
        case Constants.ORIENT_TOP_BOTTOM:
            setY(n, p, m_ay + d);
            break;
        case Constants.ORIENT_BOTTOM_TOP:
            setY(n, p, m_ay - d);
            break;
        default:
            throw new IllegalStateException();
        }
    }

    // ------------------------------------------------------------------------
    // Params Schema

    /**
     * The data field in which the parameters used by this layout are stored.
     */
    public static final String PARAMS = "_reingoldTilfordParams";
    /**
     * The schema for the parameters used by this layout.
     */
    public static final Schema PARAMS_SCHEMA = new Schema();
    static {
        PARAMS_SCHEMA.addColumn(PARAMS, Params.class);
    }

    protected void initSchema(TupleSet ts) {
        ts.addColumns(PARAMS_SCHEMA);
    }

    private Params getParams(NodeItem item) {
        Params rp = (Params)item.get(PARAMS);
        if ( rp == null ) {
            rp = new Params();
            item.set(PARAMS, rp);
        }
        if ( rp.number == -2 ) {
            rp.init(item);
        }
        return rp;
    }

    /**
     * Wrapper class holding parameters used for each node in this layout.
     */
    public static class Params implements Cloneable {
        double prelim;
        double mod;
        double shift;
        double change;
        int    number = -2;
        NodeItem ancestor = null;
        NodeItem thread = null;

        public void init(NodeItem item) {
            ancestor = item;
            number = -1;
        }

        public void clear() {
            number = -2;
            prelim = mod = shift = change = 0;
            ancestor = thread = null;
        }
    }

} // end of class NodeLinkTreeLayout
