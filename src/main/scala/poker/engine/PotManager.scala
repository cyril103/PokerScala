package poker.engine

import poker.model.{Player, PotState, SidePot, ShowdownResult}

final case class PotManager(contributions: Map[Int, Int] = Map.empty) {
  def total: Int = contributions.values.sum

  def contribution(playerId: Int): Int = contributions.getOrElse(playerId, 0)

  def addContribution(playerId: Int, amount: Int): PotManager =
    if (amount <= 0) this
    else copy(contributions = contributions.updated(playerId, contribution(playerId) + amount))

  def buildPotState(activePlayers: Set[Int]): PotState = {
    val pots = buildSidePots(activePlayers)
    PotState(total = pots.map(_.amount).sum, contributions = contributions, sidePots = pots)
  }

  def resolveShowdown(players: Vector[Player], ranks: Map[Int, HandRank]): Vector[ShowdownResult] = {
    val active = players.filter(_.inHand).map(_.id).toSet
    val pots = buildSidePots(active)
    val results = pots.flatMap { pot =>
      val contenders = players.filter(p => pot.eligible.contains(p.id) && ranks.contains(p.id))
      if (contenders.isEmpty) Vector.empty
      else {
        val bestRank = contenders.map(p => ranks(p.id)).max
        val winners = contenders.filter(p => ranks(p.id) == bestRank)
        val baseShare = pot.amount / winners.size
        val remainder = pot.amount % winners.size
        winners.zipWithIndex.map { case (player, idx) =>
          val extra = if (idx < remainder) 1 else 0
          ShowdownResult(player.id, bestRank, baseShare + extra, player.holeCards)
        }
      }
    }
    mergeShares(results)
  }

  private def mergeShares(results: Vector[ShowdownResult]): Vector[ShowdownResult] = {
    results.groupBy(_.playerId).values.map { entries =>
      val amount = entries.map(_.share).sum
      entries.head.copy(share = amount)
    }.toVector
  }

  private def buildSidePots(activePlayers: Set[Int]): Vector[SidePot] = {
    val positive = contributions.toVector.filter { case (_, amount) => amount > 0 }
    if (positive.isEmpty) Vector.empty
    else {
      val sorted = positive.sortBy(_._2)
      val levels = sorted.map(_._2).distinct.sorted
      var prevLevel = 0
      val pots = scala.collection.mutable.ArrayBuffer.empty[SidePot]
      levels.foreach { level =>
        val contributors = sorted.collect { case (id, amount) if amount >= level => id }
        val contributionPerPlayer = level - prevLevel
        val amount = contributionPerPlayer * contributors.size
        val eligible = contributors.filter(activePlayers.contains).toSet
        if (amount > 0 && eligible.nonEmpty) {
          pots += SidePot(amount, eligible)
        }
        prevLevel = level
      }
      pots.toVector
    }
  }
}




