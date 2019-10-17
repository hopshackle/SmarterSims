package groundWar

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/*
 A returned positive value is the number of surviving attackers; a negative value is interpreted as the number of
 surviving defenders, with the attack repulsed
 */
fun lanchesterLinearBattle(attack: Double, defence: Double, attackerDamageCoeff: Double, defenderDamageCoeff: Double): Double {
    var attackingForce = attack
    var defendingForce = defence
    var count = 0
    do {
        val attackDmg = attackingForce * attackerDamageCoeff
        val defenceDmg = defendingForce * defenderDamageCoeff
        attackingForce -= defenceDmg
        defendingForce -= attackDmg
        count++
    } while (attackingForce > 0.0 && defendingForce > 0.0 && count < 100)
    return if (defendingForce > 0.0) -defendingForce else attackingForce
}

fun lanchesterClosedFormBattle(attack: Force, defence: Force, attCoeff: Double, attExp: Double, defCoeff: Double, defExp: Double, defenceLimit: Double = 0.0): Double {
    // firstly calculate which side will win
    if (defence.size == 0.00) return attack.size // no defence
    val effectiveAttackCoeff = attCoeff * max(0.0, 1.0 - attack.fatigue)
    val effectiveDefenceCoeff = defCoeff * max(0.0, 1.0 - defence.fatigue) * if (defenceLimit > 0.0) sqrt(defenceLimit / defence.size) else 1.0
    val constant: Double = effectiveAttackCoeff * attack.size.pow(attExp + 1) - effectiveDefenceCoeff * defence.size.pow(defExp + 1)
    if (constant > 0.0) {
        // attacker wins
        return (constant / effectiveAttackCoeff).pow(1.0 / (attExp + 1.0))
    } else {
        // defender wins
        return -(-constant / effectiveDefenceCoeff).pow(1.0 / (defExp + 1.0))
    }
}