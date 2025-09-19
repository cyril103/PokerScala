package poker.ai

import poker.engine.{GameState, HandCategory, HandEvaluator, HandRank}
import poker.model.Player

import scala.util.Random

final case class HandStrength(equity: Double, madeHand: HandRank)

object HandStrength {
  def evaluate(state: GameState, player: Player, iterations: Int, rng: Random): HandStrength = {
    val opponents = math.max(0, state.activePlayers.size - 1)
    val equity = MonteCarloSimulator.estimateEquity(player.holeCards, state.board, opponents, iterations, rng)
    val madeHand = if (state.board.size + player.holeCards.size >= 5) {
      HandEvaluator.evaluate7(player.holeCards ++ state.board)
    } else {
      val kickers = player.holeCards.map(_.rank.value).sorted(Ordering[Int].reverse)
      HandRank(HandCategory.HighCard, kickers.toVector)
    }
    HandStrength(equity, madeHand)
  }
}
