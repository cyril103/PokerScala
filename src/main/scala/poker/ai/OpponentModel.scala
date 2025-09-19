package poker.ai

final case class OpponentModel(
  riskTolerance: Double,
  bluffFrequency: Double,
  aggression: Double,
  callStation: Double
) {
  def adjustOnOutcome(won: Boolean): OpponentModel = {
    if (won) copy(bluffFrequency = clamp(bluffFrequency * 0.98 + 0.02, 0.0, 0.4))
    else copy(riskTolerance = clamp(riskTolerance * 0.97, 0.05, 1.0))
  }

  private def clamp(value: Double, min: Double, max: Double): Double =
    math.max(min, math.min(max, value))
}

object OpponentModel {
  def default: OpponentModel = OpponentModel(0.5, 0.1, 0.5, 0.3)
}
