# ScalaHoldemFX - Agents IA

Ce document decrit comment les agents non joueurs (bots) sont construits dans le moteur ScalaHoldemFX. Tous les fichiers mentionnes se trouvent dans `src/main/scala/poker/ai` sauf indication contraire.

## Architecture generale
- `Bot` orchestre la decision pour un joueur IA : il assemble le contexte, estime la force de main et delegue la politique de mise.
- `HandStrength` fournit une estimation combinee (equity + classement realise) pour la main courante.
- `MonteCarloSimulator` approche l equity par tirages aleatoires a partir du paquet restant.
- `AIPolicy` convertit le contexte en action (`Fold`, `Check`, `Call`, `Bet`, `Raise`, `AllIn`).
- `OpponentModel` capture une memoire minimale des resultats precedents pour ajuster le profil.

## Cycle de decision
1. Le moteur appelle `Bot.decide(state)` pour chaque bot encore actif.
2. `Bot` recupere le joueur, le tour de mise courant et derive les donnees cle : montant pour suivre, mise minimale, pot, tapis.
3. `HandStrength.evaluate` :
   - compte les adversaires restants,
   - calcule l equity via `MonteCarloSimulator.estimateEquity` en utilisant `config.mcIterations` et la RNG du bot,
   - retourne la meilleure main realisee si 5 cartes ou plus sont disponibles, sinon un `HighCard` artificiel.
4. `Bot` construit un `DecisionContext` (force, pot odds, tailles de mise, rue, RNG) et le soumet a `AIPolicy`.
5. `AIPolicy.decide` applique une heuristique :
   - seuil de fold base sur pot odds et `riskTolerance`,
   - seuil de relance issu de `aggression`,
   - probabilites de bluff et de call station dependent de `bluffFrequency` et `callStation`,
   - choisit AllIn si suivre consomme tout le tapis.
6. L action retournee est renvoyee au moteur qui met a jour l etat.

## Simulation Monte Carlo
- Le simulateur supprime les cartes connues (main du bot + board) et reutilise le paquet restant.
- Chaque iteration :
  - tire les cartes manquantes du board et distribue 2 cartes par adversaire,
  - evalue la main du bot et celles des adversaires via `HandEvaluator.evaluate7`,
  - compte victoires et egalites.
- Le resultat est `(wins + 0.5 * ties) / total`. Si les preconditions (iterations, cartes disponibles) ne sont pas remplies, la fonction degrade proprement (1.0, 0.0, etc.).

## Profils et adaptation
- `AIProfile` regroupe quatre parametres principaux :

  | Champ           | Description rapide                          | Intervalle attendu |
  | --------------- | ------------------------------------------- | ------------------ |
  | `riskTolerance` | Propension a jouer des mains marginales      | 0.0 - 1.0          |
  | `bluffFrequency`| Frequence de bluffs purs/semi-bluffs         | 0.0 - 0.4          |
  | `aggression`    | Tendance a miser ou relancer agressivement   | 0.0 - 1.0          |
  | `callStation`   | Probabilite supplementaire de payer          | 0.0 - 1.0          |

- `AIProfile.default` fournit un profil equilibre (0.55 / 0.08 / 0.45 / 0.25).
- `Bot` clone ce profil dans `OpponentModel`. Apres chaque showdown (`observeShowdown`), l agent ajuste doucement sa bluff frequency (si victoire) ou sa tolerance au risque (si defaite) pour introduire une variation a long terme.

## Parametrage via Config
- `Config.mcIterations` (defaut 2000) pilote la precision de l equity et le temps de calcul.
- `Config.rngSeed` permet la reproducibilite : chaque bot derive sa RNG en ajoutant son identifiant au seed de base.
- `Config.decisionTimeMs` et `Config.minBotDelayMs` sont consommes cote UI pour cadencer les actions.
- `Config.numBots` fixe le nombre d instances `Bot` creees au lancement (1 a 8).

## Extension et personnalisations
- Pour introduire un nouveau style, instanciez `Bot` avec un `AIProfile` sur mesure (ex. profil hyper agressif ou nit) avant d ajouter le bot a la partie.
- `AIPolicy` est volontairement concise : la personnaliser (nouveaux seuils, strategies de sizing) est une piste simple sans impacter le moteur.
- `MonteCarloSimulator` peut accueillir d autres modes (par exemple echantillonnage importance) si le besoin de performance change.
- Les reglages UI exposent ces trois leviers : nombre d agents, iterations Monte Carlo, seed RNG.

## Points de vigilance
- Plus `mcIterations` est eleve, plus la latence augmente ; ajustez `decisionTimeMs` en consequence.
- `OpponentModel` ne suit que des resultats agreges : si vous avez besoin d un tracking plus fin (historique d actions), prevoir une extension dediee.
- `AIPolicy` suppose que `callAmount` ne depasse pas `stack`. Les preconditions du moteur garantissent cela mais tout changement devra conserver cette invariance.

