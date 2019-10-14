package groundWar

import kotlin.math.ln


fun compositeScoreFunction(functions: List<(LandCombatGame, Int) -> Double>): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        functions.map { f -> f(game, player) }.sum()
    }
}

val interimScoreFunction = simpleScoreFunction(5.0, 1.0, -5.0, -0.5)
val finalScoreFunction = simpleScoreFunction(5.0, 1.0, -5.0, -1.0)

val hasMaterialAdvantage: (LandCombatGame, Int) -> Boolean = { game: LandCombatGame, player: Int ->
    finalScoreFunction(game, player) > finalScoreFunction(game, 1 - player)
}

fun allFortsConquered(player: PlayerId): (LandCombatGame) -> Boolean = { game: LandCombatGame ->
    game.world.cities.filter(City::fort).all { it.owner == player }
}

fun allTargetsConquered(player: PlayerId, targets: Map<String, Double>): (LandCombatGame) -> Boolean = { game: LandCombatGame ->
    game.world.cities.filter { it.name in targets }.all { it.owner == player }
}

fun stringToScoreFunction(stringRep: String?, targetMap: Map<String, Double> = emptyMap()): (LandCombatGame, Int) -> Double {
    val scoreString = stringRep ?: "SC|5|-5|1|-1"

    var sp = scoreString.split("|").filterNot { it.startsWith("SC") }.map { it.toDouble() }
    sp = sp + (sp.size until 9).map { 0.00 }
    var scoreComponents = mutableListOf(simpleScoreFunction(sp[0], sp[2], sp[1], sp[3]))
    if (sp.subList(4, 6).any { it != 0.00 })
        scoreComponents.add(visibilityScore(sp[4], sp[5]))

    if (sp.subList(6, 7).any { it != 0.00 })
        scoreComponents.add(fortressScore(sp[6]))

    if (sp.subList(7, 8).any { it != 0.00 })
        scoreComponents.add(entropyScoreFunction(sp[7]))

    if (sp.subList(8, 9).any { it != 0.00 })
        scoreComponents.add(localAdvantageScoreFunction(sp[8]))

    if (targetMap.isNotEmpty())
        scoreComponents.add(specificTargetScoreFunction(targetMap))

    return compositeScoreFunction(scoreComponents)
}


fun simpleScoreFunction(ourCityValue: Double, ourForceValue: Double, theirCityValue: Double, theirForceValue: Double): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        val playerId = numberToPlayerID(player)
        with(game.world) {
            val ourCities = cities.count { c -> c.owner == playerId }
            val theirCities = cities.count { c -> c.owner == if (playerId == PlayerId.Blue) PlayerId.Red else PlayerId.Blue }
            // then add the total of all forces
            val ourForces = cities.filter { c -> c.owner == playerId }.sumByDouble { it.pop.size } +
                    currentTransits.filter { t -> t.playerId == playerId }.sumByDouble { it.force.effectiveSize }
            val enemyForces = cities.filter { c -> c.owner != playerId }.sumByDouble { it.pop.size } +
                    currentTransits.filter { t -> t.playerId != playerId }.sumByDouble { it.force.effectiveSize }
            ourCityValue * ourCities + ourForceValue * ourForces + theirCityValue * theirCities + theirForceValue * enemyForces
        }
    }
}

fun fortressScore(fortressValue: Double): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        val playerId = numberToPlayerID(player)
        game.world.cities.count { c -> c.fort && c.owner == playerId } * fortressValue
    }
}

fun specificTargetScoreFunction(targetValues: Map<String, Double>): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        val playerColour = numberToPlayerID(player)
        with(game.world) {
            targetValues
                    .filter { (name, _) -> cities.find { it.name == name }?.owner == playerColour }
                    .map { (_, value) -> value }
                    .sum()
        }
    }
}

fun visibilityScore(nodeValue: Double = 5.0, arcValue: Double = 2.0): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        val playerColour = numberToPlayerID(player)
        with(game.world) {
            val citiesVisible = (cities.indices).count { checkVisible(it, playerColour) }
            val arcsVisible = routes.count { checkVisible(it, playerColour) }
            citiesVisible * nodeValue + arcsVisible / 2.0 * arcValue
        }
    }
}

fun entropyScoreFunction(coefficient: Double): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        val playerColour = numberToPlayerID(player)
        with(game.world) {
            val distinctForces = (cities.filter { it.owner == playerColour }.map { it.pop.size } +
                    currentTransits.filter { it.playerId == playerColour }.map { it.force.size })
                    .filter { it > 0.05 }
            val total = distinctForces.sum()
            val distribution = distinctForces.map { it / total }
            coefficient * distribution.map { -it * ln(it) }.sum()
        }
    }
}

fun localAdvantageScoreFunction(coefficient: Double): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        val playerColour = numberToPlayerID(player)
        with(game.world) {
            cities.withIndex()
                    .filter { it.value.owner == playerColour }
                    .flatMap { allRoutesFromCity[it.index]?.map { r -> Pair(r.fromCity, r.toCity) } ?: emptyList() }
                    .filterNot { (_, toCity) -> cities[toCity].owner == playerColour }
                    .map { (fromCity, toCity) -> Pair(cities[fromCity].pop.size, cities[toCity].pop.size) }
                    .map { it.first - it.second }.sum() * coefficient
        }
    }
}