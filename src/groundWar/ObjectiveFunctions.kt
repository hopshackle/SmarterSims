package groundWar


fun compositeScoreFunction(vararg functions: (LandCombatGame, Int) -> Double): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        functions.map { f -> f(game, player) }.sum()
    }
}

val interimScoreFunction = simpleScoreFunction(5.0, 1.0, -5.0, -0.5)
val finalScoreFunction = simpleScoreFunction(5.0, 1.0, -5.0, -1.0)

val hasMaterialAdvantage: (LandCombatGame, Int) -> Boolean = { game: LandCombatGame, player: Int ->
    finalScoreFunction(game, player) > finalScoreFunction(game, 1 - player)
}

fun stringToScoreFunction(stringRep: String?): (LandCombatGame, Int) -> Double {
    if (stringRep == null) {
        return finalScoreFunction
    }
    var sp = stringRep.split("|").filterNot { it.startsWith("SC") }.map { it.toDouble() }
    sp = sp + (sp.size until 7).map { 0.00 }
    var retValue = simpleScoreFunction(sp[0], sp[2], sp[1], sp[3])
    if (sp.subList(4, 6).any { it != 0.00 }) {
        retValue = compositeScoreFunction(
                retValue,
                visibilityScore(sp[4], sp[5])
        )
    }
    if (sp.subList(6, 7).any { it != 0.00 }) {
        retValue = compositeScoreFunction(
                retValue,
                fortressScore(sp[6])
        )
    }
    return retValue
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

fun specificTargetScoreFunction(targetValue: Double = 100.0,
                                ownForceValue: Double = 1.0, enemyForceValue: Double = -1.0): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        val playerColour = numberToPlayerID(player)
        with(game.world) {
            val targetsAcquired = game.targets[playerColour]?.count { i -> cities[i].owner == playerColour } ?: 0
            val ourForces = cities.filter { c -> c.owner == playerColour }.sumByDouble { it.pop.size } +
                    currentTransits.filter { t -> t.playerId == playerColour }.sumByDouble { it.force.effectiveSize }
            val enemyForces = cities.filter { c -> c.owner != playerColour }.sumByDouble { it.pop.size } +
                    currentTransits.filter { t -> t.playerId != playerColour }.sumByDouble { it.force.effectiveSize }
            targetsAcquired * targetValue + ourForces * ownForceValue - enemyForces * enemyForceValue
        }
    }
}

fun visibilityScore(nodeValue: Double = 5.0, arcValue: Double = 2.0): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        val playerColour = numberToPlayerID(player)
        with(game.world) {
            val citiesVisible = (0 until cities.size).count { checkVisible(it, playerColour) }
            val arcsVisible = routes.count { checkVisible(it, playerColour) }
            citiesVisible * nodeValue + arcsVisible / 2.0 * arcValue
        }
    }
}
