package puck.javaGraph
package transformations

import nodeKind._
import puck.graph.DependencyGraph._
import puck.graph.constraints.{SupertypeAbstraction, AbstractionPolicy}
import puck.graph._
import puck.util.Collections.traverse
import puck.graph.transformations.rules.{Redirection, Intro}

import scalaz._

object JavaIntro extends Intro {

  override def absIntroPredicate( graph : DependencyGraph,
                                  impl : DGNode,
                                  absPolicy : AbstractionPolicy,
                                  absKind : NodeKind) : NodePredicateT = {
    (impl.kind, absPolicy) match {
      case (Method, SupertypeAbstraction)
           | (AbstractMethod, SupertypeAbstraction) =>
        (graph, potentialHost) => !graph.interloperOf(graph.container(impl.id).get, potentialHost.id)
      case _ => super.absIntroPredicate(graph, impl, absPolicy, absKind)
    }
  }

  override def abstractionName( g: DependencyGraph, impl: ConcreteNode, abskind : NodeKind, policy : AbstractionPolicy) : String = {
    if (impl.kind == Constructor)
      "create"
    else
      (abskind, policy) match {
        case (Method, SupertypeAbstraction)
             | (AbstractMethod, SupertypeAbstraction) => impl.name
        case _ => super.abstractionName(g, impl, abskind, policy)

      }
  }

  override def createNode
  ( graph : DependencyGraph,
    localName : String,
    kind : NodeKind,
    th : Option[Type],
    mutable : Mutability = true
    ) : (ConcreteNode, DependencyGraph) = {
    val (n, g) = super.createNode(graph, localName, kind, th, mutable)
    kind match {
      case Class =>
        val (ctor, g1) = createNode(g, localName, Constructor,
            Some(new MethodType(Tuple(List()), NamedType(n.id))))
        (n, g1.addContains(n.id, ctor.id))

      case _ => (n, g)
    }
  }


  def insertInTypeHierarchy(g : DependencyGraph, classId : NodeId, interfaceId : NodeId) : DependencyGraph =
    g.directSuperTypes(classId).foldLeft(g){ (g0, superType) =>
      g0.changeSource(DGEdge.isa(classId, superType), interfaceId)
    }

  def addTypesUses(g : DependencyGraph, node : ConcreteNode) : DependencyGraph =
    node.styp.map(_.ids) match {
      case None => g
      case Some(typesUsed) =>
        typesUsed.foldLeft(g){(g0, tid) => g0.addUses(node.id, tid)}
    }

  def createAbstractMethod(g : DependencyGraph, meth : ConcreteNode,
                           clazz : ConcreteNode, interface : ConcreteNode) : Try[DependencyGraph] ={
    def addContainsAndRedirectSelfType
    (g: DependencyGraph, methodNode: ConcreteNode): Try[DependencyGraph] = {
      if(methodNode.kind != AbstractMethod)
        -\/(new DGError(s"$methodNode should be an abstract method !"))
      else \/- {
        g.addContains(interface.id, methodNode.id)
          //TODO check why it is not needed
          //addTypesUses(g4, absChild)
          .changeType(methodNode.id, methodNode.styp, clazz.id, interface.id)}
    }

    createAbstraction(g, meth, AbstractMethod,  SupertypeAbstraction) flatMap {
      case (absMethod, g21) => addContainsAndRedirectSelfType(g21, absMethod)
    }
  }

  def changeSelfTypeBySuperInMethodSignature(g : DependencyGraph, meth : ConcreteNode,
                                             clazz : ConcreteNode, interface : ConcreteNode): Try[DependencyGraph] ={

    val g1 = g.changeContravariantType(meth.id, meth.styp, clazz.id, interface.id)

    if(g1.uses(meth.id, clazz.id)) {
      g.logger.writeln(s"interface creation : redirecting ${DGEdge.uses(meth.id, clazz.id)} target to $interface")
      Redirection.redirectUsesAndPropagate(g1, DGEdge.uses(meth.id, clazz.id), interface.id, SupertypeAbstraction)
    }
    else \/-(g1)
  }


