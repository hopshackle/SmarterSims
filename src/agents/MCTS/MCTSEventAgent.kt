package agents.MCTS

import ggi.*
import utilities.StatsCollator
import java.util.*

class MCTSTranspositionTableAgentMaster(val params: MCTSParameters,
                                        val stateFunction: StateSummarizer,
                                        val opponentModel: SimpleActionPlayerInterface = SimpleActionDoNothing,
                                        val rolloutPolicy: SimpleActionPlayerInterface = SimpleActionDoNothing,
                                        val name: String = "MCTS"
) : SimpleActionPlayerInterface {

    val tree: MutableMap<String, TTNode> = mutableMapOf()
    val stateLinks: MutableMap<String, MutableSet<String>> = mutableMapOf()
    val stateToActionMap: MutableMap<String, List<Action>> = mutableMapOf()

    override fun getAgentType(): String {
        return "MCTSTranspositionTableAgentMaster"
    }

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {

        val startTime = System.currentTimeMillis()
        var iteration = 0

        resetTree(gameState, playerRef)
        do {
            val clonedState = gameState.copy() as ActionAbstractGameState
            // TODO: At some point, we may then resample state here for IS-MCTS
            clonedState.registerAgent(playerRef, getForwardModelInterface())
            (0 until clonedState.playerCount()).forEach {
                if (it != playerRef)
                    clonedState.registerAgent(it, opponentModel)
                // TODO: When we have more interesting opponent models (e.g. MCTS agents), we need to instantiate/initialise them
            }

            clonedState.next(params.horizon)

            (0 until clonedState.playerCount()).forEach {
                val reward = clonedState.score(it)
                clonedState.getAgent(it).backPropagate(reward)
            }
            iteration++
        } while (iteration < params.maxPlayouts && System.currentTimeMillis() < startTime + params.timeLimit)
        StatsCollator.addStatistics("${name}_Time", System.currentTimeMillis() - startTime)
        StatsCollator.addStatistics("${name}_Iterations", iteration)
        val (meanDepth, maxDepth) = getDepth(gameState)
        StatsCollator.addStatistics("${name}_MeanDepth", meanDepth)
        StatsCollator.addStatistics("${name}_MaxDepth", maxDepth)
        StatsCollator.addStatistics("${name}_States", tree.size)
        //    println("$iteration iterations executed for player $playerId")
        return getBestAction(gameState)
    }

    // returns (meanDepth, maxDepth)
    fun getDepth(gameState: ActionAbstractGameState): Pair<Double, Int> {
        var keysToProcess = setOf(stateFunction(gameState))
        val processedKeys = mutableSetOf<String>()
        var depths = IntArray(50) { 0 }
        var currentDepth = 0
        do {
            depths[currentDepth] = keysToProcess.size
            keysToProcess = keysToProcess.flatMap {
                stateLinks[it]?.toList() ?: emptyList()
            }.filterNot(processedKeys::contains).toSet()
            currentDepth++
            processedKeys.addAll(keysToProcess)
        } while (currentDepth < 50 && keysToProcess.isNotEmpty())
        val meanDepth = depths.mapIndexed { i, d -> i * d }.sum().toDouble() / depths.sum()
        return Pair(meanDepth, currentDepth - 1)
    }

    fun getBestAction(state: ActionAbstractGameState): Action {
        val key = stateFunction(state)
        val actionMap: Map<Action, MCStatistics> = tree[key]?.actionMap ?: mapOf()
        val chosenAction = actionMap.maxBy {
            when (params.selectionMethod) {
                MCTSSelectionMethod.SIMPLE -> it.value.mean
                MCTSSelectionMethod.ROBUST -> it.value.visitCount.toDouble()
            }
        }?.key
        return chosenAction ?: NoAction(0)
    }

    fun resetTree(root: ActionAbstractGameState, playerRef: Int) {
        if (params.pruneTree && tree.containsKey(stateFunction(root))) {
            val keysToKeep = mutableSetOf<String>()
            val unprocessedKeys = TreeSet<String>()
            unprocessedKeys.add(stateFunction(root))
            do {
                val nextKey = unprocessedKeys.first()
                keysToKeep.add(nextKey)
                stateLinks[nextKey]?.let {
                    it.removeAll(keysToKeep)
                    unprocessedKeys.addAll(it)
                }
                unprocessedKeys.remove(nextKey)
            } while (unprocessedKeys.isNotEmpty())

            val keysToRemove = tree.keys - keysToKeep
            keysToRemove.forEach {
                tree.remove(it)
                stateLinks.remove(it)
            }
            stateToActionMap.clear()
            // we can rebuild from the tree nodes
            stateToActionMap.putAll(tree.keys.associateWith { tree[it]!!.actions })
        } else {
            tree.clear()
            stateLinks.clear()
            stateToActionMap.clear()
            val key = stateFunction(root)
            stateToActionMap[key] = root.possibleActions(playerRef, params.maxActions)
            tree[key] = TTNode(params, stateToActionMap[key] ?: emptyList())
        }
    }

    override fun getPlan(gameState: ActionAbstractGameState, playerRef: Int): List<Action> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun reset(): SimpleActionPlayerInterface {
        tree.clear()
        opponentModel.reset()
        stateLinks.clear()
        return this
    }

    override fun getForwardModelInterface(): SimpleActionPlayerInterface {
        return MCTSTranspositionTableAgentChild(tree, stateLinks, stateToActionMap, params, stateFunction, rolloutPolicy)
    }

    override fun backPropagate(finalScore: Double) {
        // should never need to back-propagate here...that is done in the Child agent
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}


open class MCTSTranspositionTableAgentChild(val tree: MutableMap<String, TTNode>,
                                            val stateLinks: MutableMap<String, MutableSet<String>>,
                                            val stateToActionMap: MutableMap<String, List<Action>>,
                                            val params: MCTSParameters,
                                            val stateFunction: StateSummarizer,
                                            val rolloutPolicy: SimpleActionPlayerInterface)
    : SimpleActionPlayerInterface {

    // node, possibleActions from node, action taken

    protected val trajectory: Deque<Triple<String, List<Action>, Action>> = ArrayDeque()

    private val nodesPerIteration = 1
    var actionsTaken = 0
        protected set(n) {
            field = n
        }
    var nodesToExpand = nodesPerIteration
        protected set(n) {
            field = n
        }

    override fun getAgentType(): String {
        return "MCTSTranspositionTableAgentChild"
    }

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        val currentState = stateFunction(gameState)
        actionsTaken += gameState.codonsPerAction()  // for comparability with RHEA

        val node = tree[currentState]
        val actionChosen = when {
            node == null || actionsTaken > params.maxDepth -> rollout(gameState, playerRef)
            node.hasUnexploredActions() -> expansionPolicy(node, gameState, possibleActions(gameState, currentState, playerRef))
            else -> treePolicy(node, gameState, possibleActions(gameState, currentState, playerRef))
        }
        trajectory.addLast(Triple(currentState, possibleActions(gameState, currentState, playerRef), actionChosen))
        return actionChosen
    }

    private fun possibleActions(gameState: ActionAbstractGameState, currentState: String, playerRef: Int): List<Action> {
        // we create X random actions on the same lines as an EvoAgent would
        if (!stateToActionMap.containsKey(currentState)) {
            stateToActionMap[currentState] = gameState.possibleActions(playerRef, params.maxActions)
        }

        return stateToActionMap[currentState] ?: emptyList()
    }

    open fun rollout(state: ActionAbstractGameState, playerRef: Int): Action {
        return rolloutPolicy.getAction(state, playerRef)
    }

    open fun expansionPolicy(node: TTNode, state: ActionAbstractGameState, possibleActions: List<Action>): Action {
        return node.getRandomUnexploredAction(possibleActions)
    }

    open fun treePolicy(node: TTNode, state: ActionAbstractGameState, possibleActions: List<Action>): Action {
        return node.getUCTAction(possibleActions)
    }

    override fun getPlan(gameState: ActionAbstractGameState, playerRef: Int): List<Action> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun reset(): SimpleActionPlayerInterface {
        tree.clear()
        trajectory.clear()
        stateToActionMap.clear()
        stateLinks.clear()
        nodesToExpand = nodesPerIteration
        actionsTaken = 0
        return MCTSTranspositionTableAgentChild(tree, mutableMapOf(), mutableMapOf(), params, stateFunction, rolloutPolicy)
    }

    override fun getForwardModelInterface(): SimpleActionPlayerInterface {
        return this
    }

    override fun backPropagate(finalScore: Double) {
        // Here we go forwards through the trajectory
        // we decrement nodesExpanded as we need to expand a node
        // We can discount if needed
        var totalDiscount = Math.pow(params.discountRate, trajectory.size.toDouble())
        var previousState = ""
        trajectory.forEach { (state, possibleActions, action) ->
            totalDiscount /= params.discountRate
            val node = tree[state]
            when {
                node == null && nodesToExpand > 0 -> {
                    nodesToExpand--
                    tree[state] = TTNode(params, possibleActions)
                    // Add new node (with no visits as yet; that will be sorted during back-propagation)
                }
                node == null -> Unit // do nothing
                else -> node.update(action, possibleActions, finalScore * totalDiscount)
            }
            if (tree.contains(previousState)) {
                val nextStates = stateLinks.getOrPut(previousState, { mutableSetOf() })
                nextStates.add(state)
            }
            previousState = state
        }
        nodesToExpand = nodesPerIteration
    }
}