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

package puck.view
package menus

import puck.control.PuckControl
import puck.control.actions.ChooseAbsAndRedirectMultiAction
import puck.graph.{ConcreteNode, DependencyGraph, NodeId, NodeIdP}
import puck.view.svg.actions.TargetedAutoSolveAction

/**
  * Created by Loïc Girault on 17/12/15.
  */
object ForbiddenDependencyTargetMenu {
  def apply(controller : PuckControl,
            g : DependencyGraph,
            target : ConcreteNode,
            nodeKindIcons: NodeKindIcons) :  ForbiddenDependencyTargetMenu =
    new ForbiddenDependencyTargetMenu(controller, g, List(), None, false, target, nodeKindIcons)
}
class ForbiddenDependencyTargetMenu
( controller : PuckControl,
  g : DependencyGraph,
  selectedNodes: List[NodeId],
  selectedEdge : Option[NodeIdP],
  blurryEdgeSelection : Boolean,
  target : ConcreteNode,
  nodeKindIcons: NodeKindIcons)
  extends ConcreteNodeMenu(controller, g,
    selectedNodes,
    selectedEdge,
    blurryEdgeSelection,
    target,
    nodeKindIcons) {

  import controller._

  //add(new ManualSolveAction(publisher, targetNode))
  controller.constraints foreach {
   cts =>

     val wu = cts.wrongUsers(g,target.id)

     val abstractions = graph.abstractions(target.id)

     if (abstractions.nonEmpty)
       contents += new ChooseAbsAndRedirectMultiAction(controller.Bus, g, wu,
         target.id, abstractions.toSeq)

    contents += new TargetedAutoSolveAction(Bus,
      cts, controller.mutabilitySet,
      target, printingOptionsControl)(graph, graphUtils, nodeKindIcons)

  }

}
