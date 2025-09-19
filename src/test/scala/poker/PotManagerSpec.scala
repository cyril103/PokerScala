package poker

import org.scalatest.funsuite.AnyFunSuite
import poker.engine.{HandCategory, HandRank, PotManager}
import poker.model.{Player, PlayerStatus}

final class PotManagerSpec extends AnyFunSuite {
  private def mkPlayer(
      id: Int,
      seat: Int,
      bet: Int,
      status: PlayerStatus,
      totalInvested: Int
  ): Player =
    Player(
      id = id,
      seat = seat,
      name = s"P$id",
      isHuman = false,
      stack = 0,
      bet = bet,
      status = status,
      totalInvested = totalInvested
    )

  private def samplePlayers: Vector[Player] = Vector(
    mkPlayer(id = 1, seat = 0, bet = 100, status = PlayerStatus.AllIn, totalInvested = 100),
    mkPlayer(id = 2, seat = 1, bet = 50, status = PlayerStatus.AllIn, totalInvested = 50),
    mkPlayer(id = 3, seat = 2, bet = 100, status = PlayerStatus.AllIn, totalInvested = 100)
  )

  test("buildPotState computes side pots correctly") {
    val pm = PotManager()
      .addContribution(1, 100)
      .addContribution(2, 50)
      .addContribution(3, 100)

    val pot = pm.buildPotState(Set(1, 2, 3))
    assert(pot.sidePots.length == 2)
    assert(pot.sidePots.head.amount == 150)
    assert(pot.sidePots.last.amount == 100)
  }

  test("resolveShowdown distributes side pots to winners") {
    val pm = PotManager()
      .addContribution(1, 100)
      .addContribution(2, 50)
      .addContribution(3, 100)

    val ranks = Map(
      1 -> HandRank(HandCategory.Flush, Vector(14, 10, 8, 4, 2)),
      2 -> HandRank(HandCategory.TwoPairs, Vector(11, 8, 5)),
      3 -> HandRank(HandCategory.Straight, Vector(9, 8, 7, 6, 5))
    )

    val results = pm.resolveShowdown(samplePlayers, ranks)
    val winnerShare = results.find(_.playerId == 1).map(_.share).getOrElse(0)
    assert(winnerShare == 250)
  }

  test("folded chips stay in the pot") {
    val pm = PotManager()
      .addContribution(1, 100)
      .addContribution(2, 150)
      .addContribution(3, 50)

    val players = Vector(
      mkPlayer(id = 1, seat = 0, bet = 100, status = PlayerStatus.Folded, totalInvested = 100),
      mkPlayer(id = 2, seat = 1, bet = 150, status = PlayerStatus.AllIn, totalInvested = 150),
      mkPlayer(id = 3, seat = 2, bet = 50, status = PlayerStatus.AllIn, totalInvested = 50)
    )

    val potState = pm.buildPotState(players.filter(_.inHand).map(_.id).toSet)
    assert(potState.total == 300)

    val ranks = Map(
      2 -> HandRank(HandCategory.Flush, Vector(14, 10, 8, 4, 2)),
      3 -> HandRank(HandCategory.Flush, Vector(14, 10, 8, 4, 2))
    )

    val results = pm.resolveShowdown(players, ranks)
    val payoutPlayer2 = results.find(_.playerId == 2).map(_.share).getOrElse(0)
    val payoutPlayer3 = results.find(_.playerId == 3).map(_.share).getOrElse(0)
    assert(payoutPlayer2 + payoutPlayer3 == 300)
  }

  test("odd chips go to the first eligible winner in seat order") {
    val pm = PotManager()
      .addContribution(1, 3)
      .addContribution(2, 3)
      .addContribution(3, 1)

    val players = Vector(
      mkPlayer(id = 1, seat = 0, bet = 3, status = PlayerStatus.AllIn, totalInvested = 3),
      mkPlayer(id = 2, seat = 1, bet = 3, status = PlayerStatus.AllIn, totalInvested = 3),
      mkPlayer(id = 3, seat = 2, bet = 1, status = PlayerStatus.Folded, totalInvested = 1)
    )

    val ranks = Map(
      1 -> HandRank(HandCategory.Flush, Vector(14, 9, 7, 5, 2)),
      2 -> HandRank(HandCategory.Flush, Vector(14, 9, 7, 5, 2))
    )

    val results = pm.resolveShowdown(players, ranks)
    val share1 = results.find(_.playerId == 1).map(_.share).getOrElse(0)
    val share2 = results.find(_.playerId == 2).map(_.share).getOrElse(0)
    assert(share1 == 4)
    assert(share2 == 3)
  }
}

