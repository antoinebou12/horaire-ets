# Déploiement de l'API

En ce moment, il y a 2 scripts GitHub actions permettant de déployer l'application de 2 façons différentes:

- **Déploiement sur Heroku**: Le script `.github/workflows/heroku-deployment.yml` permet de déployer l'application sur Heroku.
- **Déploiement sur une machine distante**: Le script `.github/workflows/dockerhub.yml` permet de déployer l'application sur une machine distante. Le fonctionnement est simple: build du conteneur docker et push sur DockerHub. Ensuite, il suffit de se connecter en SSH à la machine distante et de relancer les conteneurs.

Pour le moment, le déploiement sur une machine distante est utilisé pour sauver les coûts plus élevés d'Heroku. Pour changer le script de déploiement, il suffit de rajouter **if: false** dans le workflow GitHub Actions approprié.

## Étapes de déploiement sur une machine distante

- Se connecter en SSH à la machine distante
- Installer Docker et se login avec `docker login`
- Copier les fichiers suivants dans un dossier:
  - `docker-compose.yml`
  - `Caddyfile`
  - `.env` (créer avec les variables d'environnement)
- Remplir les variables d'environnement dans `.env`:
  - `DB_USERNAME` - Nom d'utilisateur PostgreSQL
  - `DB_PASSWORD` - Mot de passe PostgreSQL
  - `DB_NAME` - Nom de la base de données
  - `DB_URL` - URL de connexion (ex: `jdbc:postgresql://postgres:5432/${DB_NAME}`)
  - `DISCORD_TOKEN` - Token du bot Discord
- (Optionnel) Modifier le fichier `Caddyfile` pour mettre votre nom de domaine à la place de `localhost`
- Démarrer les conteneurs avec: `docker compose up -d`

## Configurer les secrets GitHub Actions

- Ajouter les variables suivantes dans vos secrets GitHub Actions

  - `DOCKERHUB_USERNAME`: Le nom d'utilisateur DockerHub
  - `DOCKERHUB_PASSWORD`: Le mot de passe ou token DockerHub
  - `SSH_HOST`: L'adresse IP de la machine distante
  - `SSH_USERNAME`: Le nom d'utilisateur SSH
  - `SSH_KEY`: La clé privée SSH

- Modifier à vos besoins le script de déploiement (par exemple, mettre le bon projet DockerHub, etc.)
