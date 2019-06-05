package games.eventqueuegame


fun compositeScoreFunction(vararg functions: (LandCombatGame, Int) -> Double): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        functions.map{f -> f(game, player)}.sum()
    }
}

fun simpleScoreFunction(ourCityValue: Double, ourForceValue: Double, theirCityValue: Double, theirForceValue: Double): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        val playerId = numberToPlayerID(player)
        with(game.world) {
            val ourCities = cities.count { c -> c.owner == playerId }
            val theirCities = cities.count { c -> c.owner == if (playerId == PlayerId.Blue) PlayerId.Red else PlayerId.Blue }
            // then add the total of all forces
            val ourForces = cities.filter { c -> c.owner == playerId }.sumByDouble(City::pop) +
                    currentTransits.filter { t -> t.playerId == playerId }.sumByDouble(Transit::nPeople)
            val enemyForces = cities.filter { c -> c.owner != playerId}.sumByDouble(City::pop) +
                    currentTransits.filter { t -> t.playerId == PlayerId.Red }.sumByDouble(Transit::nPeople)
            ourCityValue * ourCities + ourForceValue * ourForces + theirCityValue * theirCities + theirForceValue * enemyForces
        }
    }
}


fun specificTargetScoreFunction(targetValue: Double = 100.0,
                                ownForceValue: Double = 1.0, enemyForceValue: Double = -1.0): (LandCombatGame, Int) -> Double {
    return { game: LandCombatGame, player: Int ->
        val playerColour = numberToPlayerID(player)
        with(game.world) {
            val targetsAcquired = game.targets[playerColour]?.count { i -> cities[i].owner == playerColour } ?: 0
            val ourForces = cities.filter { c -> c.owner == playerColour }.sumByDouble(City::pop) +
                    currentTransits.filter { t -> t.playerId == playerColour }.sumByDouble(Transit::nPeople)
            val enemyForces = cities.filter { c -> c.owner != playerColour }.sumByDouble(City::pop) +
                    currentTransits.filter { t -> t.playerId != playerColour }.sumByDouble(Transit::nPeople)
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
            citiesVisible * nodeValue + arcsVisible * arcValue
        }
    }
}