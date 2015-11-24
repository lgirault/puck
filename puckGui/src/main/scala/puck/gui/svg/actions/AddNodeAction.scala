package puck.gui.svg.actions

import java.awt.event.ActionEvent
import javax.swing.AbstractAction

import puck.graph.io.Visible
import puck.graph.{NodeKind, ConcreteNode}
import puck.gui.svg.SVGController
import puck.graph.io.VisibilitySet._

class AddNodeAction
( host : ConcreteNode,
  controller : SVGController,
  childKind : NodeKind)
extends AbstractAction(s"Add $childKind")
{

  import controller._, graphUtils.{transformationRules => TR}

  override def actionPerformed(actionEvent: ActionEvent): Unit = {
    showInputDialog(s"New $childKind name:").foreach {
      childName =>
        val (n, g) = TR.intro(graph.mileStone, childName, childKind)
        pushGraph(g.addContains(host.id, n.id), display = false)
        controller.expandAll(n.id)
    }
//    childKind match {
//      case Package
//           | Interface
//           | Class =>
//
//      case _ =>
//        JOptionPane.showMessageDialog(null, s"add of $childKind not implemented",
//          "Error", JOptionPane.ERROR_MESSAGE);
//    }

  }
}