  def membersToPutInInterface(g : DependencyGraph, clazz : ConcreteNode) : Seq[ConcreteNode] = {

    def canBeAbstracted(member : ConcreteNode) : Boolean = {
      //the originSibling arg is needed in case of cyclic uses
      def aux(originSibling : ConcreteNode)(member: ConcreteNode): Boolean = {

        def sibling: NodeId => Boolean =
          sid => g.contains(clazz.id, sid) && sid != originSibling.id

        member.kind match {
          case ck: MethodKind =>
            val usedNodes = g.usedBy(member.id)
            usedNodes.isEmpty || {
              val usedSiblings = usedNodes filter sibling
              usedSiblings.map(g.getConcreteNode).forall {
                used0 => aux(member)(used0) || {
                  val typeUses = g.typeUsesOf((member.id, used0.id))
                  typeUses.forall { DGEdge.uses(_).selfUse }
                }
              }
            }
          case _ => false
        }
      }
      aux(member)(member)
    }

    g.content(clazz.id).foldLeft(Seq[ConcreteNode]()){
      (acc, mid) =>
        val member = g.getConcreteNode(mid)
        if(canBeAbstracted(member)) member +: acc
        else acc
    }
  }

  def createInterfaceAndReplaceBySuperWherePossible(g : DependencyGraph, clazz : ConcreteNode) : Try[(ConcreteNode, DependencyGraph)] = {
    val classMembers = g.content(clazz.id)

    for{
      itcGraph <- super.createAbstraction(g, clazz, Interface, SupertypeAbstraction).map {
        case (itc, g0) => (itc, insertInTypeHierarchy(g0, clazz.id, itc.id))
      }

      (interface, g1) = itcGraph
      members = membersToPutInInterface(g1, clazz)
      g2 <- traverse(members, g1){ (g0, member) =>
        createAbstractMethod(g0, member, clazz, interface)
      }

      g3 <- traverse(members, g2.addIsa(clazz.id, interface.id)){ (g0, child) =>
        (child.kind, child.styp) match {
          // even fields can need to be promoted if they are written
          //case Field() =>
          case (ck : MethodKind, Some(MethodType(_, _)))  =>
            changeSelfTypeBySuperInMethodSignature(g0, child, clazz, interface)
          case _ => \/-(g0)
        }
      }
    } yield {
      logInterfaceCreation(g3, interface)
      (interface, g3)
    }
  }

  def logInterfaceCreation(g : DependencyGraph, itc : ConcreteNode) : Unit = {
    import ShowDG._
    g.logger.writeln(s"interface $itc created, contains : {")
    g.logger.writeln(g.content(itc.id).map(showDG[NodeId](g).show).mkString("\n"))
    g.logger.writeln("}")
  }

  override def createAbstraction(g : DependencyGraph,
                                 impl: ConcreteNode,
                                 abskind : NodeKind ,
                                 policy : AbstractionPolicy) : Try[(ConcreteNode, DependencyGraph)] = {

    (abskind, policy) match {
      case (Interface, SupertypeAbstraction) =>
        createInterfaceAndReplaceBySuperWherePossible(g, impl)

      case (AbstractMethod, SupertypeAbstraction) =>
        //no (abs, impl) or (impl, abs) uses
        \/-(createAbsNode(g, impl, abskind, policy))

      case (ConstructorMethod, _) =>
        super.createAbstraction(g, impl, abskind, policy) map { case (abs, g0) =>
          (abs, addTypesUses(g0, abs))
        }

      case _ => super.createAbstraction(g, impl, abskind, policy)
    }
  }

  override def abstractionCreationPostTreatment(g: DependencyGraph,
                                                implId : NodeId,
                                                absId : NodeId,
                                                policy : AbstractionPolicy) : DependencyGraph = {
    val abstraction = g.getNode(absId)
    (abstraction.kind, policy) match {
      case (AbstractMethod, SupertypeAbstraction) =>
        val implContainer = g.container(implId).get
        val thisClassNeedsImplement = (g.abstractions(implContainer) find
          {case (abs, absPolicy) => absPolicy == SupertypeAbstraction &&
            abs == g.container(absId).get}).isEmpty

        if(!thisClassNeedsImplement) g
        else {
          val absContainer = g.container(absId).get
          val g1 = g.addUses(implContainer, absContainer)
            .addIsa(implContainer, absContainer)

          g1.content(absId).foldLeft(g1){
            case (g0, absMethodId) => val absMeth = g0.getConcreteNode(absMethodId)
              g0.changeType(absMethodId, absMeth.styp, implId, absId)
          }
        }
      case _ => g
    }
  }
}