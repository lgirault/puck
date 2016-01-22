package puck.graph

object Metrics {


  def outgoingDependencies(graph: DependencyGraph, root: NodeId): Set[NodeIdP] =
    graph.subTree(root, includeRoot = true).foldLeft(Set[NodeIdP]()) {
      (acc0, id) => graph.usedByExcludingTypeUse(id).foldLeft(acc0) {
        (acc, user) =>
          if (graph.contains_*(root, user)) acc
          else acc + ((id, user))
      }
    }

  def incomingDependencies(graph: DependencyGraph, root: NodeId): Set[NodeIdP] =
    graph.subTree(root, includeRoot = true).foldLeft(Set[NodeIdP]()) {
      (acc0, id) => graph.usersOfExcludingTypeUse(id).foldLeft(acc0) {
        (acc, user) =>
          if (graph.contains_*(root, user)) acc
          else acc + ((user, id))
      }
    }

  def internalDependencies(graph: DependencyGraph, root: NodeId): Set[NodeIdP] =
    graph.subTree(root, includeRoot = true).foldLeft(Set[NodeIdP]()){
      (acc0, id) => graph.usedByExcludingTypeUse(id).foldLeft(acc0) {
        (acc, used) =>
          if (graph.contains_*(root, used))
            acc + ((id, used))
          else acc
      }
    }


  def provides(graph: DependencyGraph, provider: NodeId, other: NodeId) = {
    val these = graph.subTree(provider)
    val others = graph.subTree(other)

    these.exists { t =>
      others.exists { o =>
        graph.uses(o, t) &&
          !(graph.contains_*(other, o) && graph.contains_*(other, t))
      }
    }
  }

  private def connection(graph: DependencyGraph, node: DGNode, p: DGNode => Boolean) = {
    graph.nodes.foldLeft(Set[NodeId]()) { (acc, n) =>
      if (n.id == node.id || n.kind.kindType != node.kind.kindType) acc
      else if (p(n)) acc + n.id
      else acc
    }
  }

  def providers(graph: DependencyGraph, id: NodeId): Set[NodeId] =
    connection(graph, graph.getNode(id), n => provides(graph, n.id, id))

  def clients(graph: DependencyGraph, id: NodeId): Set[NodeId] =
    connection(graph, graph.getNode(id), n => provides(graph, id, n.id))

  def relativeCohesion(graph: DependencyGraph, userTree: NodeId, candidate: NodeId): Double = {
    val usedElts = outgoingDependencies(graph, userTree).map(_.used)
    relativeCohesion(graph, usedElts, candidate)
  }

  def relativeCohesion(graph: DependencyGraph, usedElts: Set[NodeId], candidate: NodeId): Double = {
    val subTree = graph.subTree(candidate).toSet
    usedElts.intersect(subTree).size.toDouble / usedElts.union(subTree).size
  }

  def cohesion(graph: DependencyGraph, root: NodeId): Double =
    cohesion0(internalDependencies(graph, root).size,
      outgoingDependencies(graph, root).size,
      incomingDependencies(graph, root).size)

  def cohesion0(internalDcies : Int, outgoingDcies : Int, incomingDcies : Int): Double =
    internalDcies.toDouble / (outgoingDcies + incomingDcies + internalDcies).toDouble


  def coupling(graph: DependencyGraph, root: NodeId): Double =
    coupling0(providers(graph, root).size, clients(graph, root).size,
      internalDependencies(graph, root).size,
      outgoingDependencies(graph, root).size,
      incomingDependencies(graph, root).size)
  
  def coupling0(providers : Int, clients : Int,
                internalDcies : Int,
                outgoingDcies : Int,
                incomingDcies : Int): Double =
    1 - (providers + clients ).toDouble / (outgoingDcies + incomingDcies + internalDcies).toDouble


  def nameSpaceCoupling(graph : DependencyGraph) =
    graph.concreteNodes.foldLeft(0 : Double){
      (acc, n) => n.kind.kindType match {
        case NameSpace =>
          val c = Metrics.coupling(graph, n.id)
          if(c.isNaN) acc
          else acc + c
        case _ => acc
      }}



