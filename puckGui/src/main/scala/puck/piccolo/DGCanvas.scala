/*
 * Puck is a dependency analysis and refactoring tool.
 * Copyright (C) 2016 Loïc Girault loic.girault@gmail.com
 *               2016 Mikal Ziane  mikal.ziane@lip6.fr
 *               2016 Cédric Besse cedric.besse@lip6.fr
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   Additional Terms.
 * Author attributions in that material or in the Appropriate Legal
 * Notices displayed by works containing it is required.
 *
 * Author of this file : Loïc Girault
 */

package puck.piccolo
import puck.view.NodeKindIcons
import org.piccolo2d.event.{PBasicInputEventHandler, PInputEvent}
import org.piccolo2d.{PCanvas, PLayer, PNode}
import puck.control.PuckControl
import puck.graph._
import puck.graph.transformations.Transformation.ChangeSource
import puck.graph.transformations.{Edge, RenameOp, Transformation}

import scala.swing._

/**
  * Created by Loïc Girault on 02/06/16.
  */
abstract class BasicGraphCanvas
( val bus : Publisher,
  implicit val nodeKindIcons : NodeKindIcons
) extends PCanvas {
  def graph : DependencyGraph

  val menuBuilder = PiccoloNodeMenu.readOnly(bus, graph)
  val nodeLayer = getLayer
  val edgeLayer = new PLayer()
  getRoot.addChild(edgeLayer)
  getCamera.addLayer(0, edgeLayer)


  var register = new Register(() => graph, edgeLayer)
  getRoot.addAttribute("register", register)

  def getNode(nid : NodeId) : DGExpandableNode  = register.getOrElse(nid, {
    val titleNode = DGTitleNode(graph, nid, nodeKindIcons)
    val n = new DGExpandableNode(titleNode)
    n.titlePnode.addInputEventListener(clickEventHandler(n))
    n.addPropertyChangeListener(PNode.PROPERTY_VISIBLE, register.visibilityPropertyListener)
    register += (nid -> n)
    n
  })


  val rootNode = getNode(graph.rootId)
  nodeLayer addChild rootNode


  def clickEventHandler(n : DGExpandableNode) : PBasicInputEventHandler =
    new PBasicInputEventHandler() {

      override def mousePressed(event: PInputEvent) : Unit =
        if(event.isRightMouseButton){
          val pos = event.getCanvasPosition
          val menu = menuBuilder(BasicGraphCanvas.this, n)


          Swing.onEDT(menu.show(Component wrap BasicGraphCanvas.this,
            pos.getX.toInt, pos.getY.toInt))
        }

      override def mouseClicked(event : PInputEvent) : Unit =
        if(event.getClickCount == 2 ) {
          if(n.contentSize == 0) expand(n)
          else collapse(n)
        }
    }


  def hide(n : DGExpandableNode) : Unit =
    n.removeFromParent()

  def focus(n : DGExpandableNode) : Unit = {
    val p = n.getParent
    p.removeAllChildren()
    p.addChild(n)

  }

  def expand(n : DGExpandableNode) : Unit = puck.ignore(addContent(n))
  def collapse(n : DGExpandableNode) : Unit =  n.clearContent()

  def expandAll(n : DGExpandableNode) : Unit =
    addContent(n) foreach expandAll

  def addContent(n : DGExpandableNode) : Seq[DGExpandableNode] = {

    val content = graph content n.id

    val children = content.toSeq map getNode
    children foreach n.addContent
    children
  }

  def addIncommingUses( n : DGExpandableNode ) : Unit = {
    val uses = for(user <- graph.usersOf(n.id)) yield (user, n.id)
    register addUses uses
  }

  def addOutgoingUses( n : DGExpandableNode ) : Unit = {
    val uses = for(used <- graph.usedBy(n.id)) yield (n.id, used)
    register addUses uses
  }

  def removeIncommingUses( n : DGExpandableNode ) : Unit =
    register removeIncommingUses n.id

  def removeOutgoingUses( n : DGExpandableNode ) : Unit =
    register removeOutgoingUses n.id



  def focus(ns : Set[NodeId]) : Unit = {
    val focusedNodes = ns.foldLeft(Set[NodeId]()){
      case (acc, nid) => acc ++ (graph containerPath nid)
    }
    register = new Register(() => graph, edgeLayer)

    register += (graph.rootId -> rootNode)
    rootNode.clearContent()

    (focusedNodes - graph.rootId) foreach {
      nid =>
        val n = getNode(nid)
        val c = getNode(graph container_! nid)
        c.addContent(n)
    }
  }

}

class DGCanvas
( control : PuckControl,
  nodeKindIcons : NodeKindIcons)
  extends BasicGraphCanvas(control.Bus, nodeKindIcons) {


  def graph = control.graph

  override val menuBuilder = PiccoloNodeMenu(control, control.graph, nodeKindIcons)

  getRoot.addAttribute("control", control)



  addInputEventListener(new MoveNodeDragEventHandler(control, this))
  addInputEventListener(new ArrowDragEventHandler(control, this))



  def applyRec(newGraph: DependencyGraph, oldGraph : DependencyGraph, subRec : Recording) : Unit = {

    subRec.foreach  {
      case Transformation.Add(Edge(ContainsKind(cter, cted))) =>

        register.get(cter) foreach ( _ addContent getNode(cted) )

      case Transformation.Remove(Contains(cter, cted)) =>

        register.get(cter) foreach ( _ rmContent getNode(cted) )

      case t @ ChangeSource(Contains(oldc, tgt), newc) =>

        val n = getNode(tgt)

        register.get(oldc) foreach ( _ rmContent n )
        register.get(newc) foreach ( _ addContent n )

      case Transformation(_, RenameOp(id, _, _)) =>
        val n = getNode(id)
        import puck.graph.ShowDG._
        val newNameWithSig = (newGraph, newGraph getNode id).shows(desambiguatedLocalName)
        n.titlePnode.text.setText(newNameWithSig)


      case _ =>
    }

  }

  import puck.graph.Recording.RecordingOps
  def pushEvent(newGraph: DependencyGraph, oldGraph : DependencyGraph) : Unit = {
    //assert(oldGraph eq graph)
    val subRec : Recording = newGraph.recording.subRecordFromLastMilestone.reverse
    applyRec(newGraph, oldGraph, subRec)
  }
  def popEvent(newGraph: DependencyGraph, oldGraph : DependencyGraph) : Unit = {
    //assert(oldGraph eq graph)
    val subRec : Recording = oldGraph.recording.subRecordFromLastMilestone map (_.reverse)

    applyRec(newGraph, oldGraph, subRec)
  }

  removeInputEventListener(getPanEventHandler)
  removeInputEventListener(getZoomEventHandler)
}





