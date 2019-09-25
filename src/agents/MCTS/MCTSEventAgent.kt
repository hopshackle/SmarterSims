package agents.MCTS

import agents.SimpleActionDoNothing
import ggi.*
import groundWar.*
import utilities.StatsCollator
import java.util.*
import kotlin.math.*

var MCTSPlanMaintenance = true

class MCTSTranspositionTableAgentMaster(val params: MCTSParameters,
                                        val stateFunction: StateSummarizer,
                                        val opponentModel: SimpleActionPlayerInterface? = SimpleActionDoNothing(params.horizon),
                                        val rolloutPolicy: SimpleActionPlayerInterface = SimpleActionDoNothing(params.horizon),
                                        val name: String = "MCTS"
) : SimpleActionPlayerInterface {

    val tree: MutableMap<String, TTNode> = mutableMapOf()
    val stateLinks: MutableMap<String, MutableSet<String>> = mutableMapOf()
    val stateToActionMap: MutableMap<String, List<Action>> = mutableMapOf()
    var currentPlan = emptyList<Action>()

    override fun getAgentType(): String {
        return "MCTSTranspositionTableAgentMaster"
    }

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {

        val opponentTree: MutableMap<String, TTNode> = mutableMapOf()
        val opponentStateToActionMap: MutableMap<String, List<Action>> = mutableMapOf()
        val startTime = System.currentTimeMillis()
        var iteration = 0

        resetTree(gameState, playerRef)
        do {
            val clonedState = gameState.copy() as ActionAbstractGameState
            // TODO: At some point, we may then resample state here for IS-MCTS
            val MCTSChildAgent = getForwardModelInterface() as MCTSTranspositionTableAgentChild

            MCTSChildAgent.setRoot(gameState, playerRef)
            clonedState.registerAgent(playerRef, MCTSChildAgent)
            (0 until clonedState.playerCount()).forEach {
                if (it != playerRef) {
                    if (opponentModel != null) {
                        clonedState.registerAgent(it, opponentModel)
                    } else {
                        val opponentMCTS = MCTSTranspositionTableAgentChild(opponentTree, mutableMapOf(), opponentStateToActionMap,
                                params, LandCombatStateFunction, SimpleActionDoNothing(1000))
                        clonedState.registerAgent(it, opponentMCTS)
                    }
                }
            }

            clonedState.next(params.horizon)

            (0 until clonedState.playerCount()).forEach {
                val reward = clonedState.score(it)
                clonedState.getAgent(it).backPropagate(reward, clonedState.nTicks())
            }
            iteration++
        } while (iteration < params.maxPlayouts && System.currentTimeMillis() < startTime + params.timeLimit)
        StatsCollator.addStatistics("${name}_Time", System.currentTimeMillis() - startTime)
        StatsCollator.addStatistics("${name}_Iterations", iteration)
        val (meanDepth, maxDepth) = getDepth(playerRef, gameState)
        StatsCollator.addStatistics("${name}_MeanDepth", meanDepth)
        StatsCollator.addStatistics("${name}_MaxDepth", maxDepth)
        StatsCollator.addStatistics("${name}_States", tree.size)
        if (opponentModel == null) {
            StatsCollator.addStatistics("${name}_OM_States", opponentTree.size)
        }
        //    println("$iteration iterations executed for player $playerId")
        if (MCTSPlanMaintenance)
            currentPlan = getPlan(gameState, tree, stateFunction, playerRef)
        return getBestAction(playerRef, gameState)
    }

    // returns (meanDepth, maxDepth)
    fun getDepth(playerRef: Int, gameState: ActionAbstractGameState): Pair<Double, Int> {
        var keysToProcess = setOf(playerRef.toString() + "|" + stateFunction(gameState))
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

    fun getBestAction(playerRef: Int, state: ActionAbstractGameState): Action {
        val key = playerRef.toString() + "|" + stateFunction(state)
        val chosenAction = tree[key]?.getBestAction()
        if (chosenAction == null)
            throw AssertionError("Null action")
        return chosenAction
    }

    fun resetTree(root: ActionAbstractGameState, playerRef: Int) {
        val key = playerRef.toString() + "|" + stateFunction(root)
        if (params.pruneTree && tree.containsKey(key)) {
            val keysToKeep = mutableSetOf<String>()
            val unprocessedKeys = TreeSet<String>()
            unprocessedKeys.add(key)
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
        }
    }

    override fun getLastPlan(): List<Action> {
        return currentPlan
    }

    override fun reset(): SimpleActionPlayerInterface {
        tree.clear()
        opponentModel?.reset()
        stateLinks.clear()
        return this
    }

    override fun getForwardModelInterface(): SimpleActionPlayerInterface {
        return MCTSTranspositionTableAgentChild(tree, stateLinks, stateToActionMap, params, stateFunction, rolloutPolicy)
    }

    override fun backPropagate(finalScore: Double, finalTime: Int) {
        // should never need to back-propagate here...that is done in the Child agent
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

fun getPlan(gameState: ActionAbstractGameState, tree: Map<String, TTNode>, stateFunction: StateSummarizer, playerRef: Int): List<Action> {
    // We need to work our way down the tree
    val retValue = mutableListOf<Action>()
    val forwardModel = gameState.copy() as ActionAbstractGameState
    forwardModel.registerAgent(0, SimpleActionDoNothing(1000))
    forwardModel.registerAgent(1, SimpleActionDoNothing(1000))
    var count = 0
    do {
        val action = tree[stateFunction(forwardModel)]?.getBestAction()
        if (action != null) {
            retValue.add(action)
            val nextDecisionTime = action.nextDecisionPoint(playerRef, forwardModel)
            action.apply(forwardModel)
            forwardModel.next(nextDecisionTime.first - forwardModel.nTicks())
            count++
        }
    } while (count < 10 && action != null)
    return retValue.toList()
}


open class MCTSTranspositionTableAgentChild(val tree: MutableMap<String, TTNode>,
                                            val stateLinks: MutableMap<String, MutableSet<String>>,
                                            val stateToActionMap: MutableMap<String, List<Action>>,
                                            val params: MCTSParameters,
                                            val stateFunction: StateSummarizer,
                                            val rolloutPolicy: SimpleActionPlayerInterface)
    : SimpleActionPlayerInterface {

    // node, possibleActions from node, action taken

    data class TrajectoryInstance(val stateRep: String, val time: Int, val score: Double, val allActions: List<Action>, val chosenAction: Action)

    protected val trajectory: Deque<TrajectoryInstance> = ArrayDeque()
    var firstAction = true
    var debug = false
    private var actionCount = 0

    private val nodesPerIteration = 1
    var actionsTaken = 0
        protected set(n) {
            field = n
        }
    // we default this to zero, and set it to the requisite value only when we expand a node
    var nodesToExpand = 0
        protected set(n) {
            field = n
        }

    override fun getAgentType(): String {
        return "MCTSTranspositionTableAgentChild"
    }

    fun setRoot(gameState: ActionAbstractGameState, playerRef: Int) {
        val key = playerRef.toString() + "|" + stateFunction(gameState)
        if (debug) println("Adding root state " + key)
        if (!stateToActionMap.contains(key))
            stateToActionMap[key] = gameState.possibleActions(playerRef, params.maxActions, params.actionFilter)

        if (!tree.contains(key))
            tree[key] = TTNode(params, stateToActionMap[key] ?: emptyList())

        firstAction = false
    }

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        if (firstAction) setRoot(gameState, playerRef)

        actionCount++
        val currentState = playerRef.toString() + "|" + stateFunction(gameState)
        actionsTaken += gameState.codonsPerAction()  // for comparability with RHEA

        val node = tree[currentState]
        if (debug) println(String.format("Action %d, Tick: %d, Current State %s, Node %s, ", actionCount, gameState.nTicks(), currentState, if (node == null) "null" else "exists"))
        val actionChosen = when {
            node == null && actionCount == 1 -> {
                println(this)
                throw AssertionError("Should always find state on first call")
            }
            node == null || actionsTaken > params.maxDepth -> rollout(gameState, playerRef)
            node.hasUnexploredActions() -> {
                nodesToExpand = nodesPerIteration
                expansionPolicy(node, gameState, possibleActions(gameState, currentState, playerRef))
            }
            else -> treePolicy(node, gameState, possibleActions(gameState, currentState, playerRef))
        }

        trajectory.addLast(TrajectoryInstance(currentState, gameState.nTicks(), gameState.score(playerRef),
                possibleActions(gameState, currentState, playerRef), actionChosen))
        return actionChosen
    }

    private fun possibleActions(gameState: ActionAbstractGameState, currentState: String, playerRef: Int): List<Action> {
        // we create X random actions on the same lines as an EvoAgent would
        if (!stateToActionMap.containsKey(currentState)) {
            stateToActionMap[currentState] = gameState.possibleActions(playerRef, params.maxActions, params.actionFilter)
        }

        return stateToActionMap[currentState] ?: emptyList()
    }

    open fun rollout(state: ActionAbstractGameState, playerRef: Int): Action {
        val retValue = rolloutPolicy.getAction(state, playerRef)
        if (debug) println("\tRollout action: " + retValue)
        return retValue
    }

    open fun expansionPolicy(node: TTNode, state: ActionAbstractGameState, possibleActions: List<Action>): Action {
        val retValue = node.getRandomUnexploredAction(possibleActions)
        if (debug) println("\tExpansion action: " + retValue)
        return retValue
    }

    open fun treePolicy(node: TTNode, state: ActionAbstractGameState, possibleActions: List<Action>): Action {
        val retValue = node.getUCTAction(possibleActions)
        if (debug) println("\tTree action: " + retValue)
        return retValue
    }

    override fun getLastPlan(): List<Action> {
        TODO("Not implemented")
    }

    override fun reset(): SimpleActionPlayerInterface {
        tree.clear()
        trajectory.clear()
        stateToActionMap.clear()
        stateLinks.clear()
        nodesToExpand = 0
        actionsTaken = 0
        return MCTSTranspositionTableAgentChild(tree, mutableMapOf(), mutableMapOf(), params, stateFunction, rolloutPolicy)
    }

    override fun getForwardModelInterface(): SimpleActionPlayerInterface {
        return this
    }

    override fun backPropagate(finalScore: Double, finalTime: Int) {
        // Here we go forwards through the trajectory
        // we decrement nodesExpanded as we need to expand a node
        // We can discount if needed
        // this is the incremental reward...
        // TODO: MAX option will require us to go backwards through the trajectory to calculate update value
        val startTime = if (trajectory.isNotEmpty()) trajectory.first.time else System.currentTimeMillis().toInt()
        trajectory.add(TrajectoryInstance("GAME_OVER", finalTime, finalScore, listOf(), NoAction(0, 1)))
        val incrementalRewardsAtTime = trajectory.map { Pair(it.score, it.time) }.zipWithNext()
                .map { (a, b) -> Pair(b.first - a.first, b.second - startTime) }
        val discountedReward = incrementalRewardsAtTime.map { it.first * params.discountRate.pow(it.second) }.sum()
        var previousState = ""
        trajectory.forEach { (state, _, _, possibleActions, action) ->
            val node =  tree[state]
            when {
                node == null && nodesToExpand > 0 -> {
                    nodesToExpand--
                    tree[state] = TTNode(params, possibleActions)
                    // Add new node (with no visits as yet; that will be sorted during back-propagation)
                }
                node == null -> Unit // do nothing
                else -> {
                    node.update(action, possibleActions, discountedReward)
                }
            }
            if (tree.contains(previousState)) {
                val nextStates = stateLinks.getOrPut(previousState, { mutableSetOf() })
                nextStates.add(state)
            }
            previousState = state
        }
        nodesToExpand = 0
    }
}