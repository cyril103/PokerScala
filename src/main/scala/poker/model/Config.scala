package poker.model

final case class Config(
  startingStack: Int = 200,
  smallBlind: Int = 1,
  bigBlind: Int = 2,
  ante: Int = 0,
  numBots: Int = 4,
  mcIterations: Int = 2000,
  decisionTimeMs: Int = 25,
  minBotDelayMs: Int = 275,
  rngSeed: Option[Long] = Some(42L),
  uiAnimationMs: Int = 300,
  maxPlayers: Int = 9
) {
  require(minBotDelayMs >= 0, "minBotDelayMs must be non-negative")
  require(numBots >= 1 && numBots <= 8, "numBots must be between 1 and 8")
  require(startingStack > 0, "starting stack must be positive")
  require(smallBlind > 0 && bigBlind > smallBlind, "blinds must be positive and bigBlind > smallBlind")
}

