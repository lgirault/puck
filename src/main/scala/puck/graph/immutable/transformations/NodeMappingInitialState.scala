package puck.graph.immutable.transformations

import puck.graph.immutable.AccessGraph.NodeId
import puck.graph.immutable.transformations.MappingChoices._
import puck.graph.immutable.{AGNode, Contains, AGEdge, NodeKind}
import puck.javaAG.immutable.nodeKind.JavaRoot
import puck.util.{PuckLog, PuckLogger}

import scala.collection.immutable.HashSet
import scala.util.Try

object NodeTransfoStatus {
  def apply( i : Int) = i match {
    case 1 => Created
    case 0 => Neuter
    case -1 => Deleted
    case _ => throw new Error("Illegal node transfo status value !")
  }
}

sealed abstract class NodeTransfoStatus
case object Created extends NodeTransfoStatus
case object Deleted extends NodeTransfoStatus
case object Neuter extends NodeTransfoStatus

/**
 * Created by lorilan on 30/09/14.
 */

object NodeMappingInitialState{

  def normalizeNodeTransfos[Kind <: NodeKind[Kind], T](l : Seq[Transformation[Kind, T]],
                                                       init : Seq[Transformation[Kind, T]] = Seq()) : (Map[NodeId[Kind], (Int, Kind)], Seq[Transformation[Kind, T]])= {
    val (map, rl) = l.foldLeft( (Map[NodeId[Kind], (Int, Kind)](), init) ){
      case ((m,l1), Transformation(Add, TTNode(id, _, kind, _, _, _ )))=>
        val (i, _) = m.getOrElse(id, (0, JavaRoot) )
        (m + (id -> (i + 1, kind)), l1)
      case ((m,l1), Transformation(Remove, TTNode(id, _, kind, _, _, _ ))) =>
        val (i, _) = m.getOrElse(id, (0, JavaRoot))
        (m + (id -> (i - 1, kind)), l1)

      //removing the dependendency and abstraction of the comparison
      // they are used to compute the change on the graph, its the change themselves we want to compare
      //if added back think to uncommment comparison in RecordingComparator.compare
      /*case ((m,l1), Transformation(_, TTDependency(_, _))) => (m, l1)
      case ((m,l1), Transformation(_, TTAbstraction(_, _, _))) => (m, l1)
      case ((m,l1), Transformation(_, TTConstraint(_,_))) => (m, l1)*/
      case ((m,l1), Transformation(_, TTTypeRedirection(_,_,_, _))) => (m, l1)
      case ((m,l1), t : Transformation[Kind, T]) => (m, t +: l1)
    }

    (map, rl.reverse)
  }

  def switchNodes[Kind <: NodeKind[Kind], T]
          (nodeStatusesMap : Map[NodeId[Kind], (Int, Kind)], init : T)
          (add : (T, NodeId[Kind]) => T) : (T, Seq[NodeId[Kind]], Set[NodeId[Kind]]) = {
    nodeStatusesMap.foldLeft[(T, Seq[NodeId[Kind]], Set[NodeId[Kind]])](init, List(), HashSet()) {
      case ((addAcc, rmAcc, neuterAcc), (node, (i, kind))) =>
        NodeTransfoStatus(i) match {
          case Created => (add(addAcc, node), rmAcc, neuterAcc)
          case Deleted => (addAcc, node +: rmAcc, neuterAcc)
          case Neuter => (addAcc, rmAcc, neuterAcc + node)
        }
    }
  }

  def select[T](l : List[T], pred : T => Boolean) : (Option[T], List[T]) = {
    def aux(l1 : List[T], acc : List[T]) : (Option[T], List[T]) =
      if(l1.isEmpty) (None, l)
      else if(pred(l1.head)) (Some(l1.head), acc reverse_::: l1.tail)
      else aux(l1.tail, l1.head :: acc)

    aux(l, List[T]())
  }


  implicit val defaultVerbosity = (PuckLog.Search, PuckLog.Debug)

