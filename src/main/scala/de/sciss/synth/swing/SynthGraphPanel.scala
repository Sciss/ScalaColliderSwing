/*
 *  SynthGraphPanel.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2012 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.swing

import java.awt.{ BorderLayout, Color, Point }
import java.awt.event.{ WindowAdapter, WindowEvent }
import javax.swing.{ JComponent, JFrame, JPanel, JSplitPane, WindowConstants }
import prefuse.{ Constants, Display, Visualization }
import prefuse.action.{ ActionList, RepaintAction }
import prefuse.action.animate.{ ColorAnimator, LocationAnimator, VisibilityAnimator }
import prefuse.action.assignment.ColorAction
import prefuse.action.layout.graph._
import prefuse.activity.Activity
import prefuse.controls.{ DragControl, PanControl, WheelZoomControl, ZoomControl, ZoomToFitControl }
import prefuse.data.{ Graph => PGraph, Node => PNode }
import prefuse.render.{ AbstractShapeRenderer, DefaultRendererFactory, EdgeRenderer, LabelRenderer }
import prefuse.util.ColorLib
import prefuse.util.ui.JForcePanel
import prefuse.visual.VisualItem
import prefuse.visual.expression.InGroupPredicate
import prefuse.visual.sort.TreeDepthItemSorter
import de.sciss.synth._
import collection.immutable.IntMap

object SynthGraphPanel {
   def viewGraph( g: UGenGraph, forceDirected: Boolean = true ) : SynthGraphPanel =
      view( new SynthGraphPanel( "", g, forceDirected ))

   def viewDef( d: SynthDef, forceDirected: Boolean = true ) : SynthGraphPanel =
      view( new SynthGraphPanel( d.name, d.graph, forceDirected ))

   private def view( p: SynthGraphPanel ) = {
      val f = p.makeWindow
      f.setVisible( true )
      p
   }
}
class SynthGraphPanel ( name: String, graph: UGenGraph, forceDirected: Boolean )
extends JPanel {
   panel =>
   
//   def this( graph: SynthGraph, forceDirected: Boolean = true ) = this( "", graph, forceDirected )
//   def this( synthDef: SynthDef, forceDirected: Boolean = true ) = this( synthDef.name, synthDef.graph, forceDirected )

   private val COL_LABEL            = "name"
   private val COL_RATE             = "rate"
   private val GROUP_GRAPH          = "graph"
   private val GROUP_NODES          = "graph.nodes"
   private val GROUP_EDGES          = "graph.edges"
   private val ACTION_LAYOUT        = "layout"
   private val ACTION_LAYOUT_ANIM   = "layout-anim"
   private val ACTION_COLOR         = "color"
//   private val ACTION_COLOR_ANIM    = "layout-anim"
//   private val FADE_TIME            = 20000

   val colorMap = Map[ Any, Int ](
      scalar   -> ColorLib.rgb( 200, 200, 200 ),
      control  -> ColorLib.rgb(  50,  50, 250 ),
      audio    -> ColorLib.rgb( 220,  50,  50 ),
      demand   -> ColorLib.rgb(  50, 200,  50 )
   )

   val g                    = {
      val g       = new PGraph( true )
      val nodes   = g.getNodeTable()
      nodes.addColumn( COL_LABEL,  classOf[ String ])
      nodes.addColumn( COL_RATE,   classOf[ Rate ])
      val edges   = g.getEdgeTable()
      edges.addColumn( COL_RATE,   classOf[ Rate ])
//      PrefuseHelper.addColumn( nodes, COL_ICON,   classOf[ String ])
//      PrefuseHelper.addColumn( nodes, COL_PAUSED, classOf[ Boolean ])
//      FIELDS.foreach( PrefuseHelper.addColumn( nodes, _, classOf[ PNode ]))
      g
   }
   val vis                  = new Visualization()

   // ---- constructor ----
   {
      var mapNodes   = IntMap.empty[ PNode ]
      val indexed    = graph.ugens.zipWithIndex
      indexed.foreach( tup => {
         val (ru, idx) = tup
         val pNode = g.addNode()
         pNode.setString( COL_LABEL, ru.ugen.displayName )
         pNode.set( COL_RATE, ru.ugen.rate )
         mapNodes += idx -> pNode
      })
      indexed.foreach( tup => {
         val (ru, idx) = tup
         val pNode1 = mapNodes( idx )
         ru.inputSpecs.foreach( spec => {
            if( spec._1 >= 0 ) {
               val (pidx, oidx) = spec
               val pNode2 = mapNodes( pidx )
               val pEdge = g.addEdge( pNode2, pNode1 )
               pEdge.set( COL_RATE, graph.ugens( pidx ).ugen.outputRates( oidx ))
            }
         })
      })
   }
      
   val display = new Display( vis )
   val vg = vis.addGraph( GROUP_GRAPH, g )
   val lay = if( forceDirected ) {
      new ForceDirectedLayout( GROUP_GRAPH )
   } else {
      new NodeLinkTreeLayout( GROUP_GRAPH, Constants.ORIENT_TOP_BOTTOM, 24, 2, 8 )
   }

   {
      val vi0 = vg.getNode( 0 ).asInstanceOf[ VisualItem ]
      if( vi0 != null ) vi0.setFixed( false )
//      vis.addFocusGroup( GROUP_PAUSED, setPaused )

      val nodeRenderer = new LabelRenderer( COL_LABEL )
      nodeRenderer.setRenderType( AbstractShapeRenderer.RENDER_TYPE_FILL )
      nodeRenderer.setHorizontalAlignment( Constants.LEFT )
      nodeRenderer.setRoundedCorner( 8, 8 )
      nodeRenderer.setVerticalPadding( 2 )
      val edgeRenderer = new EdgeRenderer( /* if( forceDirected )*/ Constants.EDGE_TYPE_LINE /* else Constants.EDGE_TYPE_CURVE*/,
         Constants.EDGE_ARROW_FORWARD )
      
      val rf = new DefaultRendererFactory( nodeRenderer )
      rf.add( new InGroupPredicate( GROUP_EDGES), edgeRenderer )
      vis.setRendererFactory( rf )

      // colors
