package puck.javaGraph
package transformations

import nodeKind.Interface
import puck.graph.transformations.MergeMatcherInstances
import puck.graph.transformations.rules.MergingCandidatesFinder
import puck.graph._
import puck.javaGraph.transformations

object JavaTransformationHelper extends MergingCandidatesFinder {

  def mergeMatcherInstances : MergeMatcherInstances =
    JavaMergeMatcherInstances

  override def find(g : DependencyGraph, node : ConcreteNode) : Option[ConcreteNode] = {

    val nid = node.id
    node.kind match {
      case Interface if g.content(nid).nonEmpty =>
        g.concreteNodes.find { other =>
          node.canBeMergedInto(other, None, g) &&
            g.usersOfExcludingTypeUse(nid).forall(!g.interloperOf(_,other.id)) &&
            g.usedByExcludingTypeUse(nid).forall( !g.interloperOf(other.id, _) )
        }
      case _ => None
    }

  }
  def findIn(g : DependencyGraph, method : ConcreteNode, interface : ConcreteNode) : Option[NodeId] =
    (method.kind.kindType, interface.kind.kindType) match {
      case (InstanceValueDecl, TypeDecl) => InterfaceMergeMatcher.findMergingCandidateIn(g, method, interface)
      case _ => None
    }

}