  def filterNoise[Kind <: NodeKind[Kind], T](transfos : Seq[Transformation[Kind, T]], logger : PuckLogger):
  Seq[Transformation[Kind, T]] = {

    implicit val defaultVerbosity = 1

    def printRule(name : => String, op1 : => String, op2 : => String, res : => String ){
      logger.writeln(name +" : ")
      logger.writeln(op1)
      logger.writeln("+ " + op2)
      logger.writeln("= " + res)
      logger.writeln("----------------------------------------------")
    }

    def mapUntil( stoppingEdge : AGEdge[Kind],
                  transfos : List[Transformation[Kind, T]])
                (rule : (Transformation[Kind, T] => Transformation[Kind, T])): Seq[Transformation[Kind, T]] = {

      def aux(acc : Seq[Transformation[Kind, T]], l : Seq[Transformation[Kind, T]]) : Seq[Transformation[Kind, T]] ={
        if(l.isEmpty) acc.reverse
        else
          l.head match {
            case Transformation(_, TTRedirection(`stoppingEdge`, _)) => acc.reverse ++: l
            case _ => aux(rule(l.head) +: acc, l.tail)

          }
      }
      aux(List(), transfos)
    }

    // We are going backwards !
    def aux(filteredTransfos : Seq[Transformation[Kind, T]],
            l : Seq[Transformation[Kind, T]],
             removedEdges : Seq[AGEdge[Kind]]): (Seq[Transformation[Kind, T]], Seq[AGEdge[Kind]]) = {
      l match {
        case List() => (filteredTransfos, removedEdges)
        case (op2 @ Transformation(Remove, TTEdge(AGEdge(Contains, n1, n2)))) :: tl =>
          /*val (applied, newTl) = applyRuleOnce[Transformation[Kind]](tl)
          { case Transformation(Add(), TTEdge(AGEdge(Contains(), `n1`, `n2`))) => (true, None)
          case _ => (false, None)
          }
          if(applied) aux(filteredTransfos, newTl)
          else aux(l.head :: filteredTransfos, l)*/
          select[Transformation[Kind,T]](tl,
          { case Transformation(Add, TTEdge(AGEdge(Contains, `n1`, `n2`))) => true
          case _ => false
          }) match {
            case (Some( op1 ), newTl) =>
              printRule("AddDel", op1.toString, op2.toString, "None")
              aux(filteredTransfos, newTl, removedEdges)
            case (None, _) => aux(l.head +: filteredTransfos, l.tail, removedEdges)
          }
        case (op1 @ Transformation(Add, TTRedirection(stopingEdge @ AGEdge(kind, n1, n2), Source(n3)))) :: tl =>
          aux(filteredTransfos, mapUntil(stopingEdge, tl)
          { case op2 @ Transformation(Add, TTRedirection(AGEdge(`kind`, n0, `n2`), Source(`n1`))) =>
            val res = Transformation[Kind,T](Add, TTRedirection(AGEdge(kind, n0, n2), Source(n3)))
            printRule("RedRed_src", op2.toString, op1.toString, res.toString)
            res
          case op2 @ Transformation(Add, TTEdge(AGEdge(`kind`, `n1`, `n2`))) =>
            val res = Transformation[Kind,T](Add, TTEdge(AGEdge(kind, n3, n2)))
            printRule("AddRed_src", op2.toString, op1.toString, res.toString)
            res
          case t => t
          }, removedEdges)

        case (op1 @ Transformation(Add, TTRedirection(stopingEdge @ AGEdge(kind, n1, n2), Target(n3)))) :: tl =>
          aux(filteredTransfos, mapUntil(stopingEdge, tl)
          { case op2 @ Transformation(Add, TTRedirection(AGEdge(`kind`, `n1`, n0), Target(`n2`))) =>
            val res = Transformation[Kind,T](Add, TTRedirection(AGEdge(kind, n1, n0), Target(n3)))
            printRule("RedRed_tgt", op2.toString, op1.toString, res.toString)
            res
          case op2 @ Transformation(Add, TTEdge(AGEdge(`kind`, `n1`, `n2`))) =>
            val res = Transformation[Kind,T](Add, TTEdge(AGEdge(kind, n1, n3)))
            printRule("AddRed_tgt", op2.toString, op1.toString, res.toString)
            res
          case t => t
          }, removedEdges)

        case (t @ Transformation(Add, TTEdge(_))):: tl => aux(t +: filteredTransfos, tl, removedEdges)
        case (Transformation(Remove, TTEdge(e))):: tl => aux(filteredTransfos, tl, e +: removedEdges)

        case hd :: _ => sys.error(hd  + " should not be in list")
      }
    }

    val (normalTransfos, removedEdges) = aux(Seq(), transfos.reverse, Seq())

    removedEdges.foldLeft(normalTransfos){case (normalTransfos0, e) =>
      normalTransfos0 filter {
        case Transformation(Add, TTEdge(`e`)) => false
        case _ => true
      }
    }

  }

