package groundWar

import ggi.*
import groundWar.fatigue.FatigueModel
import groundWar.fatigue.LinearFatigue
import groundWar.fogOfWar.HistoricVisibility
import org.apache.commons.math3.ode.events.FilterType
import test.noVisibility
import kotlin.math.*
import kotlin.collections.*
import kotlin.random.Random
import ggi.SimpleActionPlayerInterface as SimpleActionPlayerInterface

internal val noVisibility = HistoricVisibility(emptyMap())

data class Event(val tick: Int, val action: Action) : Comparable<Event> {

    val priority = when (action) {
        is MakeDecision -> 0
        is TransitEnd -> 2
        else -> 10
    }

    override operator fun compareTo(other: Event): Int {
        val tickComparison = tick.compareTo(other.tick)
        if (tickComparison == 0) return priority.compareTo(other.priority)
        return tickComparison
    }
}

val defaultScoreFunctions = mutableMapOf(
        PlayerId.Blue to simpleScoreFunction(1.0, 0.0, -1.0, 0.0),
        PlayerId.Red to simpleScoreFunction(1.0, 0.0, -1.0, 0.0)
)
val defaultVictoryFunctions = mutableMapOf(
        PlayerId.Blue to { game: LandCombatGame -> game.world.cities.all { it.owner == PlayerId.Blue } },
        PlayerId.Red to { game: LandCombatGame -> game.world.cities.all { it.owner == PlayerId.Red } }
)

class LandCombatGame(val world: World = World()) : ActionAbstractGameState {

    val LCG_rnd = Random(world.params.seed)
    // the first digits are the city...so we need at least the number of cities
    // then the second is the destination...so we need to maximum degree of a node
    // then the next two are proprtion of force and wait time
    val cityGenes = log10(world.cities.size.toDouble() - 1.0).toInt() + 1
    val routeGenes = log10((world.allRoutesFromCity.map { (_, v) -> v.size }.max()
            ?: 2).toDouble() - 1.0).toInt() + 1

    val eventQueue = EventQueue()
    val fatigueModels = world.params.fatigueRate.map { LinearFatigue(it) }
    val visibilityModels = mutableMapOf(PlayerId.Blue to HistoricVisibility(emptyMap()), PlayerId.Red to HistoricVisibility(emptyMap()))

    override fun registerAgent(player: Int, agent: SimpleActionPlayerInterface) = eventQueue.registerAgent(player, agent, nTicks())
    override fun getAgent(player: Int) = eventQueue.getAgent(player)
    override fun planEvent(time: Int, action: Action) {
        eventQueue.add(Event(time, action))
    }

    var scoreFunction: MutableMap<PlayerId, (LandCombatGame, Int) -> Double> = defaultScoreFunctions
    var victoryFunction: MutableMap<PlayerId, (LandCombatGame) -> Boolean> = defaultVictoryFunctions

    override fun copy(perspective: Int): LandCombatGame {
        val newWorld = if (world.params.fogOfWar) {
            val playerId = numberToPlayerID(perspective)
            world.deepCopyWithFog(playerId, visibilityModels[playerId] ?: noVisibility)
        } else world.deepCopy()
        val retValue = copyHelper(newWorld)
        // We also need to strip out any events in the queue that are not visible to the perspective player!
        retValue.eventQueue.addAll(eventQueue) { e -> e.action.visibleTo(perspective, retValue) }
        retValue.visibilityModels[numberToPlayerID(1 - perspective)] = noVisibility
        addMakeDecisions(perspective, retValue)
        return retValue
    }

    override fun copy(): LandCombatGame {
        val retValue = copyHelper(world.deepCopy())
        retValue.eventQueue.addAll(eventQueue) { true }
        addMakeDecisions(-1, retValue)
        return retValue
    }

    private fun copyHelper(world: World): LandCombatGame {
        val state = LandCombatGame(world)
        visibilityModels.forEach {
            state.visibilityModels[it.key] = it.value
        }
        state.scoreFunction = scoreFunction
        state.victoryFunction = victoryFunction
        state.eventQueue.currentTime = nTicks()
        return state
    }

    private fun addMakeDecisions(perspective: Int, state: LandCombatGame) {
        if (perspective != -1)
            state.registerAgent(perspective, getAgent(perspective).getForwardModelInterface())
        (0 until playerCount()).forEach { p ->
            if (p != perspective)
                state.registerAgent(p, getAgent(p).getForwardModelInterface())
        }
    }

    override fun playerCount() = 2

    override fun codonsPerAction() = cityGenes + routeGenes + 1

    override fun nActions() = 10

