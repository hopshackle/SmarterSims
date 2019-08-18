package groundWar

import ggi.*
import test.params
import kotlin.math.*
import kotlin.collections.*
import kotlin.random.Random
import ggi.SimpleActionPlayerInterface as SimpleActionPlayerInterface

data class Event(val tick: Int, val action: Action) : Comparable<Event> {

    val priority = if (action is MakeDecision) 0 else 1

    operator override fun compareTo(other: Event): Int {
        val tickComparison = tick.compareTo(other.tick)
        if (tickComparison == 0) return priority.compareTo(other.priority)
        return tickComparison
    }
}

class LandCombatGame(val world: World = World(), val targets: Map<PlayerId, List<Int>> = emptyMap(), val codons: Int = 4) : ActionAbstractGameState {

    val LCG_rnd = Random(params.seed)
    // the first digits are the city...so we need at least the number of cities
    // then the second is the destination...so we need to maximum degree of a node
    // then the next two are proprtion of force and wait time
    val cityGenes = Math.log10(world.cities.size.toDouble() - 1.0).toInt() + 1
    val routeGenes = Math.log10((world.allRoutesFromCity.map { (_, v) -> v.size }.max()
            ?: 2).toDouble() - 1.0).toInt() + 1

    val eventQueue = EventQueue()

    override fun registerAgent(player: Int, agent: SimpleActionPlayerInterface) = eventQueue.registerAgent(player, agent, nTicks())
    override fun getAgent(player: Int) = eventQueue.getAgent(player)
    override fun planEvent(time: Int, action: Action) {
        eventQueue.add(Event(time, action))
    }

    var scoreFunction: MutableMap<PlayerId, (LandCombatGame, Int) -> Double> = mutableMapOf(
            PlayerId.Blue to simpleScoreFunction(1.0, 0.0, -1.0, 0.0),
            PlayerId.Red to simpleScoreFunction(1.0, 0.0, -1.0, 0.0)
    )

    override fun copy(perspective: Int): LandCombatGame {
        val newWorld = if (world.params.fogOfWar) world.deepCopyWithFog(numberToPlayerID(perspective)) else world.deepCopy()
        val retValue = copyHelper(newWorld)
        // We also need to strip out any events in the queue that are not visible to the perspective player!
        retValue.eventQueue.addAll(eventQueue) { e -> e.action.visibleTo(perspective, this) }
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
        val state = LandCombatGame(world, targets, codons)
        state.scoreFunction = scoreFunction
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

    override fun codonsPerAction() = cityGenes + routeGenes + codons - 2

    override fun nActions() = 10

    override fun possibleActions(player: Int, max: Int): List<Action> {
        return (0 until max * 10).asSequence().map {
            translateGene(player, IntArray(codonsPerAction()) { LCG_rnd.nextInt(nActions()) })
        }.distinct().take(max).toList()
    }

    override fun translateGene(player: Int, gene: IntArray): Action {
        // if the gene does not encode a valid LaunchExpedition, then we interpret it as a Wait action
        // if we take a real action, then we must wait for a minimum period before the next one
        val playerId = numberToPlayerID(player)
        if (world.cities.filter { it.owner == playerId }.sumByDouble(City::pop) +
                world.currentTransits.filter { it.playerId == playerId }.sumByDouble(Transit::nPeople) < 0.001)
            return NoAction(player, 1000)   // no units left
        val rawFromGene = gene.take(cityGenes).reversed().mapIndexed { i, v -> 10.0.pow(i.toDouble()) * v }.sum().toInt()
        val rawToGene = gene.takeLast(gene.size - cityGenes).take(routeGenes).reversed().mapIndexed { i, v -> 10.0.pow(i.toDouble()) * v }.sum().toInt()
        val proportion = 0.1 * (gene[cityGenes + routeGenes] + 1)

        val origin = rawFromGene % world.cities.size
        val destination = destinationCity(origin, rawToGene)
        val encodedWait = when (codons) {
            4 -> (gene[cityGenes + routeGenes + 1] + 1).pow(2)
            3 -> (gene[cityGenes + routeGenes] + 1).pow(2)  // normally this is the proportion gene
            else -> throw AssertionError("Only 3 or 4 base encoding supported")
        }
        val actualWait = when (codons) {
            4 -> max(encodedWait, world.params.OODALoop[player])
            3 -> world.params.OODALoop[player]
            else -> throw AssertionError("Only 3 or 4 base encoding supported")
        }

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

    override fun score(player: Int) = scoreFunction[numberToPlayerID(player)]?.invoke(this, player) ?: 0.0

    override fun isTerminal(): Boolean {
        // game is over if all cities are controlled by the same player, whoever that is
        val player0 = world.cities[0].owner
        return (nTicks() > 1000 || world.cities.all { c -> c.owner == player0 })
    }

    override fun nTicks() = eventQueue.currentTime
}

private fun Int.pow(i: Int): Int {
    return this.toDouble().pow(i.toDouble()).toInt()
}
