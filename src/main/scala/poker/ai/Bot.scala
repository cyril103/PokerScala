package poker.ai

import poker.engine.GameState
import poker.model.{Action, Config, ShowdownResult}

import scala.util.Random

final class Bot(val playerId: Int, profile: AIProfile, config: Config) {
  private val policy = new AIPolicy(profile)
  private var opponentModel: OpponentModel = OpponentModel(profile.riskTolerance, profile.bluffFrequency, profile.aggression, profile.callStation)
  private val rng = config.rngSeed.map(seed => new Random(seed + playerId)).getOrElse(new Random())

  def decide(state: GameState): Action = {
    val player = state.playerById(playerId).getOrElse(throw new IllegalStateException(s"Player $playerId not found"))
    val round = state.bettingRound.getOrElse(throw new IllegalStateException("No betting round active"))
    val callAmount = math.max(0, round.currentBet - player.bet)
    val strength = HandStrength.evaluate(state, player, config.mcIterations, rng)
    val ctx = DecisionContext(
      strength = strength,
      callAmount = callAmount,
      minRaise = round.minRaise,
      potSize = state.potManager.total,
      currentBet = round.currentBet,
      stack = player.stack,
      street = state.street,
      rng = rng
    )
    policy.decide(ctx)
  }

  def observeShowdown(result: Option[ShowdownResult]): Unit = {
    opponentModel = opponentModel.adjustOnOutcome(result.exists(_.playerId == playerId))
  }
}