    override fun possibleActions(player: Int, max: Int, filterType: String): List<Action> {
        return when (filterType) {
            "core" -> {
                val playerId = numberToPlayerID(player)
                world.cities.withIndex()
                        .filter { it.value.owner == playerId && it.value.pop.size > 0.01 }
                        .flatMap { (startIndex, _) -> world.allRoutesFromCity[startIndex]!!.map { Pair(it.fromCity, it.toCity) } }
                        .flatMap { (fromCity, toCity) ->
                            val proportionLimit = (world.allRoutesFromCity[fromCity]?.find{it.toCity == toCity}?.limit ?: 0.0) / world.cities[fromCity].pop.size
                            val loopLimit = if (proportionLimit > 0.00) max(1, (proportionLimit * 3).toInt()) else 3
                            (1..loopLimit)
                                    .map {
                                        LaunchExpedition(playerId, fromCity, toCity, it / 3.0 , world.params.OODALoop[player])
                                    }
                        }.filter{it.isValid(this)} +
                        listOf(InterruptibleWait(player, world.params.OODALoop[player]),
                                InterruptibleWait(player, 3 * world.params.OODALoop[player]),
                                InterruptibleWait(player, 10 * world.params.OODALoop[player]))
            }
            else -> (0 until max * 10).asSequence().map {
                translateGene(player, IntArray(codonsPerAction()) { LCG_rnd.nextInt(nActions()) })
            }.distinct().take(max).toList()
        }
    }

    override fun translateGene(player: Int, gene: IntArray): Action {
        // if the gene does not encode a valid LaunchExpedition, then we interpret it as a Wait action
        // if we take a real action, then we must wait for a minimum period before the next one
        val playerId = numberToPlayerID(player)
        if (world.cities.filter { it.owner == playerId }.sumByDouble { it.pop.size } +
                world.currentTransits.filter { it.playerId == playerId }.sumByDouble { it.force.effectiveSize } < 0.001)
            return NoAction(player, 1000)   // no units left
        val rawFromGene = gene.take(cityGenes).reversed().mapIndexed { i, v -> 10.0.pow(i.toDouble()) * v }.sum().toInt()
        val rawToGene = gene.takeLast(gene.size - cityGenes).take(routeGenes).reversed().mapIndexed { i, v -> 10.0.pow(i.toDouble()) * v }.sum().toInt()
        val proportion = 0.1 * (gene[cityGenes + routeGenes] + 1)

        val origin = rawFromGene % world.cities.size
        val destination = destinationCity(origin, rawToGene)
        val encodedWait = (gene[cityGenes + routeGenes] + 1).pow(2)  // normally this is the proportion gene
        val actualWait = world.params.OODALoop[player]

        val proposedAction = LaunchExpedition(playerId, origin, destination, proportion, actualWait)
        if (!proposedAction.isValid(this))
            return InterruptibleWait(player, max(encodedWait, 10))
        return proposedAction
    }

    private fun destinationCity(from: Int, toCode: Int): Int {
        val routes = world.allRoutesFromCity[from] ?: emptyList()
        if (routes.isEmpty())
            throw AssertionError("Should not be empty")
        return routes[toCode % routes.size].toCity
    }

    override fun next(forwardTicks: Int): LandCombatGame {
        eventQueue.next(forwardTicks, this)
        return this
    }

    override fun score(player: Int): Double {
        if (isTerminal())
            return if (victoryFunction[numberToPlayerID(player)]?.invoke(this) == true) 10000.0 else -10000.0
        return scoreFunction[numberToPlayerID(player)]?.invoke(this, player) ?: 0.0
    }

    override fun isTerminal(): Boolean {
        // game is over if all cities are controlled by the same player, whoever that is
        return (nTicks() > 1000 ||
                victoryFunction[PlayerId.Blue]?.invoke(this) ?: false ||
                victoryFunction[PlayerId.Red]?.invoke(this) ?: false)
    }

    override fun nTicks() = eventQueue.currentTime

    fun updateVisibility(node: Int, playerId: PlayerId) {
        if (world.cities[node].owner != playerId) {
            val vModel = visibilityModels[playerId]
            if (vModel != null)
                visibilityModels[playerId] = vModel.updateNode(node, nTicks(), world.cities[node].pop.size)
            else
                throw AssertionError("null visibility model")
        }
    }

    fun updateVisibilityOfNeighbours(node: Int, playerId: PlayerId) {
        (world.allRoutesFromCity[node]!!.map(Route::toCity).toList() + node).forEach {
            updateVisibility(it, playerId)
        }
    }

    override fun sanityChecks() {
        // check to see if we have any transits that should have finished
        val transitsThatShouldHaveEnded = world.currentTransits.filter { it.endTime < nTicks() }
        if (transitsThatShouldHaveEnded.isNotEmpty()) {
            println(transitsThatShouldHaveEnded.joinToString("\n"))
            throw AssertionError("Extant transits that should have been terminated")
        }
        if (world.currentTransits.any { it.force.size == 0.00 }) {
            throw AssertionError("Extant transits with zero force")
        }
        cleanUpVisibility()
    }

    private fun cleanUpVisibility() {
        // we remove any data on cities we now control. Their visibility is not a sufficient condition
        // as that does not mean we have information on all routes into the city
        listOf(PlayerId.Blue, PlayerId.Red).forEach {
            visibilityModels[it] = HistoricVisibility(visibilityModels[it]!!.filterNot { (city, data) ->
                world.cities[city].owner == it ||
                        data.first < nTicks() - world.params.fogMemory[playerIDToNumber(it)]
            })
        }
    }
}

private fun Int.pow(i: Int): Int {
    return this.toDouble().pow(i.toDouble()).toInt()
}
