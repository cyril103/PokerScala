package poker

import org.scalatest.funsuite.AnyFunSuite
import poker.ai.{AIProfile, Bot, HandStrength}
import poker.engine.{BettingRound, Deck, GameState, HandCategory, HandRank}
import poker.model.{Action, Config, Player, PlayerStatus, ShowdownResult, Street}

import scala.util.Random

final class BotSpec extends AnyFunSuite {
  private val profile = AIProfile(riskTolerance = 0.9, bluffFrequency = 0.08, aggression = 0.3, callStation = 0.2)
  private val config = Config(rngSeed = Some(123L), mcIterations = 100)

  private val fixedStrength = HandStrength(0.6, HandRank(HandCategory.Pair, Vector(12, 10, 8, 6, 4)))
  private val evaluator = (_: GameState, _: Player, _: Int, _: Random) => fixedStrength

  private def baseState: GameState = {
    val hero = Player(id = 1, seat = 0, name = "Bot", isHuman = false, stack = 50, bet = 0, status = PlayerStatus.Active)
    val villain = Player(id = 2, seat = 1, name = "Villain", isHuman = false, stack = 200, bet = 50, status = PlayerStatus.Folded)
    val pot = poker.engine.PotManager(Map(2 -> 10))
    val round = BettingRound(currentBet = 50, minRaise = 10, lastAggressor = Some(2), pending = Set(1), actionOrder = Vector(1), pointer = 0)

    GameState(
      config = config,
      handId = 1L,
      players = Vector(hero, villain),
      buttonIndex = 0,
      deck = Deck(config.rngSeed),
      board = Vector.empty,
      street = Street.Turn,
      potManager = pot,
      bettingRound = Some(round)
    )
  }

  test("bot regenerates policy after opponent model adjustment") {
    val bot = new Bot(playerId = 1, profile = profile, config = config, evaluateHand = evaluator)
    val state = baseState

    assert(bot.decide(state) == Action.AllIn)

    val loss = ShowdownResult(playerId = 2, handRank = HandRank(HandCategory.HighCard, Vector(14, 5, 4, 3, 2)), share = 0, cards = Vector.empty)
    (0 until 50).foreach(_ => bot.observeShowdown(Some(loss)))

    assert(bot.decide(state) == Action.Fold)
  }
}

