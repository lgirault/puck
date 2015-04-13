package puck.graph.transformations

import puck.graph._
import puck.graph.constraints.AbstractionPolicy

sealed trait TransformationTarget{
  type GraphT= DependencyGraph
  def execute(g: GraphT, op : Operation) : GraphT
  def productPrefix : String
}

case class TTCNode(n : ConcreteNode) extends TransformationTarget {

  def execute(g: GraphT, op : Operation) = op match {
    case Add => g.addConcreteNode(n)
    case Remove => g.removeConcreteNode(n.id)
  }
}

case class TTVNode(n : VirtualNode) extends TransformationTarget {
  def execute(g: GraphT, op : Operation) = ???
}

case class TTEdge(edge : DGEdge)
  extends TransformationTarget {

  def execute(g: GraphT, op : Operation) = op match {
    case Add =>edge.createIn(g)
    case Remove => edge.deleteIn(g)
  }
}

sealed abstract class Extremity{
  val node : NodeId
  def create(n : NodeId) : Extremity
  def productPrefix : String
  /*def apply[K <: NodeKind[K]](e : AGEdge[K]): AGNode[K]*/
}
case class Source(node : NodeId) extends Extremity{
  /*def apply[K <: NodeKind[K]](e : AGEdge[K]) = e.source*/
  def create(n : NodeId) : Extremity = Source(n)

}
case class Target(node : NodeId) extends Extremity  {
  /*def apply[K <: NodeKind[K]](e : AGEdge[K]) = e.target*/
  def create(n : NodeId) : Extremity = Target(n)
}

case class TTRedirection(edge : DGEdge, extremity : Extremity)
  extends TransformationTarget{

  val withMerge = false
  def execute(g: GraphT, op : Operation) = (op, extremity) match {
    case (Add, Target(newTarget)) => edge.changeTarget(g, newTarget)
    case (Remove, Target(newTarget)) => DGEdge(edge.kind, edge.source, newTarget).changeTarget(g, edge.target)
    case (Add, Source(newSource)) => edge.changeSource(g, newSource)
    case (Remove,Source(newSource)) => DGEdge(edge.kind, newSource, edge.target).changeSource(g, edge.source)
  }
}

class RedirectionWithMerge(edge : DGEdge, extremity : Extremity)
  extends TTRedirection(edge, extremity){
  override val productPrefix = "RedirectionWithMerge"

  override def copy(edge : DGEdge = edge, extremity: Extremity = extremity) =
    new RedirectionWithMerge(edge, extremity)

  override val withMerge = true

  override def execute(g: GraphT, op : Operation) = (op, extremity) match {
    case (Add, _) => super.execute(g, op)
    case (Remove, _) => edge.createIn(g)
  }

}

case class TTTypeRedirection
(typed : NodeId,
 styp : Option[Type],
 oldUsee: NodeId,
 newUsee : NodeId)
  extends TransformationTarget{

  override def execute(g: DependencyGraph, op: Operation) = op match {
    case Add => g.changeType(typed, styp, oldUsee, newUsee)
    case Remove => g.changeType(typed, styp, newUsee, oldUsee)
  }
}

case class TTAbstraction
(impl: NodeId,
 abs: NodeId,
 policy: AbstractionPolicy)
 extends TransformationTarget{

  def execute(g: GraphT, op : Operation) = op match {
    case Add => g.addAbstraction(impl, (abs, policy))
    case Remove => g.removeAbstraction(impl, (abs, policy))
  }
}

/*
case class TTDependency(dominant : AGEdge,
                                                   dominated : AGEdge)
  extends TransformationTarget[Kind,T]{

  def execute(g: GraphT, op : Operation) = ???
}




case class TTConstraint(ct : Constraint,
                                                friend : AGNode)
  extends TransformationTarget{

  def execute(op : Operation) = ???
}*/