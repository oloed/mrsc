package mrsc.counters

import mrsc.core._

object Demo extends App {

  def graphSize(g: TGraph[_, _]): Int =
    size(g.root)

  def graphSize(g: SGraph[_, _]): Int =
    g.completeNodes.size

  def size(n: TNode[_, _]): Int = 1 + n.outs.map(out => size(out.node)).sum

  def scProtocol(protocol: Protocol, l: Int): Unit = {

    val rules = SRCountersRules(protocol, l)
    val graphs = GraphGenerator(rules, protocol.start)

    for (graph <- graphs if graph.isComplete) {
      val tgraph = Transformations.transpose(graph)
      val isSafe = checkSubTree(protocol.unsafe)(tgraph.root)
      println("* Graph generated by single-result supercompiler *")
      println("* The size of graph: " + graphSize(tgraph) + " *")
      println("* The graph is evidence of proof: " + isSafe + " *")
      println(tgraph)
      println()
    }
  }

  def multiScProtocol(protocol: Protocol, l: Int): Unit = {
    val rules = MRCountersRules(protocol, l)
    val graphs = GraphGenerator(rules, protocol.start)

    var minGraph: SGraph[Conf, Int] = null
    var size = Int.MaxValue
    for (graph <- graphs) {
      if (graph.isComplete) {
        if (graphSize(graph) < size) {
          minGraph = graph
          size = graphSize(graph)
          rules.maxSize = size
        }
      }
    }
    
    if (minGraph != null) {
      val tgraph = Transformations.transpose(minGraph)
      println("* The minimal graph found by multi-result supercompiler *")
      println("* The size of graph: " + graphSize(tgraph) + " *")
      println("* The graph is evidence of proof: true *")
      println(tgraph)
    }
    
    println((graphs.consumed, graphs.pruned))
  }

  def checkSubTree(unsafe: Conf => Boolean)(node: TNode[Conf, _]): Boolean =
    !unsafe(node.conf) && node.outs.map(_.node).forall(checkSubTree(unsafe))

  def demo(protocol: Protocol): Unit = {
    println()
    println(protocol)
    println("================================")
    scProtocol(protocol, 2)
    multiScProtocol(protocol, 2)
  }

  demo(Synapse)
  demo(MSI)
  demo(MOSI)
  demo(MESI)
  demo(MOESI)
  demo(Illinois)
  demo(Berkley)
  demo(Firefly)
  // many variants here - be patient
  demo(Futurebus)
  demo(Xerox)
  // many variants here - be patient
  demo(Java)
  demo(ReaderWriter)
  demo(DataRace) 
}
