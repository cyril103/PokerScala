package poker.engine

import org.scalatest.funsuite.AnyFunSuite
import poker.model.{Action, Player, PlayerStatus}

final class BettingRoundSpec extends AnyFunSuite {
  test("all-in raise reopens action for remaining players") {
    val player1 = Player(1, seat = 0, name = "P1", isHuman = false, stack = 100, bet = 50, status = PlayerStatus.Active)
    val player2 = Player(2, seat = 1, name = "P2", isHuman = false, stack = 120, bet = 50, status = PlayerStatus.Active)
    val player3Before = Player(3, seat = 2, name = "P3", isHuman = false, stack = 60, bet = 50, status = PlayerStatus.Active)
    val player3After = player3Before.withBet(player3Before.bet + player3Before.stack)

    val playersAfter = Vector(player1, player2, player3After)
    val round = BettingRound(
      currentBet = 50,
      minRaise = 10,
      lastAggressor = Some(2),
      pending = Set(3),
      actionOrder = Vector(1, 2, 3),
      pointer = 0
    )

    val updatedRound = round.onAction(player3Before, Action.AllIn, player3After, playersAfter)

    assert(updatedRound.currentBet == player3After.bet)
    assert(updatedRound.pending == Set(1, 2))
  }
}