  /*def filterNoise[Kind <: NodeKind[Kind]](transfos : List[Transformation[Kind]], logger : Logger[Int]):
  List[Transformation[Kind]] = {

    def printRule(name : => String, op1 : => String, op2 : => String, res : => String ){
      logger.writeln(name +" : ")
      logger.writeln(op1)
      logger.writeln("+ " + op2)
      logger.writeln("= " + res)
      logger.writeln("----------------------------------------------")
    }

    def applyRuleOnce[T](l : List[T])(rule : T => (Boolean, Option[T])) : (Boolean, List[T]) = {
      def aux(l1 : List[T], acc : List[T]) : (Boolean, List[T]) =
        if(l1.isEmpty) (false, l)
        else rule(l1.head) match {
          case (true, None) => (true, acc reverse_::: l1.tail)
          case (true, Some(t)) => (true, acc reverse_::: t :: l1.tail)
          case (false, _ ) => aux(l1.tail, l1.head :: acc)
        }
      aux(l, List[T]())
    }

    def aux(l1 : List[Transformation[Kind]],
            l2 : List[Transformation[Kind]]): List[Transformation[Kind]] = {
      if(l2.isEmpty) l1.reverse
      else{
        val (applied, tl) = l2.head match {
          case (op1 @ Transformation(Add(), TTEdge(AGEdge(Contains(), n1, n2)))) =>
            applyRuleOnce[Transformation[Kind]](l2.tail)
            {
              //case Transformation(Add(), TTEdge(AGEdge(Contains(), `n1`, `n2`))) => true
              case op2 @ Transformation(Remove(), TTEdge(AGEdge(Contains(), `n1`, `n2`))) =>
                printRule("AddDel", op1.toString, op2.toString, "None")
                (true, None)
              case op2 @ Transformation(Add(), TTRedirection(AGEdge(Contains(), `n1`, `n2`), Source(n3))) =>
                val res = Transformation(Add(), TTEdge(AGEdge.contains(n3, n2)))
                printRule("AddRed_Src", op1.toString, op2.toString, res.toString)
                (true, Some(res))

              case Transformation(Add(), TTRedirection(AGEdge(Contains(), `n1`, `n2`), Target(_))) =>
                sys.error("contains redirection target should not happen !")
              case _ => (false, None)
            }

          case (op1 @ Transformation(Add(), TTEdge(AGEdge(kind, n1, n2)))) =>
            applyRuleOnce[Transformation[Kind]](l2.tail)
            { case duplicate @ Transformation(Add(), TTEdge(AGEdge(`kind`, `n1`, `n2`))) =>
              printRule("AddAdd", op1.toString, duplicate.toString, duplicate.toString)
              (true, Some(duplicate))
            case op2 @ Transformation(Add(), TTRedirection(AGEdge(`kind`, `n1`, `n2`), Source(n3))) =>
              val res = Transformation(Add(), TTEdge(kind(n3, n2)))
              printRule("AddRed_Src", op1.toString, op2.toString, res.toString)
              (true, Some(res))
            case op2 @Transformation(Add(), TTRedirection(AGEdge(`kind`, `n1`, `n2`), Target(n3))) =>
              val res = Transformation(Add(), TTEdge(kind(n1, n3)))
              printRule("AddRed_Tgt", op1.toString, op2.toString, res.toString)
              (true, Some(res))

            case t => (false, None)
            }
          case t => sys.error(t  + " should not be in list")
        }

        if(applied) aux(l1, tl)
        else aux(l2.head :: l1, tl)
      }

    }

    aux(List(), transfos)
  }*/

  /* def filterDuplicates[Kind <: NodeKind[Kind]](transfos : List[Transformation[Kind]]): List[Transformation[Kind]] = {
     /*can be needed for cases like
      [... add(a,b) .. red((a,b), tgt(c)) ... add(a,d) .. red((a, d), tgt(c)) ... ]
      */
     def aux(l0 : List[Transformation[Kind]],
             l1 : List[Transformation[Kind]]) : List[Transformation[Kind]]= {
       if(l1.isEmpty) l0.reverse
       else aux(l1.head :: l0, l1.tail filter { _ != l1.head })
     }

     aux(List(), transfos)
   }*/

}

