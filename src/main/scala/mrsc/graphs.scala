package mrsc

import scala.annotation.tailrec
/*! # SCP Graph Abstraction
 
 At the heart of MRSC is a mini-framework for manipulating SC graphs.
 In MRSC SC Graph is a special kind of graph that can be seen as a tree (skeleton), 
 some leaves of which can have loopbacks.
 Note that loopbacks can start only from leaves. 
 /Q: Is there a term for such graphs in graph theory?/
 
 Here is the simplest SC graph. There are two loopbacks: j⇢c and h⇢d. 
 There is also a concept of a path. The path to the node g is [0,0,1].

         . c
        .  ↓
      .    d
     .     ↓  .  
    .      e   . 
    .   ↙ ↓ ↘ .  
    .  f   g   h
     . ↓
       j
       
 SC graphs in MRSC are used in three forms:
 
 * `Graph` - good for top-down traversals (a node knows about its outs).
 * `CoGraph` - good for easy bottom-up traversals (a node knows about in).
 * `PartialCoGraph` - good for using in multi-result supercompilation 
    (cograph consists of complete and incomplete parts, 
    and operation to add outs to incomplete nodes is cheap).
 
 */

/*! The labeled directed edge. `N` is a destination node; `D` is driving info.
 */
case class Edge[N, D](node: N, label: D)

/*! `Graph[C, I]`. `C` (label) is a type of node label and `I` (extra) is a type of edge label.
 */
case class Graph[C, D, E](root: Node[C, D, E], leaves: Nodes[C, D, E]) {
  def get(path: Path): Node[C, D, E] = root.get(path)
  override def toString = root.toString
}

/*! `Node[C,I]` is a very simple and straightforward implementation. 
 */
case class Node[C, D, E](
  label: C, extra: E,
  outs: List[Edge[Node[C, D, E], D]],
  base: Loopback,
  path: Path) {

  lazy val coPath = path.reverse

  @tailrec
  final def get(relPath: Path): Node[C, D, E] = relPath match {
    case Nil => this
    case i :: rp => outs(i).node.get(rp)
  }

  override def toString = "" //GraphPrettyPrinter.toString(this)
}

/*! `CoGraph[C, I]` is dual to `Graph[C, I]`. It has additionally the list of all nodes (vertices).
 */
case class CoGraph[C, D, E](
  root: CoNode[C, D, E],
  leaves: CoNodes[C, D, E],
  nodes: CoNodes[C, D, E])

/*! `CoNode[C,I]` is straightforward. 
 */
// TODO: rename info into extra
case class CoNode[C, D, E](label: C, info: E, in: In[C, D, E], base: Loopback, coPath: CoPath) {
  lazy val path = coPath.reverse

  val ancestors: List[CoNode[C, D, E]] =
    if (in == null) List() else in.node :: in.node.ancestors

  override def toString = label.toString
}

/*! `PartialCoGraph[C, I]` is a central concept of MRSC. It represents a SCP "work in progress".
 */
case class PartialCoGraph[C, D, E](
  completeLeaves: CoNodes[C, D, E],
  incompleteLeaves: CoNodes[C, D, E],
  completeNodes: CoNodes[C, D, E]) {

  val activeLeaf: Option[CoNode[C, D, E]] = incompleteLeaves.headOption
  /*! Partial state is exposed to SCP machines.
   */
  val pState = PState(activeLeaf.getOrElse(null), completeNodes)

  /*! Step is "applied" to the current active leaf.
   */
  def addStep(step: Step[C, D, E]): PartialCoGraph[C, D, E] = incompleteLeaves match {
    case active :: ls =>
      step match {
        case MComplete =>
          PartialCoGraph(active :: completeLeaves, ls, active :: completeNodes)

        case MReplace(l, _) =>
          val node = CoNode(l, active.info, active.in, None, active.coPath)
          PartialCoGraph(completeLeaves, node :: ls, completeNodes)

        case MRollback(dangNode, c, _) =>
          val node = CoNode(c, dangNode.info, dangNode.in, None, dangNode.coPath)
          val completeNodes1 = completeNodes.remove(n => n.path.startsWith(dangNode.path))
          val completeLeaves1 = completeLeaves.remove(n => n.path.startsWith(dangNode.path))
          val incompleteLeaves1 = ls.remove(n => n.path.startsWith(dangNode.path))
          PartialCoGraph(completeLeaves1, node :: incompleteLeaves1, completeNodes1)

        case MForest(subSteps) =>
          val deltaLeaves: CoNodes[C, D, E] = subSteps.zipWithIndex map {
            case (subStep, i) =>
              val edge: In[C, D, E] = Edge(active, subStep.info)
              CoNode(subStep.label, null.asInstanceOf[E], edge, None, i :: active.coPath)
          }
          PartialCoGraph(completeLeaves, deltaLeaves ++ ls, active :: completeNodes)

        case MFold(basePath) =>
          val node = CoNode(active.label, active.info, active.in, Some(basePath), active.coPath)
          PartialCoGraph(node :: completeLeaves, ls, node :: completeNodes)

        case MPrune =>
          throw new Error()
      }
    case _ =>
      throw new Error()
  }
}

/*! Based on the current `PState`, SCP machine should decide what should be done next.
 */
case class PState[C, D, E](val node: CoNode[C, D, E], val completeNodes: CoNodes[C, D, E])

/*! The simple lexicographic order on paths.
 */
object PathOrdering extends Ordering[Path] {
  @tailrec
  final def compare(p1: Path, p2: Path) =
    if (p1.length < p2.length) {
      -1
    } else if (p1.length > p2.length) {
      +1
    } else {
      val result = p1.head compare p2.head
      if (result == 0) {
        compare(p1.tail, p2.tail)
      } else {
        result
      }
    }
}