//      val actionNodeColor = new ColorAction( GROUP_NODES, VisualItem.FILLCOLOR, ColorLib.rgb( 200, 200, 200 ))
      val actionNodeColor = new RateColorAction( GROUP_NODES, VisualItem.FILLCOLOR )
      val actionTextColor = new ColorAction( GROUP_NODES, VisualItem.TEXTCOLOR, ColorLib.rgb( 255, 255, 255 ))

      val actionEdgeColor = new RateColorAction( GROUP_EDGES, VisualItem.STROKECOLOR )
      val actionArrowColor = new RateColorAction( GROUP_EDGES, VisualItem.FILLCOLOR )

      // quick repaint
      val actionColor = new ActionList()
      actionColor.add( actionTextColor )
      actionColor.add( actionNodeColor )
      actionColor.add( actionEdgeColor )
      actionColor.add( actionArrowColor )
      vis.putAction( ACTION_COLOR, actionColor )

      // animate paint change
//      val animateColor = new ActionList( FADE_TIME )
//      animateColor.add( new ColorAnimator( GROUP_NODES ))
//      animateColor.add( new RepaintAction() )
//      vis.putAction( ACTION_COLOR_ANIM, animateColor )
//      vis.alwaysRunAfter( ACTION_COLOR, ACTION_COLOR_ANIM )

//      val actionTreeLayout = new NodeLinkTreeLayout( GROUP_TREE, orientation, 32, 2, 8 )
//      val actionGraphLayout = new ForceDirectedLayout( GROUP_GRAPH )
//      val actionGraphLayout = new NodeLinkTreeLayout( GROUP_GRAPH )
//      val actionGraphLayout = layoutName match {
//         case "force-directed" => new ForceDirectedLayout( GROUP_GRAPH )
//         case "fruchterman-reingold" => new FruchtermanReingoldLayout( GROUP_GRAPH )
//         case "node-link-tree" => new NodeLinkTreeLayout( GROUP_GRAPH, Constants.ORIENT_TOP_BOTTOM, 24, 2, 8 )
//         case "balloon-tree" => new BalloonTreeLayout( GROUP_GRAPH )
//         case "radial-tree" => new RadialTreeLayout( GROUP_GRAPH )
//         case _ => new ForceDirectedLayout( GROUP_GRAPH )
//      }

      lay.setLayoutAnchor( new Point( 200, if( forceDirected ) 200 else 20 ))

      // create the filtering and layout
      // 50ms is not super smooth but saves significant CPU over the default 20ms!
      val actionLayout = if( forceDirected ) new ActionList( Activity.INFINITY, 50 ) else new ActionList()
      actionLayout.add( lay )
      actionLayout.add( new RepaintAction() )
      vis.putAction( ACTION_LAYOUT, actionLayout )

