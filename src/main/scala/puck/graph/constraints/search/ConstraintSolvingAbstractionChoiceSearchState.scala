package puck.graph.constraints.search

import puck.graph.{Logged, ResultT, NodeKind}
import puck.graph.constraints.AbstractionPolicy
import puck.search.SearchState

class ConstraintSolvingAbstractionChoice
( val k : Option[(NodeKind, AbstractionPolicy)] => Unit,
  var remainingChoices : Set[Option[(NodeKind, AbstractionPolicy)]],
  var triedChoices : Set[Option[(NodeKind, AbstractionPolicy)]])
extends ConstraintSolvingChoice[(NodeKind, AbstractionPolicy), ConstraintSolvingAbstractionChoice] {
  def createState(id: Int,
                  prevState: Option[SearchState[ResultT]],
                  currentResult : Logged[ResultT],
                  choices: ConstraintSolvingAbstractionChoice) =
    new ConstraintSolvingAbstractionChoiceSearchState(id, currentResult, choices, prevState)

}


class ConstraintSolvingAbstractionChoiceSearchState
(val id : Int,
 val loggedResult : Logged[ResultT],
 val internal: ConstraintSolvingAbstractionChoice,
 val prevState : Option[SearchState[ResultT]])
extends ConstraintSolvingState[(NodeKind, AbstractionPolicy), ConstraintSolvingAbstractionChoice]
