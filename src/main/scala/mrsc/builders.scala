package mrsc

import scala.annotation.tailrec

/*!# SC cographs builders
 
 SC cographs builders implement the logic of constructing SC cographs step-by-step by invoking
 provided abstract machines. They build cographs - it is up to `CoGraphConsumer` how to use this 
 cograph further.
 */

/*! `MultiCoGraphBuilder` considers all steps returned by `MultiResultMachine` and proceeds with
  all possible variants.
 */
class CoGraphBuilder[C, D, E](machine: Machine[C, D, E], consumer: CoGraphConsumer[C, D, E]) {

  /*! It maintains a list of partial cographs ...
   */
  var partialCoGraphs: List[PartialCoGraph[C, D, E]] = null

  /*! ... starts with a one-element list of partial cographs ... 
   */
  def buildCoGraph(conf: C, info: E): Unit = {
    val startNode = CoNode[C, D, E](conf, info, null, None, Nil)
    partialCoGraphs = List(new PartialCoGraph(List(), List(startNode), Nil))
    loop()
  }

  /*! ... and loops
   */
  @tailrec
  private def loop(): Unit =
    partialCoGraphs match {
      /*! If `partialCoGraphs` is empty, then it just stops, */
      case Nil =>
      /*! otherwise it investigates the status of the first cograph: */
      case g :: gs =>
        g.activeLeaf match {
          /*! If the first cograph is completed, builder transforms it to the pure cograph
           and sends it to the consumer. 
          */
          case None =>
            // TODO: do we need to sort it??
            val orderedNodes = g.completeNodes.sortBy(_.coPath)(PathOrdering)
            val rootNode = orderedNodes.head
            val completed = CoGraph(rootNode, g.completeLeaves, orderedNodes)
            partialCoGraphs = gs
            consumer.consume(Some(completed))

          /*! If the first cograph is incomplete, then builder considers all variants suggested
           by `machine`:
           */
          case Some(leaf) =>
            partialCoGraphs = gs
            for (step <- machine.steps(g.pState)) step match {
              /*! informing `consumer` about pruning, if any */
              case Prune =>
                consumer.consume(None)
              /*! or adding new cograph to the pending list otherwise */
              case s =>
                partialCoGraphs = g.addStep(s) :: partialCoGraphs
            }
        }
        /*! and looping again. */
        loop()
    }

}