//      // animated transition
//      val animateLayout = new ActionList( Activity.INFINITY )
//      actionLayout.add( actionGraphLayout )
////      animateLayout.add( new VisibilityAnimator( GROUP_GRAPH ))
////      animateLayout.add( new LocationAnimator( GROUP_NODES ))
////      animateLayout.add( new ColorAnimator( GROUP_NODES ))
//      animateLayout.add( new RepaintAction() )
//      vis.putAction( ACTION_LAYOUT_ANIM, animateLayout )
////      vis.alwaysRunAfter( ACTION_LAYOUT, ACTION_LAYOUT_ANIM )
      vis.runAfter( ACTION_COLOR, ACTION_LAYOUT )

      // ------------------------------------------------

      // initialize the display
      display.setSize( 400, 400 )
      display.setItemSorter( new TreeDepthItemSorter() )
      display.addControlListener( new DragControl() )
      display.addControlListener( new ZoomToFitControl() )
      display.addControlListener( new ZoomControl() )
      display.addControlListener( new WheelZoomControl() )
      display.addControlListener( new PanControl() )
      display.setHighQuality( true )

      // ------------------------------------------------

       nodeRenderer.setHorizontalAlignment( Constants.CENTER )
       edgeRenderer.setHorizontalAlignment1( Constants.CENTER )  // outlets
       edgeRenderer.setHorizontalAlignment2( Constants.CENTER )  // inlets
       edgeRenderer.setVerticalAlignment1( if( forceDirected ) Constants.CENTER else Constants.BOTTOM )    // outlets
       edgeRenderer.setVerticalAlignment2( if( forceDirected ) Constants.CENTER else Constants.TOP )       // inlets

      display.setForeground( Color.WHITE )
      display.setBackground( Color.BLACK )

      setLayout( new BorderLayout() )
      add( display, BorderLayout.CENTER )

      vis.run( ACTION_COLOR )
   }

   def dispose {
      stopAnimation
   }

   private def stopAnimation {
      vis.cancel( ACTION_COLOR )
      vis.cancel( ACTION_LAYOUT )
   }

   private class RateColorAction( group: String, field: String )
   extends ColorAction( group, field ) {
      override def getColor( vi: VisualItem ) = colorMap( vi.get( COL_RATE ))
   }

   def makeWindow: JFrame = {
      val frame = new JFrame( "Synth Graph" + (if( name != "" ) " (" + name + ")" else "") )
//		frame.setResizable( false )
      frame.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE )
      frame.addWindowListener( new WindowAdapter {
         override def windowClosed( e: WindowEvent ) {
            panel.dispose
         }
      })
      val cp = frame.getContentPane
      lay match {
         case fd: ForceDirectedLayout => {
            val fsim = fd.getForceSimulator()
            val fpanel = new JForcePanel( fsim )
            fpanel.setBackground( null )
            def setDeepSchnuck( jc: JComponent ) {
               jc.putClientProperty( "JComponent.sizeVariant", "mini" )
               for( i <- 0 until jc.getComponentCount ) jc.getComponent( i ) match {
                  case jc2: JComponent => setDeepSchnuck( jc2 )
               }
            }

            setDeepSchnuck( fpanel )
            val split = new JSplitPane()
            split.setLeftComponent( panel )
            split.setRightComponent( fpanel )
            split.setOneTouchExpandable( true )
            split.setContinuousLayout( false )
            split.setDividerLocation( 400 )
            split.setResizeWeight( 1.0 )
            cp.add( split )
         }
         case _ => cp.add( panel )
      }
      frame.pack()
//		frame.setVisible( true )
      frame
   }
}