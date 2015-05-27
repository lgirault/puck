package puck.gui.search

import puck.graph.io.VisibilitySet
import puck.graph.{ResultT, graphOfResult}
import puck.gui.{ConstraintDisplayRequest, ApplyOnCodeRequest, GraphDisplayRequest, SearchStateSeqPrintingRequest}
import puck.search.SearchState

import scala.swing._
import scala.swing.event.{Event, SelectionChanged}


case class StateSelected(state : SearchState[ResultT]) extends Event


class SimpleStateSelector
(map : Map[Int, Seq[SearchState[ResultT]]])
  extends BoxPanel(Orientation.Vertical) {
  val firstLine = new FlowPanel()
  val combobox2wrapper = new FlowPanel()
  val couplingValues = new ComboBox(map.keys.toSeq)

  var searchStateComboBox : ComboBox[SearchState[ResultT]] =
    new ComboBox(map(couplingValues.selection.item))
  combobox2wrapper.contents += searchStateComboBox

  firstLine.contents += couplingValues
  firstLine.contents += combobox2wrapper
  contents += firstLine

  this listenTo couplingValues.selection

  reactions += {
    case SelectionChanged(cb) if cb == couplingValues =>
      combobox2wrapper.contents.clear()
      searchStateComboBox = new ComboBox(map(couplingValues.selection.item))
      combobox2wrapper.contents += searchStateComboBox
      this.revalidate()
      this listenTo searchStateComboBox.selection
      this.publish(StateSelected(selectedState))

    case SelectionChanged(cb) if cb == searchStateComboBox =>
      this.publish(StateSelected(selectedState))
  }

  def selectedState = searchStateComboBox.selection.item

}

class StateSelector
( map : Map[Int, Seq[SearchState[ResultT]]],
  printId : () => Boolean,
  printSig: () => Boolean,
  visibility : VisibilitySet.T)
  extends  SimpleStateSelector(map) {


  val secondLine = new FlowPanel()
  /*secondLine.contents += new Button(""){
      action = new Action("Show"){
        def apply() {
          StateSelector.this publish
            GraphDisplayRequest(couplingValues.selection.item + " " + searchStateComboBox.selection.item.uuid(),
              graphOfResult(searchStateComboBox.selection.item.result), printId(), printSig())
        }
      }
    }
  */

  secondLine.contents += new Button(""){
    action = new Action("Show"){
      def apply() : Unit = {

        val state: SearchState[ResultT] = searchStateComboBox.selection.item
        var id = -1

        StateSelector.this publish SearchStateSeqPrintingRequest(state.uuid()+"history",
          state.ancestors(includeSelf = true), Some({s => id +=1
            id.toString}), printId(), printSig(), visibility)

      }
    }
  }

  secondLine.contents += new Button(""){
    action = new Action("Constraint"){
      def apply() : Unit =  {
        val state: SearchState[ResultT] = searchStateComboBox.selection.item
        StateSelector.this publish ConstraintDisplayRequest(graphOfResult(state.result))
      }
    }
  }

  secondLine.contents += new Button(""){
    action = new Action("Apply"){
      def apply() : Unit = {
        StateSelector.this publish ApplyOnCodeRequest(searchStateComboBox.selection.item.result)
      }
    }
  }
  contents += secondLine

}
