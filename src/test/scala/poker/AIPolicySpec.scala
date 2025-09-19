package poker

import org.scalatest.funsuite.AnyFunSuite
import poker.ai.{AIProfile, AIPolicy, DecisionContext, HandStrength}
import poker.engine.HandRank
import poker.engine.HandCategory
import poker.model.{Action, Street}

import scala.util.Random

final class AIPolicySpec extends AnyFunSuite {
  private val profile = AIProfile.default
  private val policy = new AIPolicy(profile)

  test("folds weak equity facing a bet") {
    val ctx = DecisionContext(
      strength = HandStrength(0.02, HandRank(HandCategory.HighCard, Vector(14, 9, 7, 5, 3))),
      callAmount = 5,
      minRaise = 4,
      potSize = 20,
      currentBet = 5,
      stack = 50,
      street = Street.Flop,
      rng = new Random(0L)
    )
    assert(policy.decide(ctx) == Action.Fold)
  }

  test("raises with strong equity") {
    val ctx = DecisionContext(
      strength = HandStrength(0.8, HandRank(HandCategory.Flush, Vector(14, 12, 8, 6, 2))),
      callAmount = 2,
      minRaise = 4,
      potSize = 20,
      currentBet = 4,
      stack = 50,
      street = Street.Turn,
      rng = new Random(1L)
    )
    policy.decide(ctx) match {
      case Action.Raise(amount) => assert(amount >= 10)
      case other                => fail(s"Expected a raise, got $other")
    }
  }

  test("checks when facing no bet and low aggression") {
    val ctx = DecisionContext(
      strength = HandStrength(0.3, HandRank(HandCategory.Pair, Vector(9, 8, 5, 4))),
      callAmount = 0,
      minRaise = 4,
      potSize = 15,
      currentBet = 0,
      stack = 40,
      street = Street.River,
      rng = new Random(3L)
    )
    assert(policy.decide(ctx) == Action.Check)
  }
}
