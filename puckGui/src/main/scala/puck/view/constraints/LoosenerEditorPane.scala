package puck.view.constraints

import puck.graph.DependencyGraph
import puck.graph.constraints._
import puck.view._
import puck.view.util.ResizeableOKCancelDialog

import scala.swing.Dialog.Result
import scala.swing._


object LoosenerEditorDialog{
  type RangeName = String
  def apply
  ( graph : DependencyGraph,
    setNames : => Seq[String],
    namedSets : String => NamedRangeSet,
    constraint : Constraint )
  ( implicit nodeKindIcons : NodeKindIcons )  : Option[Constraint] = {
    val cep = new LoosenerEditorPane(graph, setNames, namedSets, constraint)
    ResizeableOKCancelDialog(cep) match {
      case Result.Ok => Some(cep.constraint)
      case _ => None
    }
  }
}

class LoosenerEditorPane
(graph : DependencyGraph,
 setNames : => Seq[String],
 namedSets : String => NamedRangeSet,
 constraintIn : Constraint)
(implicit nodeKindIcons : NodeKindIcons)
  extends BoxPanel (Orientation.Vertical){

  val friendsRangePane = new RangeSetPane(graph, setNames, namedSets, "", constraintIn.owners )
  val ownersRangePane  = new RangeSetPane(graph, setNames, namedSets, "friend-of", constraintIn.friends)

  contents += ownersRangePane
  contents += friendsRangePane

  def constraint =
    Constraint(
      if(ownersRangePane.rangeSet.isEmpty) constraintIn.owners
      else ownersRangePane.rangeSet,

      RangeSet.empty,

      RangeSet.empty,

      if(friendsRangePane.rangeSet.isEmpty) constraintIn.friends
      else friendsRangePane.rangeSet)

}