  def typeWeight(g: DependencyGraph, typeId: NodeId): Double = {
    val t = g.getNode(typeId)


    def usesConnectedComponent
    (border: Set[NodeId],
     component: Set[NodeId],
     unreached: Set[NodeId]
      ): (Set[NodeId], Set[NodeId]) =
      if (border.isEmpty) (component, unreached)
      else {
        val n = border.head
        val neighbors = (g.usedByExcludingTypeUse(n) ++ g.usersOfExcludingTypeUse(n) - n) intersect unreached

        //        println(s" n = ${g.getNode(n)}\n neighbors = ${neighbors.map(g.getNode)}")

        val (newBorder, newUnreached) =
          neighbors.foldLeft((border - n, unreached)) {
            case ((accBorder, accUnreached), neighbor) =>
              if (component contains neighbor) (accBorder, accUnreached)
              else (accBorder + neighbor, unreached - neighbor)
          }
        usesConnectedComponent(newBorder, component + n, newUnreached)
      }

    def components(i: Int, unreached: Set[NodeId]): Int =
      if (unreached.isEmpty) i
      else {
        val n = unreached.head
        val (_, newUnreached) = usesConnectedComponent(Set(n), Set(), unreached - n)
        components(i + 1, newUnreached)
      }

    //    println("######################################################")
    //    println("######################################################")
    //    println("######################################################")

    val content = g.content(typeId)
    val s = Math.max(1, content.size)
    components(0, content).toDouble / s.toDouble
  }



  def numberOfCycles(edges : Set[NodeIdP]) : Int = {
    val nodes = edges.foldLeft(Set[NodeId]()){
      case (acc, (s,t)) => acc + s + t
    }

    val sortedEdges = edges.groupBy(_.user)
    var components = List[List[Int]]()

    //tarjan algorithm cf https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm

    var index : Int= 0
    var indexes = Map[NodeId, Int]()
    var lowLinks = Map[NodeId, Int]()
    var s = List[Int]()


    def strongConnect(nid : NodeId) : Unit = {
      indexes += (nid -> index)
      lowLinks += (nid -> index)
      index += 1
      s ::= nid

      sortedEdges.get(nid) match {
        case None => ()
        case Some(es) =>
          es.foreach{
            e =>
              if(!(indexes isDefinedAt e.used)) {
                strongConnect(e.used)
                val newLowLink = Math.min(lowLinks(nid), lowLinks(e.used))
                lowLinks += (nid -> newLowLink)
              }
              else if(s contains e.used){
                val newLowLink = Math.min(lowLinks(nid), indexes(e.used))
                lowLinks += (nid -> newLowLink)
              }
          }
        if(lowLinks(nid) == indexes(nid)){
          var w : Int = -1
          var c = List[Int]()
          do {
            w = s.head
            s = s.tail
            c ::= w
          } while(nid != w)
          components ::= c
        }
      }

    }

    nodes.foreach {
      nid =>
        if(!(indexes isDefinedAt nid))
          strongConnect(nid)
    }

    components.size
  }


//  def weight
//  ( g: DependencyGraph,
//    lightKind : NodeKind,
//    cyclePenalty: Double = 5): Double = {
//
//    val types = g.nodes.filter { _.kind.kindType == TypeDecl } map ( _.id)
//
//    val tsByNameSpaces = types.groupBy(g.hostNameSpace).toList
//
//    val (w, extraNSdeps) = tsByNameSpaces.foldLeft((0d, Set[NodeIdP]())){
//      case ((wAcc, extraNSdepAcc), (hns, ts)) =>
//
//        val (newWacc, intraNSdep, newExtraNSdep) =
//          ts.foldLeft((wAcc, Set[NodeIdP](), extraNSdepAcc)) {
//            case ((wAcc0, intraNSdepAcc0, extraNSdepAcc0), t) =>
//              val outDep = outgoingDependencies(g, t)
//              val (intraNSdep, extraNSdep) =
//                outDep.partition(u => g.hostNameSpace(u.used) == hns)
//
//              val intraNSdep0 = intraNSdep map {
//                case (user, used) => (g.hostTypeDecl(user), g.hostTypeDecl(used))
//              }
//
//              val extraNSdep0 = extraNSdep map {
//                case (user, used) => (g.hostNameSpace(user), g.hostNameSpace(used))
//              }
//              (wAcc0 + typeWeight(g, t),
//                intraNSdepAcc0 ++ intraNSdep0,
//                extraNSdepAcc0 ++ extraNSdep0)
//          }
//
//
//        val (lightUses, regularUses) =
//          intraNSdep partition (u => g.getNode(u.used).kind == lightKind)
//
//        val intraUsesWeight =
//          lightUses.size.toDouble / 2 + regularUses.size
//
//
//        val cycleWeight = numberOfCycles(intraNSdep).toDouble * (cyclePenalty /2)
//
//        (newWacc + intraUsesWeight + cycleWeight, newExtraNSdep)
//    }
//
//
//    w + numberOfCycles(extraNSdeps) * cyclePenalty
//  }
}

