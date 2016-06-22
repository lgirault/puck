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

package puck.gui.svg.actions

import puck.graph._
import puck.graph.constraints.ConstraintsMaps
import puck.graph.constraints.search.TargetedControlWithHeuristic
import puck.graph.io.VisibilitySet
import puck.gui.PrintingOptionsControl
import puck.search._
import puck.util.Logged

import scala.swing._
import scala.swing.Dialog.{Message, Options, Result}

class AutoSolveAction
( publisher : Publisher,
  cm : ConstraintsMaps,
  printingOptionsControl: PrintingOptionsControl,
  strategy : SearchStrategy[DecoratedGraph[Any]],
  control : SearchControl[DecoratedGraph[Any]])
(implicit graphUtils: GraphUtils)
  extends Action("Solve") {

  implicit val graph = control.initialState.graph

  private def dialog[T](res : Search[DecoratedGraph[T]]) : Option[Logged[DependencyGraph]] = {
    val title = "Solve"

    val confirm : Component => Result.Value =
      c =>
        Dialog.showConfirmation(null, c.peer, title, Options.OkCancel, Message.Plain)


    val panel = new AutoSolveResultPanel(publisher, cm, VisibilitySet.allVisible(graph), printingOptionsControl, res)
    confirm(panel) match {
      case Result.Ok => Some( panel.selectedResult)
      case Result.Cancel
           | Result.Closed => None
    }
  }


  override def apply(): Unit = {
    val g = graph.mileStone

    val engine =
      new SearchEngine( strategy, control, Some(1)/*,
        evaluator = Some(GraphConstraintSolvingStateEvaluator)*/)

    engine.explore()

    try {
      puck.actions.printErrOrPushGraph(publisher, "Solve action : ") {
        dialog(engine) match {
          case Some(g) => g.toLoggedTry
          case None => LoggedError("cancelled")
        }
      }
    }
    catch{
      case t : Throwable =>
        println("catched "+ t.getMessage)
        t.printStackTrace()
    }

  }
}



class TargetedAutoSolveAction
( publisher : Publisher,
  cm : ConstraintsMaps,
  violationTarget : ConcreteNode,
  printingOptionsControl: PrintingOptionsControl)
(implicit graph : DependencyGraph,
  graphUtils: GraphUtils)
  extends Action("Solve [BETA - under development]") {

  private def dialog[T](res : Search[DecoratedGraph[T]]) : Option[Logged[DependencyGraph]] = {
    val title = "Solve"

    val confirm : Component => Result.Value =
      c =>
        Dialog.showConfirmation(null, c.peer, title, Options.OkCancel, Message.Plain)

//    if(res.successes.isEmpty){
//      confirm(new JLabel("No solution")) match{
//        case Result.Ok =>
//          val panel = new AutosolveResultPanel(violationTarget, controller, res)
//          val resVal = confirm(panel.peer)
//          Some((resVal, panel.selectedResult))
//        case Result.Cancel => None
//      }
//    }
//    else {
    val panel = AutoSolveResultPanel(publisher, cm, violationTarget, printingOptionsControl, res)
    confirm(panel) match {
        case Result.Ok => Some( panel.selectedResult)
        case Result.Cancel
           | Result.Closed => None
    }
  }


  override def apply(): Unit = {
    //val g = graph.mileStone
    val g = graph.nodes.foldLeft(graph.mileStone){
      case (g, n) => n.kind.kindType match {
        case TypeDecl => g.setMutability(n.id, false)
        case _ => g
      }
    }

    val searchControlStrategy =
      new TargetedControlWithHeuristic(
        graphUtils.Rules,
        g, cm, violationTarget)

    val engine =
      new SearchEngine(
        new BreadthFirstSearchStrategy[(DependencyGraph, Int)],
        searchControlStrategy,
        None/*,
        evaluator = Some(GraphConstraintSolvingStateEvaluator)*/)

    engine.explore()

    try {
      puck.actions.printErrOrPushGraph(publisher, "Solve action : ") {
        dialog(engine) match {
          case Some(g) => g.toLoggedTry
          case None => LoggedError("cancelled")
        }
      }
    }
    catch{
     case t : Throwable =>
       println("catched "+ t.getMessage)
       t.printStackTrace()
    }

  }
}