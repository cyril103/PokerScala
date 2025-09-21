package poker

import org.scalatest.funsuite.AnyFunSuite
import poker.engine.{Dealer, GameState}
import poker.model.{Action, Config, Player, PlayerStatus, Street}

final class GameStateSpec extends AnyFunSuite {
  private def baseConfig: Config = Config(
    startingStack = 100,
    smallBlind = 1,
    bigBlind = 2,
    ante = 0,
    numBots = 1,
    mcIterations = 100,
    decisionTimeMs = 10,
    showdownPauseMs = 0,
    rngSeed = Some(42L)
  )

  private def players: Vector[Player] = Vector(
    Player(1, 0, "Hero", isHuman = true, stack = 100, status = PlayerStatus.Active),
    Player(2, 1, "Bot", isHuman = false, stack = 100, status = PlayerStatus.Active)
  )

  test("startHand deals cards and sets street to preflop") {
    val dealer = new Dealer(GameState.initial(baseConfig, players))
    dealer.startHand()
    val state = dealer.gameState
    assert(state.street == Street.PreFlop)
    assert(state.players.forall(p => if (p.status == PlayerStatus.Active) p.holeCards.size == 2 else true))
  }

  test("folding heads-up ends the hand immediately") {
    val dealer = new Dealer(GameState.initial(baseConfig, players))
    dealer.startHand()
    val firstToAct = dealer.nextToAct.get
    dealer.act(firstToAct, Action.Fold)
    assert(dealer.gameState.street == Street.Showdown)
  }
}