class NodeMappingInitialState[Kind <: NodeKind[Kind], T]
(initialTransfos : Seq[Transformation[Kind, T]],
 eng : RecordingComparator[Kind, T],
 lr1 : Seq[Transformation[Kind, T]],
 lr2 : Seq[Transformation[Kind, T]],
 k: Try[ResMapping[Kind]] => Unit,
 logger : PuckLogger)
  extends NodeMappingState(0, eng, null, null, None) {

  //println("creating initial state")
  //println("Comparing %s and %s".format(recording1, recording2))


  import NodeMappingInitialState._


  val (nodeStatuses, remainingTransfos1) = normalizeNodeTransfos(lr1, initialTransfos)

  val (nodeStatuses2, remainingTransfos2) = normalizeNodeTransfos(lr2, initialTransfos)

  /* def switch(nts : NodeTransfoStatus, c : Int, d : Int, n : Int) = nts match {
     case Created() => (c + 1 , d , n)
     case Deleted() => (c, d + 1, n)
     case Neuter() => (c, d, n + 1)
   }*/

  val (initialMapping, removedNode, neuterNodes) =
    switchNodes(nodeStatuses, Map[NodeId[Kind], Option[NodeId[Kind]]]()){
      case (m, n) => m + (n -> None)
    }

  val (nodesToMap, otherRemovedNodes, otherNeuterNodes) =
    switchNodes(nodeStatuses2, Seq[NodeId[Kind]]()){case (l, n) => n +: l}



  var triedAll0 = false

  override def triedAll = triedAll0

  import puck.graph.immutable.transformations.NodeMappingInitialState.defaultVerbosity

  def printlnNode(n : AGNode[Kind, T]){
    logger.writeln("%d = %s(%s)".format(n.id, n.kind, n.fullName))
  }


  override def executeNextChoice() {
    triedAll0 = true

    if( nodesToMap.length != initialMapping.size ||
      !(removedNode forall otherRemovedNodes.contains)) {
      val sameNumberOfNodesToMap = nodesToMap.length == initialMapping.size
      val sameRemovedNodes = removedNode forall otherRemovedNodes.contains
      logger.writeln("sameNumberOfNodesToMap  = " + sameNumberOfNodesToMap)

      logger.writeln("same number of removed nodes = " + (removedNode.length == otherRemovedNodes.length))
      logger.writeln("same removed nodes = " + sameRemovedNodes)

      logger.writeln("initialMapping : %s, %d remaining transfo".format(initialMapping, remainingTransfos1.size))

      logger.writeln("created nodes :")
      initialMapping.keys foreach printlnNode
      logger.writeln("neuter nodes : ")
      neuterNodes foreach printlnNode

      logger.writeln("*******************************************************")
      logger.writeln("")
      logger.writeln("")

      logger.writeln("nodes to map : %s, %d remaining transfo".format(nodesToMap, remainingTransfos2.size))

      logger.writeln("created nodes :")
      nodesToMap foreach printlnNode
      logger.writeln("neuter nodes : ")
      otherNeuterNodes foreach printlnNode

      logger.writeln("recording1")
      lr1 foreach { t => logger.writeln(t.toString)}

      logger.writeln("*******************************************************")
      logger.writeln("")
      logger.writeln("")

      logger.writeln("recording2")
      lr2 foreach { t => logger.writeln(t.toString)}

      throw new NoSolution()
    }
    else {


      logger.writeln("initialMapping : %s, %d remaining transfo".format(initialMapping, remainingTransfos1.size))

      logger.writeln("created nodes :")
      initialMapping.keys foreach printlnNode
      logger.writeln("neuter nodes : ")
      neuterNodes foreach printlnNode

      logger.writeln("")
      logger.writeln("")
      remainingTransfos1 foreach { t => logger.writeln(t.toString)}

      logger.writeln("*******************************************************")
      logger.writeln("***************** filtering 1 *************************")
      logger.writeln("*******************************************************")

      val filteredTransfos1 = if(neuterNodes.nonEmpty) filterNoise(remainingTransfos1, logger)
      else remainingTransfos1

      logger.writeln("*******************************************************")
      logger.writeln("*******************************************************")

      filteredTransfos1 foreach { t => logger.writeln(t.toString)}

      logger.writeln("")
      logger.writeln("")
      logger.writeln("*******************************************************")
      logger.writeln("*******************************************************")
      logger.writeln("************************* and *************************")
      logger.writeln("*******************************************************")
      logger.writeln("*******************************************************")
      logger.writeln("")
      logger.writeln("")

      logger.writeln("nodes to map : %s, %d remaining transfo".format(nodesToMap, remainingTransfos2.size, logger))

      logger.writeln("created nodes :")
      nodesToMap foreach printlnNode
      logger.writeln("neuter nodes : ")
      otherNeuterNodes foreach printlnNode

      logger.writeln("")
      logger.writeln("")
      remainingTransfos2 foreach { t => logger.writeln(t.toString)}


      logger.writeln("*******************************************************")
      logger.writeln("***************** filtering 2 *************************")
      logger.writeln("*******************************************************")

      val filteredTransfos2 = if(otherNeuterNodes.nonEmpty) filterNoise(remainingTransfos2, logger)
      else remainingTransfos2

      logger.writeln("*******************************************************")
      logger.writeln("*******************************************************")

      filteredTransfos2 foreach { t => logger.writeln(t.toString)}

      if(filteredTransfos1.length != filteredTransfos2.length)
        throw new NoSolution()

      eng.compare(filteredTransfos1, filteredTransfos2, initialMapping, nodesToMap)
      /*eng.compare(filteredTransfos1, filteredTransfos2,
        neuterNodes.foldLeft(initialMapping){ (m, n) => m + (n -> None) },
        nodesToMap ++ otherNeuterNodes)*/
      //eng.compare(remainingTransfos1, remainingTransfos2, initialMapping, nodesToMap)

    }


  }
}


