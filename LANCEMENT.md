# Lancer HelpDesk Agent en local

## Prérequis (une seule fois)

| Outil | Vérifier | Installer |
|---|---|---|
| Java 21 (conda) | `conda env list` doit afficher `helpdesk` | `conda create -n helpdesk -c conda-forge openjdk=21` |
| Docker Desktop | `docker info` | https://www.docker.com/products/docker-desktop |
| Ollama | `ollama list` doit afficher `qwen3:8b` et `bge-m3` | `ollama pull qwen3:8b && ollama pull bge-m3` |

## Démarrer

```bash
git clone git@github.com:Sollres/Helpdesk-Agent.git   # première fois uniquement
cd Helpdesk-Agent
conda activate helpdesk
docker compose up -d          # base PostgreSQL + pgvector (Docker Desktop doit être ouvert)
./mvnw spring-boot:run
```

Attendre la ligne `Tickets indexés au démarrage ...` dans les logs, puis ouvrir **http://localhost:8080**.

### Variante : tout dans Docker

```bash
docker compose --profile app up -d --build    # base + application (Ollama reste sur le Mac)
docker compose --profile app down             # arrêter
```

## Questions pour tester

- « Mon VPN ne se connecte plus depuis ce matin » → procédure + source
- « Mes mails restent bloqués dans la boîte d'envoi Outlook » → incident en cours
- « Mon deuxième écran branché sur la station d'accueil reste noir » → solution d'un ticket résolu
- « Mon clavier Bluetooth se déconnecte toutes les dix minutes », puis « oui » → création d'un ticket
- « Où en est le ticket HD-2026-000002 ? » → suivi
- « Donne-moi la recette de la tarte tatin » → refus poli

La mémoire de conversation est perdue au redémarrage de l'application. Les tickets créés restent en base.

## Arrêter

```bash
Ctrl+C                        # arrête l'application
docker compose down           # arrête la base (les données sont conservées)
docker compose down -v        # arrête la base ET efface les données
```

## Tester sans l'interface

```bash
# Chat
curl -s localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"message": "Mon VPN ne se connecte plus"}' | python3 -m json.tool

# Recherche dans la base de connaissances, sans LLM
curl -s -G localhost:8080/api/knowledge/search --data-urlencode 'q=compte bloqué' | python3 -m json.tool

# Consulter un ticket
curl -s localhost:8080/api/tickets/HD-2026-000001 | python3 -m json.tool

# Ajouter une procédure
curl -s localhost:8080/api/admin/documents -F files=@ma-procedure.md
```

## Lancer les tests et l'évaluation

```bash
./mvnw verify                 # 41 tests, sans LLM ; Docker doit tourner (Testcontainers)
./mvnw verify -Peval          # évaluation avec le vrai modèle (~12 min), Docker + Ollama → eval/report.md
```

## Régénérer le GIF de démo

L'application doit tourner sur le port 8080 ; la commande est en tête de [docs/record_demo.py](docs/record_demo.py).

## Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker Desktop fermé | Ouvrir Docker Desktop, attendre qu'il soit prêt |
| `Connection to localhost:5432 refused` au démarrage | Base non lancée | `docker compose up -d` |
| `Connection refused` vers `localhost:11434` / réponse 502 | Ollama arrêté | Ouvrir l'application Ollama ou lancer `ollama serve` |
| `Unable to locate a Java Runtime` | Environnement conda non activé | `conda activate helpdesk` |
| `Port 8080 was already in use` | Une autre instance tourne | L'arrêter, ou `PORT=8081 ./mvnw spring-boot:run` |
| Première réponse très lente | Ollama charge le modèle en mémoire | Normal, les suivantes sont plus rapides |
| Erreur Flyway après un changement de schéma | Base créée avec une ancienne version | `docker compose down -v` puis relancer |
| Effacer les tickets créés pendant les tests | — | `docker compose exec postgres psql -U helpdesk -c "DELETE FROM vector_store WHERE metadata->>'type'='ticket'; DELETE FROM tickets WHERE id > 10;"` puis redémarrer l'application (les 10 tickets d'exemple sont réindexés) |
| Utiliser l'ancien modèle | — | `OLLAMA_CHAT_MODEL=qwen2.5:7b ./mvnw spring-boot:run` |
