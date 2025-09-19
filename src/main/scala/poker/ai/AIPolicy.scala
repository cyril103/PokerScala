package poker.ai

import poker.model.{Action, Street}

import scala.util.Random

final case class AIProfile(
  riskTolerance: Double,
  bluffFrequency: Double,
  aggression: Double,
  callStation: Double
)

object AIProfile {
  val default: AIProfile = AIProfile(riskTolerance = 0.55, bluffFrequency = 0.08, aggression = 0.45, callStation = 0.25)
}

final case class DecisionContext(
  strength: HandStrength,
  callAmount: Int,
  minRaise: Int,
  potSize: Int,
  currentBet: Int,
  stack: Int,
  street: Street,
  rng: Random
)

final class AIPolicy(profile: AIProfile) {
  def decide(ctx: DecisionContext): Action = {
    val HandStrength(equity, _) = ctx.strength
    val callAmount = math.max(0, ctx.callAmount)
    val potOdds = if (callAmount == 0) 0.0 else callAmount.toDouble / (ctx.potSize + callAmount.toDouble)
    val foldThreshold = math.max(0.05, potOdds * (1.0 - profile.riskTolerance))
    val raiseThreshold = math.min(0.95, 0.45 + profile.aggression * 0.3)

    if (callAmount >= ctx.stack) {
      if (equity >= foldThreshold) Action.AllIn else Action.Fold
    } else if (equity < foldThreshold && ctx.rng.nextDouble() > profile.bluffFrequency) {
      if (callAmount == 0) Action.Check else Action.Fold
    } else if (equity < potOdds && ctx.rng.nextDouble() > profile.callStation) {
      if (callAmount == 0) Action.Check else Action.Fold
    } else if (equity >= raiseThreshold || (equity >= potOdds && ctx.rng.nextDouble() < profile.aggression)) {
      val baseRaise = math.max(ctx.minRaise, (ctx.potSize * 0.6).toInt)
      val raiseAmount = math.min(ctx.stack + ctx.currentBet, ctx.currentBet + callAmount + baseRaise)
      if (raiseAmount >= ctx.currentBet + ctx.stack) Action.AllIn
      else if (ctx.currentBet == 0) Action.Bet(raiseAmount)
      else Action.Raise(raiseAmount)
    } else {
      if (callAmount == 0) Action.Check else Action.Call
    }
  }
}
