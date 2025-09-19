package poker.ai

import poker.engine.{Card, Deck, HandEvaluator, HandRank}

import scala.util.Random

object MonteCarloSimulator {
  def estimateEquity(hero: Vector[Card], board: Vector[Card], opponents: Int, iterations: Int, rng: Random): Double = {
    if (opponents <= 0) return 1.0
    if (iterations <= 0) return 0.0

    val known = (hero ++ board).toSet
    val deckPool = Deck.allCards.filterNot(known.contains)

    var wins = 0
    var ties = 0
    var total = 0

    val neededBoard = math.max(0, 5 - board.length)
    val neededCards = opponents * 2 + neededBoard
    if (deckPool.length < neededCards) return 0.0

    for (_ <- 0 until iterations) {
      val shuffled = rng.shuffle(deckPool)
      val (boardTail, remainder) = shuffled.splitAt(neededBoard)
      val completeBoard = board ++ boardTail
      val opponentHands = remainder.grouped(2).take(opponents).toVector

      val heroRank = HandEvaluator.evaluate7(hero ++ completeBoard)
      val opponentRanks = opponentHands.map(hand => HandEvaluator.evaluate7(hand ++ completeBoard))
      val bestOpponent = opponentRanks.maxOption.getOrElse(HandRank.lowest)
      val cmp = heroRank.compare(bestOpponent)
      if (cmp > 0) wins += 1
      else if (cmp == 0) ties += 1
      total += 1
    }

    if (total == 0) 0.0 else (wins + ties * 0.5) / total
  }
}

