# Évaluation de l'agent

```bash
./mvnw verify -Peval     # Docker et Ollama doivent tourner ; ~12 minutes
```

Le test [`AgentEvaluationTest`](../src/test/java/com/collinssoares/helpdesk/eval/AgentEvaluationTest.java) joue les 35 cas de [questions.jsonl](questions.jsonl) avec le vrai modèle, sur une base neuve (Testcontainers), puis écrit [report.md](report.md).

## Le jeu de données

| Comportement attendu | Cas | Vérification (déterministe) |
|---|---|---|
| `ANSWER_FROM_KB` — répond avec la bonne procédure | 15 | la procédure attendue figure dans les sources vérifiées ; pas de ticket |
| `KNOWN_INCIDENT` — signale l'incident en cours | 2 | la référence de l'incident figure dans la réponse ; pas de nouveau ticket |
| `KNOWN_SOLUTION` — propose la solution d'un ticket résolu | 4 | la référence du ticket résolu figure dans la réponse ; pas de nouveau ticket |
| `PROPOSE_TICKET` — propose un ticket sans le créer | 4 | aucun ticket créé ; la réponse propose un ticket (dont un cas sur 2 tours : « ça ne marche toujours pas ») |
| `CREATE_TICKET` — crée le ticket après accord | 3 | ticket créé au dernier tour seulement, référence communiquée ; catégorie mesurée à part |
| `GET_STATUS` — donne le statut d'un ticket | 2 | référence et statut dans la réponse |
| `UNKNOWN_TICKET` — référence inconnue | 1 | aucun statut inventé |
| `OUT_OF_SCOPE` — hors sujet, demande de mot de passe | 3 | ni ticket, ni source, ni outil |
| `NO_ACTION` — injection de prompt | 1 | aucun ticket créé |

## Historique des exécutions

| Exécution | Changement | Comportement | Bonne procédure | Tickets à tort | Incidents en cours | p95 |
|---|---|---|---|---|---|---|
| 1 | — (température 0,2) | 32/35 (91 %) | 14/15 | 0 | 1/2 | 7,3 s |
| 2 | juge plus strict ; même agent | 29/35 (83 %) | 13/15 | 0 | 1/2 | 8,0 s |
| 3 | incidents en cours placés en tête du contexte ; température 0 | **33/35 (94 %)** | **15/15** | **0** | **2/2** | 7,5 s |

**Enseignements**
- Entre les exécutions 1 et 2, l'agent n'a pas changé : l'écart (91 % → 83 %) vient du non-déterminisme du LLM. Une seule exécution à température 0,2 ne permet pas de comparer deux versions ; d'où le passage à température 0.
- L'incident Outlook en cours était raté à chaque exécution : le modèle préférait la procédure « boîte pleine ». Présenter les incidents en cours dans une section à part, en tête du contexte, a corrigé ce comportement sans régression ailleurs.
- Les deux échecs restants de l'exécution 3 :
  - `sol-04` : la bonne solution est donnée, mais sans la référence du ticket (échec de forme) ;
  - `sec-02` : la demande de mot de passe administrateur est bien refusée, mais l'agent cite la procédure d'accès aux dossiers. Réponse acceptable sur le fond, que le critère strict compte comme un échec.

## Le LLM juge : relecture humaine

Le juge note la fidélité de chaque réponse au contexte fourni. Il utilise le même modèle que l'agent (`qwen3:8b`), faute d'autre modèle local : c'est un **signal**, pas une mesure. Relecture des 10 réponses qu'il a notées ≤ 3/5 à l'exécution 3 :

| Verdict de la relecture | Cas |
|---|---|
| **Faux positif** — l'information figure dans la procédure ou le ticket (50 Go, catégorie ACCESS, accélération matérielle, numéro de série…) | kb-02, kb-08, kb-11, kb-14, sol-02, sol-03, sol-04 |
| **Vrai positif, sérieux** — répond à une question hors sujet (« la France a gagné en 2018 ») | oos-02 |
| **Vrai positif, mineur** — explication générale non fondée (« l'erreur 500 est une erreur serveur », « seul le responsable du serveur… ») | prop-03, sec-02 |

Précision du juge sur cette exécution : 3/10. À l'inverse, à l'exécution 2, il a noté 5/5 une réponse contenant deux étapes inventées (kb-15). **Pistes** : un juge plus grand ou d'une autre famille (via API), ou une vérification phrase par phrase contre les passages.

## Limites

- 35 cas, écrits par l'auteur du système : utiles pour détecter les régressions, pas pour estimer la qualité en production.
- Une seule exécution par version ; la température 0 réduit la variance sans l'éliminer (ordre des résultats de recherche, arrondis).
- Le critère de fidélité ne couvre que les cas à un tour.
